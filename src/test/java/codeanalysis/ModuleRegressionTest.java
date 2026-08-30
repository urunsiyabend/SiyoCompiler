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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Regression tests for type information crossing a Siyo module boundary. */
class ModuleRegressionTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-module-regression");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> { try { Files.delete(path); } catch (Exception ignored) { } });
        }
    }

    @Test
    void importedEnumsAndImplMethodsRemainUsable() throws Exception {
        write("model.siyo", """
                enum PageKind { Page, Post }
                struct Page { title: string, kind: int }
                fn page(title: string) -> Page { Page { title: title, kind: PageKind.Post } }
                impl Page {
                    fn isPost(self) -> bool { self.kind == PageKind.Post }
                }
                """);

        String output = compileAndRun("""
                import "model"
                fn main() {
                    mut page = model.page("Aja")
                    println(toString(PageKind.Post))
                    println(toString(page.isPost()))
                }
                """, "ImportedSymbolsMain");

        assertEquals("1\ntrue", output);
    }

    @Test
    void importedArrayReturnKeepsItsElementType() throws Exception {
        write("provider.siyo", """
                import "std/strings"
                fn characters(value: string) -> string[] { strings.chars(value) }
                """);
        write("provider_two.siyo", """
                import "std/strings"
                fn characters(value: string) -> string[] { strings.chars(value) }
                """);

        String output = compileAndRun("""
                import "provider"
                import "provider_two"
                fn main() {
                    mut left = provider.characters("alpha")
                    mut right = provider_two.characters("beta")
                    println(left[0] + right[0])
                }
                """, "ImportedArraysMain");

        assertEquals("ab", output);
    }

    @Test
    void inlineLambdaKeepsImportedStructParameterTypes() throws Exception {
        write("model.siyo", """
                struct Page { title: string }
                fn page(title: string) -> Page { Page { title: title } }
                """);

        String output = compileAndRun("""
                import "model"
                fn main() {
                    mut pages: Page[] = []
                    push(pages, model.page("z"))
                    push(pages, model.page("a"))
                    sort(pages, fn(a: Page, b: Page) -> int {
                        if a.title < b.title { return -1 }
                        if a.title > b.title { return 1 }
                        0
                    })
                    println(pages[0].title)
                }
                """, "ImportedLambdaMain");

        assertEquals("a", output);
    }

    @Test
    void importedJavaTypeAnnotationSelectsExactConstructorOverload() throws Exception {
        Path input = tempDir.resolve("input.txt");
        Files.writeString(input, "aja");
        String escapedPath = input.toString().replace("\\", "\\\\").replace("\"", "\\\"");

        String output = compileAndRun("""
                import java "java.io.File"
                import java "java.io.FileInputStream"
                fn open(file: File) {
                    mut input = FileInputStream.new(file)
                    input.close()
                }
                fn main() {
                    open(File.new("%s"))
                    println("opened")
                }
                """.formatted(escapedPath), "JavaAnnotationMain");

        assertEquals("opened", output);
    }

    @Test
    void missingQualifiedModuleMemberNamesTheFunction() throws Exception {
        String source = """
                import "std/io"
                fn main() { println(toString(io.fileExists("missing"))) }
                """;
        Path mainPath = tempDir.resolve("main.siyo");
        Files.writeString(mainPath, source);
        Compilation compilation = new Compilation(
                SyntaxTree.parse(source), new ModuleRegistry(), mainPath.toString());

        assertNull(compilation.compile("MissingMemberMain"));
        assertTrue(compilation.getGlobalScope().getDiagnostics().hasNext());
        assertEquals("Function 'io.fileExists' does not exist",
                compilation.getGlobalScope().getDiagnostics().get(0).getMessage());
    }

    @Test
    void erasedObjectCannotSilentlyChooseAnAmbiguousJavaOverload() throws Exception {
        String source = """
                import java "java.io.File"
                import java "java.io.FileInputStream"
                fn open(file: object) { FileInputStream.new(file) }
                fn main() { open(File.new("input.txt")) }
                """;
        Path mainPath = tempDir.resolve("main.siyo");
        Files.writeString(mainPath, source);
        Compilation compilation = new Compilation(
                SyntaxTree.parse(source), new ModuleRegistry(), mainPath.toString());

        assertNull(compilation.compile("AmbiguousJavaMain"));
        assertTrue(compilation.getGlobalScope().getDiagnostics().hasNext());
        assertEquals(
                "Java call 'FileInputStream.new' is ambiguous for erased object arguments; add an imported Java type annotation",
                compilation.getGlobalScope().getDiagnostics().get(0).getMessage());
    }

    @Test
    void transitiveImportsDoNotReExportFunctionsWithTheWrongJvmOwner() throws Exception {
        write("model.siyo", "fn answer() -> int { 42 }");
        write("content.siyo", """
                import "model"
                fn contentValue() -> int { model.answer() }
                """);
        write("builder.siyo", """
                import "content"
                import "model"
                fn build() -> int { model.answer() }
                """);

        String output = compileAndRun("""
                import "builder"
                fn main() { println(toString(builder.build())) }
                """, "TransitiveOwnerMain");

        assertEquals("42", output);
    }

    @Test
    void lambdaReturnOpcodeIsIndependentFromItsContainingModuleFunction() throws Exception {
        write("model.siyo", """
                struct Page { title: string }
                fn page(title: string) -> Page { Page { title: title } }
                """);
        write("sorting.siyo", """
                import "model"
                fn sorted() -> Page[] {
                    mut pages: Page[] = [model.page("z"), model.page("a")]
                    sort(pages, fn(a: Page, b: Page) -> int {
                        if a.title < b.title { return -1 }
                        if a.title > b.title { return 1 }
                        0
                    })
                    pages
                }
                """);

        String output = compileAndRun("""
                import "sorting"
                fn main() { println(sorting.sorted()[0].title) }
                """, "ModuleLambdaOpcodeMain");

        assertEquals("a", output);
    }

    @Test
    void structArrayParametersKeepImplIdentityDuringIteration() throws Exception {
        write("model.siyo", """
                struct Page { kind: int }
                fn page() -> Page { Page { kind: 1 } }
                impl Page { fn isPost(self) -> bool { self.kind == 1 } }
                """);
        write("builder.siyo", """
                import "model"
                fn hasPost(pages: Page[]) -> bool {
                    for page in pages { if page.isPost() { return true } }
                    false
                }
                """);

        String output = compileAndRun("""
                import "model"
                import "builder"
                fn main() {
                    mut pages: Page[] = [model.page()]
                    println(toString(builder.hasPost(pages)))
                }
                """, "StructArrayImplMain");

        assertEquals("true", output);
    }

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
