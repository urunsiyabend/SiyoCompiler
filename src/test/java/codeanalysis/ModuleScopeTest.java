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
 * Module scope: a module is a namespace, and its symbols keep their identity
 * however deep the import chain.
 *
 * <p>Every case here was a defect found while building a real multi-module
 * library. A module used to leak the functions it imported into its own
 * exports, which made same-named functions from different modules collide and
 * made module-level variables unreachable past the first import hop.
 */
class ModuleScopeTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-module-scope");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> { try { Files.delete(path); } catch (Exception ignored) { } });
        }
    }

    // --- symbol identity across modules ------------------------------------

    @Test
    void twoModulesMayDeclareTheSameFunctionName() throws Exception {
        write("mine.siyo", "fn parse(raw: string) -> string { \"mine:\" + raw }");
        write("theirs.siyo", "fn parse(raw: string) -> string { \"theirs:\" + raw }");

        String output = compileAndRun("""
                import "mine"
                import "theirs"
                fn main() {
                    println(mine.parse("a"))
                    println(theirs.parse("b"))
                }
                """, "SameNameMain");

        assertEquals("mine:a\ntheirs:b", output);
    }

    @Test
    void aQualifiedCallInsideAnImportedModuleIsNotHijacked() throws Exception {
        // `mid` imports a module that exports `parse` and also calls std/json's
        // `parse`. The bare name must not decide which one runs.
        write("mine.siyo", "fn parse(raw: string) -> string { raw }");
        write("mid.siyo", """
                import "mine"
                import "std/json"
                fn go(s: string) -> string { toString(json.parse(s).get("a")) }
                fn local(s: string) -> string { mine.parse(s) }
                """);

        String output = compileAndRun("""
                import "mid"
                fn main() {
                    println(mid.go("{\\"a\\": 1}"))
                    println(mid.local("kept"))
                }
                """, "QualifiedCallMain");

        assertEquals("1\nkept", output);
    }

    @Test
    void aModuleFunctionDoesNotShadowAnImplMethodOfTheSameName() throws Exception {
        write("store.siyo", """
                fn get(key: string) -> string { "module:" + key }
                """);
        write("model.siyo", """
                struct Box { v: string }
                impl Box {
                    fn new(v: string) -> Box { Box { v: v } }
                    fn get(self) -> string { "method:" + self.v }
                }
                """);

        String output = compileAndRun("""
                import "store"
                import "model"
                fn main() {
                    mut b = Box.new("x")
                    println(b.get())
                    println(store.get("y"))
                }
                """, "MethodShadowMain");

        assertEquals("method:x\nmodule:y", output);
    }

    // --- module-level variables --------------------------------------------

    @Test
    void aModuleLevelVariableSurvivesATransitiveImport() throws Exception {
        write("mine.siyo", """
                imut LABEL = "hi"
                mut counter = 0
                fn label() -> string { LABEL }
                fn bump() -> int { counter = counter + 1  counter }
                """);
        write("mid.siyo", """
                import "mine"
                fn go() -> string { mine.label() }
                fn twice() -> int { mine.bump()  mine.bump() }
                """);

        String output = compileAndRun("""
                import "mid"
                fn main() {
                    println(mid.go())
                    println(toString(mid.twice()))
                }
                """, "TransitiveVarMain");

        assertEquals("hi\n2", output);
    }

    @Test
    void aModuleLevelVariableSurvivesADiamondImport() throws Exception {
        write("mine.siyo", """
                mut enabled = true
                fn setEnabled(v: bool) { enabled = v }
                fn isEnabled() -> bool { enabled }
                """);
        write("mid.siyo", """
                import "mine"
                fn on() { mine.setEnabled(true) }
                """);
        write("mid2.siyo", """
                import "mine"
                fn off() { mine.setEnabled(false) }
                """);

        String output = compileAndRun("""
                import "mine"
                import "mid"
                import "mid2"
                fn main() {
                    mid2.off()
                    println(toString(mine.isEnabled()))
                    mid.on()
                    println(toString(mine.isEnabled()))
                }
                """, "DiamondVarMain");

        assertEquals("false\ntrue", output);
    }

    @Test
    void moduleLevelVariablesAreExportedUnderTheModuleName() throws Exception {
        write("status.siyo", """
                imut OK = 200
                imut NOT_FOUND = 404
                mut counter = 7
                fn reason(code: int) -> string { if code == 200 { "OK" } else { "?" } }
                """);

        String output = compileAndRun("""
                import "status"
                fn main() {
                    println(toString(status.OK))
                    println(toString(status.NOT_FOUND))
                    println(status.reason(status.OK))
                    println(toString(status.counter))
                }
                """, "ExportedVarMain");

        assertEquals("200\n404\nOK\n7", output);
    }

    @Test
    void anExportedModuleVariableReflectsWritesMadeInsideItsModule() throws Exception {
        write("counterMod.siyo", """
                mut total = 0
                fn add(n: int) { total = total + n }
                """);

        String output = compileAndRun("""
                import "counterMod"
                fn main() {
                    counterMod.add(5)
                    counterMod.add(4)
                    println(toString(counterMod.total))
                }
                """, "ExportedWriteMain");

        assertEquals("9", output);
    }

    // --- a module keeps its own exports -------------------------------------

    @Test
    void aModuleDoesNotReExportWhatItImports() throws Exception {
        write("inner.siyo", "fn hidden() -> int { 1 }");
        write("outer.siyo", """
                import "inner"
                fn visible() -> int { inner.hidden() }
                """);

        String source = """
                import "outer"
                fn main() { println(toString(outer.hidden())) }
                """;
        Path mainPath = tempDir.resolve("main.siyo");
        Files.writeString(mainPath, source);
        Compilation compilation = new Compilation(
                SyntaxTree.parse(source), new ModuleRegistry(), mainPath.toString());

        assertEquals(null, compilation.compile("NoReExportMain"));
        assertEquals("Function 'outer.hidden' does not exist",
                compilation.getGlobalScope().getDiagnostics().get(0).getMessage());
    }

    // --- a facade module ----------------------------------------------------

    @Test
    void aFacadeModuleCarriesTheTypesAndMethodsItRePublishes() throws Exception {
        // One import has to be enough for a library to be usable: the structs a
        // module re-exports bring their impl methods with them.
        write("core.siyo", """
                struct Server { routes: int }
                impl Server {
                    fn new() -> Server { Server { routes: 0 } }
                    fn addRoute(self) -> Server {
                        self.routes += 1
                        self
                    }
                    fn count(self) -> int { self.routes }
                }
                """);
        write("facade.siyo", """
                import "core"
                fn newServer() -> Server { Server.new() }
                """);

        String output = compileAndRun("""
                import "facade"
                fn main() {
                    mut app = facade.newServer()
                    app.addRoute().addRoute()
                    println(toString(app.count()))
                }
                """, "FacadeMain");

        assertEquals("2", output);
    }

    @Test
    void aFileThatWouldCollideWithAnImportedModuleIsRejected() throws Exception {
        // A source file compiles to a class named after it. Two classes with one
        // name used to resolve arbitrarily and fail at run time.
        write("client.siyo", """
                struct Client { v: int }
                impl Client { fn new() -> Client { Client { v: 1 } } }
                """);

        String source = """
                import "client"
                fn main() {
                    mut c = Client.new()
                    println(toString(c.v))
                }
                """;
        Path mainPath = tempDir.resolve("caller.siyo");
        Files.writeString(mainPath, source);
        Compilation compilation = new Compilation(
                SyntaxTree.parse(source), new ModuleRegistry(), mainPath.toString());

        assertEquals(null, compilation.compile("Client"));
        assertTrue(compilation.getEmitDiagnostics().get(0).getMessage()
                .startsWith("This file and the module 'client' it imports would both compile"));
    }

    // --- helpers ------------------------------------------------------------

    private void write(String name, String source) throws Exception {
        Files.writeString(tempDir.resolve(name), source);
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
