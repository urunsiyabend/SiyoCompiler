package codeanalysis;

import codeanalysis.binding.BoundBlockStatement;
import codeanalysis.emitting.Emitter;
import codeanalysis.lowering.Lowerer;
import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * What a module exports, and the name an importer reaches it through.
 *
 * <p>A module used to export everything it declared and could only be reached
 * under its own file name, so a helper could not be kept to itself and two
 * modules with the same last path segment could not both be imported.
 */
class ModuleVisibilityTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-module-visibility");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> { try { Files.delete(path); } catch (Exception ignored) { } });
        }
    }

    // --- pub ------------------------------------------------------------------

    @Test
    void aModuleThatMarksNothingStillExportsEverything() throws Exception {
        write("plain.siyo", """
                fn visible() -> string { "here" }
                fn alsoVisible() -> string { "also here" }
                """);

        assertEquals("here\nalso here", compileAndRun("""
                import "plain"
                fn main() {
                    println(plain.visible())
                    println(plain.alsoVisible())
                }
                """, "PlainMain"));
    }

    @Test
    void aPublicFunctionIsReachableFromAnImporter() throws Exception {
        write("api.siyo", """
                pub fn greet(name: string) -> string { "hello " + name }
                fn helper() -> string { "internal" }
                """);

        assertEquals("hello world", compileAndRun("""
                import "api"
                fn main() { println(api.greet("world")) }
                """, "PubMain"));
    }

    @Test
    void aFunctionThatIsNotPublicIsPrivateOnceTheModuleMarksAnything() throws Exception {
        write("api.siyo", """
                pub fn greet(name: string) -> string { "hello " + name }
                fn helper() -> string { "internal" }
                """);

        assertTrue(firstDiagnosticOf("""
                import "api"
                fn main() { println(api.helper()) }
                """, "PrivateMain").startsWith("'api.helper' is private to module 'api'"));
    }

    @Test
    void aPrivateFunctionIsStillCallableInsideItsOwnModule() throws Exception {
        write("api.siyo", """
                fn helper() -> string { "internal" }
                pub fn describe() -> string { "uses " + helper() }
                """);

        assertEquals("uses internal", compileAndRun("""
                import "api"
                fn main() { println(api.describe()) }
                """, "OwnModuleMain"));
    }

    @Test
    void aPublicStructCrossesTheBoundaryAndAPrivateOneDoesNot() throws Exception {
        write("shapes.siyo", """
                pub struct Point { x: int, y: int }
                struct Secret { code: int }
                pub fn origin() -> Point { Point { x: 0, y: 0 } }
                """);

        assertEquals("0", compileAndRun("""
                import "shapes"
                fn main() {
                    imut p = shapes.origin()
                    println(toString(p.x))
                }
                """, "PubStructMain"));

        assertTrue(firstDiagnosticOf("""
                import "shapes"
                fn main() {
                    imut s: Secret = Secret { code: 1 }
                    println(toString(s.code))
                }
                """, "PrivStructMain").startsWith("'Secret' is private to module 'shapes'"));
    }

    @Test
    void aPublicSumTypeCrossesTheBoundary() throws Exception {
        write("results.siyo", """
                pub type Result = Ok(int) | Err(string)
                pub fn parse(raw: string) -> Result {
                    if raw == "1" { Ok(1) } else { Err("bad") }
                }
                """);

        assertEquals("Ok(1)", compileAndRun("""
                import "results"
                fn main() { println(toString(results.parse("1"))) }
                """, "PubUnionMain"));
    }

    @Test
    void aPublicModuleVariableIsReadableAndAPrivateOneIsNot() throws Exception {
        write("config.siyo", """
                pub imut name = "siyo"
                imut secret = "hunter2"
                """);

        assertEquals("siyo", compileAndRun("""
                import "config"
                fn main() { println(config.name) }
                """, "PubVarMain"));

        assertTrue(firstDiagnosticOf("""
                import "config"
                fn main() { println(config.secret) }
                """, "PrivVarMain").contains("secret"));
    }

    @Test
    void aPublicEnumCrossesTheBoundary() throws Exception {
        write("status.siyo", """
                pub enum Level { Low, High }
                enum Hidden { A, B }
                pub fn top() -> int { Level.High }
                """);

        assertEquals("1", compileAndRun("""
                import "status"
                fn main() { println(toString(status.top())) }
                """, "PubEnumMain"));
    }

    @Test
    void pubOnSomethingThatIsNotADeclarationIsReported() {
        SyntaxTree tree = SyntaxTree.parse("""
                fn main() {
                    pub println("hi")
                }
                """);
        if (!tree.diagnostics().hasNext()) fail("expected a diagnostic");
        assertTrue(tree.diagnostics().get(0).getMessage()
                .startsWith("pub marks a declaration as exported and cannot be written here"));
    }

    @Test
    void aStdModulesInternalStateIsNotPartOfItsSurface() throws Exception {
        assertTrue(firstDiagnosticOf("""
                import "std/testing"
                fn main() { println(toString(testing._beforeHook)) }
                """, "StdInternalMain").startsWith("'testing._beforeHook' is private to module 'std/testing'"));
    }

    // --- import aliasing -------------------------------------------------------

    @Test
    void anImportMayBeGivenANameOfTheImportersChoosing() throws Exception {
        write("geometry.siyo", "pub fn area(w: int, h: int) -> int { w * h }");

        assertEquals("12", compileAndRun("""
                import "geometry" as g
                fn main() { println(toString(g.area(3, 4))) }
                """, "AliasMain"));
    }

    @Test
    void twoModulesSharingALastSegmentAreBothReachableUnderAliases() throws Exception {
        Files.createDirectories(tempDir.resolve("left"));
        Files.createDirectories(tempDir.resolve("right"));
        write("left/util.siyo", "pub fn tag() -> string { \"left\" }");
        write("right/util.siyo", "pub fn tag() -> string { \"right\" }");

        assertEquals("left\nright", compileAndRun("""
                import "left/util" as l
                import "right/util" as r
                fn main() {
                    println(l.tag())
                    println(r.tag())
                }
                """, "TwoAliasMain"));
    }

    @Test
    void theModulesOwnNameIsNotReachableOnceItIsAliased() throws Exception {
        write("geometry.siyo", "pub fn area(w: int, h: int) -> int { w * h }");

        assertEquals("Name 'geometry' does not exist", firstDiagnosticOf("""
                import "geometry" as g
                fn main() { println(toString(geometry.area(3, 4))) }
                """, "AliasOnlyMain"));
    }

    @Test
    void anAliasedImportStillCarriesStructsAndSumTypes() throws Exception {
        write("shapes.siyo", """
                pub struct Point { x: int, y: int }
                pub fn origin() -> Point { Point { x: 2, y: 3 } }
                """);

        assertEquals("2 3", compileAndRun("""
                import "shapes" as s
                fn main() {
                    imut p = s.origin()
                    println(toString(p.x) + " " + toString(p.y))
                }
                """, "AliasStructMain"));
    }

    @Test
    void aStdModuleMayBeAliased() throws Exception {
        assertEquals("4", compileAndRun("""
                import "std/math" as m
                fn main() { println(toString(m.abs(-4))) }
                """, "AliasStdMain"));
    }

    // --- helpers ------------------------------------------------------------

    private void write(String name, String source) throws Exception {
        Files.writeString(tempDir.resolve(name), source);
    }

    private String firstDiagnosticOf(String source, String className) throws Exception {
        Path mainPath = tempDir.resolve("main.siyo");
        Files.writeString(mainPath, source);
        Compilation compilation = new Compilation(
                SyntaxTree.parse(source), new ModuleRegistry(), mainPath.toString());
        byte[] bytes = compilation.compile(className);
        if (bytes != null) fail("expected the program to be rejected");
        DiagnosticBox diagnostics = compilation.getGlobalScope().getDiagnostics();
        if (!diagnostics.hasNext() && compilation.getEmitDiagnostics() != null) {
            diagnostics = compilation.getEmitDiagnostics();
        }
        if (!diagnostics.hasNext()) fail("expected a diagnostic");
        return diagnostics.get(0).getMessage();
    }

    private String compileAndRun(String source, String className) throws Exception {
        Path mainPath = tempDir.resolve("main.siyo");
        Files.writeString(mainPath, source);
        ModuleRegistry registry = new ModuleRegistry();
        Compilation compilation = new Compilation(SyntaxTree.parse(source), registry, mainPath.toString());
        byte[] mainBytes = compilation.compile(className);
        if (mainBytes == null) {
            String message = compilation.getGlobalScope().getDiagnostics().hasNext()
                    ? compilation.getGlobalScope().getDiagnostics().get(0).getMessage()
                    : "unknown error";
            fail("Compilation failed: " + message);
        }

        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) return defineClass(name, mainBytes, 0, mainBytes.length);
                for (ModuleSymbol module : registry.getAllModules()) {
                    if (!name.equals(module.getClassName())) continue;
                    Map<FunctionSymbol, BoundBlockStatement> bodies = new HashMap<>();
                    for (var entry : module.getFunctionBodies().entrySet()) {
                        bodies.put(entry.getKey(), Lowerer.lower(entry.getValue()));
                    }
                    BoundBlockStatement topLevel = module.getTopLevelBlock() != null
                            ? module.getTopLevelBlock()
                            : new BoundBlockStatement(new ArrayList<>());
                    Emitter emitter = new Emitter(topLevel, bodies);
                    emitter.setModuleClass(true);
                    emitter.setImportedModuleClasses(module.getImportedClassNames());
                    byte[] moduleBytes = emitter.emit(module.getClassName());
                    return defineClass(name, moduleBytes, 0, moduleBytes.length);
                }
                return super.findClass(name);
            }
        };

        Thread.currentThread().setContextClassLoader(loader);
        Class<?> mainClass = loader.loadClass(className);
        PrintStream oldOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            mainClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        return output.toString().trim();
    }
}
