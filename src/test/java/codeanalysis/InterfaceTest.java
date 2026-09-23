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
 * Interfaces: naming the methods a struct must have, and calling one on a value
 * whose struct is only known when the program runs.
 *
 * <p>A method could previously only be called on a value whose concrete struct
 * the compiler already knew, so no function could take "anything that can
 * describe itself".
 */
class InterfaceTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-interface");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> { try { Files.delete(path); } catch (Exception ignored) { } });
        }
    }

    // --- calling through an interface -----------------------------------------

    @Test
    void aStructIsReachedThroughTheInterfaceItImplements() throws Exception {
        String source = """
                interface Printable {
                    fn describe() -> string
                }
                struct Point { x: int, y: int }
                impl Printable for Point {
                    fn describe(self) -> string { "(" + toString(self.x) + ", " + toString(self.y) + ")" }
                }
                fn show(p: Printable) { println(p.describe()) }
                fn main() { show(Point { x: 1, y: 2 }) }
                """;
        assertEquals("(1, 2)", run(source, "InterfaceBasic"));
        assertEquals("(1, 2)", interpret(source, "InterfaceBasic"));
    }

    @Test
    void twoStructsImplementingOneInterfaceEachRunTheirOwnMethod() throws Exception {
        String source = """
                interface Shape {
                    fn area() -> int
                }
                struct Square { side: int }
                struct Rect { w: int, h: int }
                impl Shape for Square { fn area(self) -> int { self.side * self.side } }
                impl Shape for Rect { fn area(self) -> int { self.w * self.h } }
                fn total(a: Shape, b: Shape) -> int { a.area() + b.area() }
                fn main() { println(toString(total(Square { side: 3 }, Rect { w: 2, h: 5 }))) }
                """;
        assertEquals("19", run(source, "TwoImplementors"));
        assertEquals("19", interpret(source, "TwoImplementors"));
    }

    @Test
    void anInterfaceMethodTakesArguments() throws Exception {
        String source = """
                interface Greeter {
                    fn greet(name: string) -> string
                }
                struct Polite { prefix: string }
                impl Greeter for Polite {
                    fn greet(self, name: string) -> string { self.prefix + " " + name }
                }
                fn run(g: Greeter) { println(g.greet("world")) }
                fn main() { run(Polite { prefix: "hello" }) }
                """;
        assertEquals("hello world", run(source, "InterfaceArgs"));
        assertEquals("hello world", interpret(source, "InterfaceArgs"));
    }

    @Test
    void anInterfaceMethodMayReturnNothing() throws Exception {
        String source = """
                interface Runner {
                    fn run()
                }
                struct Job { id: int }
                impl Runner for Job { fn run(self) { println("job " + toString(self.id)) } }
                fn go(r: Runner) { r.run() }
                fn main() { go(Job { id: 7 }) }
                """;
        assertEquals("job 7", run(source, "InterfaceVoid"));
        assertEquals("job 7", interpret(source, "InterfaceVoid"));
    }

    @Test
    void anInterfaceDeclaresSeveralMethods() throws Exception {
        String source = """
                interface Animal {
                    fn name() -> string
                    fn legs() -> int
                }
                struct Dog { name: string }
                struct Bird { name: string }
                impl Animal for Dog {
                    fn name(self) -> string { "dog" }
                    fn legs(self) -> int { 4 }
                }
                impl Animal for Bird {
                    fn name(self) -> string { "bird" }
                    fn legs(self) -> int { 2 }
                }
                fn describe(a: Animal) -> string { a.name() + " has " + toString(a.legs()) + " legs" }
                fn main() {
                    println(describe(Dog { name: "rex" }))
                    println(describe(Bird { name: "tweety" }))
                }
                """;
        assertEquals("dog has 4 legs\nbird has 2 legs", run(source, "ManyMethods"));
        assertEquals("dog has 4 legs\nbird has 2 legs", interpret(source, "ManyMethods"));
    }

    // --- where an interface-typed value can appear ----------------------------

    @Test
    void anArrayOfAnInterfaceDispatchesPerElement() throws Exception {
        String source = """
                interface Shape { fn area() -> int }
                struct Square { side: int }
                struct Rect { w: int, h: int }
                impl Shape for Square { fn area(self) -> int { self.side * self.side } }
                impl Shape for Rect { fn area(self) -> int { self.w * self.h } }
                fn main() {
                    imut shapes: Shape[] = [Square { side: 2 }, Rect { w: 3, h: 4 }]
                    mut total = 0
                    for s in shapes { total = total + s.area() }
                    println(toString(total))
                }
                """;
        assertEquals("16", run(source, "InterfaceArray"));
        assertEquals("16", interpret(source, "InterfaceArray"));
    }

    @Test
    void anArrayWrittenWithTypeArgumentsAlsoDispatchesPerElement() throws Exception {
        String source = """
                interface Shape {
                    fn area() -> int
                    fn name() -> string
                }
                struct Square { side: int }
                struct Rect { w: int, h: int }
                impl Shape for Square {
                    fn area(self) -> int { self.side * self.side }
                    fn name(self) -> string { "square" }
                }
                impl Shape for Rect {
                    fn area(self) -> int { self.w * self.h }
                    fn name(self) -> string { "rect" }
                }
                fn main() {
                    imut shapes: Array<Shape> = [Square { side: 3 }, Rect { w: 2, h: 5 }]
                    for s in shapes { println(s.name() + " " + toString(s.area())) }
                }
                """;
        assertEquals("square 9\nrect 10", run(source, "GenericInterfaceArray"));
        assertEquals("square 9\nrect 10", interpret(source, "GenericInterfaceArray"));
    }

    @Test
    void aLocalDeclaredAsAnInterfaceDispatches() throws Exception {
        String source = """
                interface Printable { fn describe() -> string }
                struct Point { x: int }
                impl Printable for Point { fn describe(self) -> string { toString(self.x) } }
                fn main() {
                    imut p: Printable = Point { x: 5 }
                    println(p.describe())
                }
                """;
        assertEquals("5", run(source, "InterfaceLocal"));
        assertEquals("5", interpret(source, "InterfaceLocal"));
    }

    @Test
    void aFunctionReturnsAnInterface() throws Exception {
        String source = """
                interface Printable { fn describe() -> string }
                struct Point { x: int }
                impl Printable for Point { fn describe(self) -> string { toString(self.x) } }
                fn make() -> Printable { Point { x: 8 } }
                fn main() { println(make().describe()) }
                """;
        assertEquals("8", run(source, "InterfaceReturn"));
        assertEquals("8", interpret(source, "InterfaceReturn"));
    }

    @Test
    void aStructFieldHoldsAnInterface() throws Exception {
        String source = """
                interface Printable { fn describe() -> string }
                struct Point { x: int }
                impl Printable for Point { fn describe(self) -> string { toString(self.x) } }
                struct Holder { item: Printable }
                fn main() {
                    imut h = Holder { item: Point { x: 3 } }
                    println(h.item.describe())
                }
                """;
        assertEquals("3", run(source, "InterfaceField"));
        assertEquals("3", interpret(source, "InterfaceField"));
    }

    @Test
    void anImplBlockWithNoInterfaceStillDeclaresPlainMethods() throws Exception {
        String source = """
                struct Counter { n: int }
                impl Counter { fn twice(self) -> int { self.n * 2 } }
                fn main() { println(toString(Counter { n: 4 }.twice())) }
                """;
        assertEquals("8", run(source, "PlainImpl"));
        assertEquals("8", interpret(source, "PlainImpl"));
    }

    // --- what is rejected ------------------------------------------------------

    @Test
    void aStructMissingAnInterfaceMethodIsReported() {
        assertTrue(firstDiagnostic("""
                interface Printable { fn describe() -> string }
                struct Point { x: int }
                impl Printable for Point { fn other(self) -> string { "x" } }
                fn main() { println("ok") }
                """).startsWith("'Point' does not implement 'Printable': it has no method 'describe'"));
    }

    @Test
    void aMethodWithTheWrongReturnTypeIsReported() {
        assertEquals("'Point.describe' does not match 'Printable': it returns int where the interface declares string",
                firstDiagnostic("""
                interface Printable { fn describe() -> string }
                struct Point { x: int }
                impl Printable for Point { fn describe(self) -> int { self.x } }
                fn main() { println("ok") }
                """));
    }

    @Test
    void aMethodWithTheWrongNumberOfArgumentsIsReported() {
        assertEquals("'Greeter.greet' does not match 'Greeter': it takes 0 arguments where the interface declares 1",
                firstDiagnostic("""
                interface Greeter { fn greet(name: string) -> string }
                struct Greeter2 { }
                struct Greeter { }
                impl Greeter for Greeter { fn greet(self) -> string { "hi" } }
                fn main() { println("ok") }
                """));
    }

    @Test
    void animplForAnInterfaceThatWasNeverDeclaredIsReported() {
        assertEquals("Type 'Nonexistent' does not exist", firstDiagnostic("""
                struct Point { x: int }
                impl Nonexistent for Point { fn describe(self) -> string { "x" } }
                fn main() { println("ok") }
                """));
    }

    @Test
    void aCallThroughAnInterfaceNoStructImplementsIsReported() {
        assertTrue(firstDiagnostic("""
                interface Printable { fn describe() -> string }
                fn show(p: Printable) { println(p.describe()) }
                fn main() { println("ok") }
                """).startsWith("No struct implements 'Printable'"));
    }

    // --- across a module boundary ---------------------------------------------

    @Test
    void anInterfaceAndItsImplementorCrossAModuleBoundary() throws Exception {
        Files.writeString(tempDir.resolve("shapes.siyo"), """
                pub interface Shape {
                    fn area() -> int
                }
                pub struct Square { side: int }
                impl Shape for Square { fn area(self) -> int { self.side * self.side } }
                pub fn unit() -> Square { Square { side: 4 } }
                """);

        assertEquals("16", compileAndRun("""
                import "shapes"
                fn measure(s: Shape) -> int { s.area() }
                fn main() { println(toString(measure(shapes.unit()))) }
                """, "CrossModuleInterface"));
    }

    @Test
    void anInterfaceThatIsNotPublicDoesNotCrossTheBoundary() throws Exception {
        Files.writeString(tempDir.resolve("shapes.siyo"), """
                pub struct Square { side: int }
                interface Shape { fn area() -> int }
                impl Shape for Square { fn area(self) -> int { self.side * self.side } }
                """);

        assertTrue(firstDiagnosticOf("""
                import "shapes"
                fn measure(s: Shape) -> int { s.area() }
                fn main() { println("ok") }
                """, "PrivateInterface").startsWith("'Shape' is private to module 'shapes'"));
    }

    // --- helpers -------------------------------------------------------------

    private String firstDiagnostic(String source) {
        SyntaxTree tree = SyntaxTree.parse(source);
        if (tree.diagnostics().hasNext()) {
            return tree.diagnostics().get(0).getMessage();
        }
        Compilation compilation = new Compilation(tree, new ModuleRegistry(), "Diag.siyo");
        byte[] bytes = compilation.compile("Diag");
        if (bytes != null) fail("expected the program to be rejected");
        DiagnosticBox diagnostics = compilation.getGlobalScope().getDiagnostics();
        if (!diagnostics.hasNext() && compilation.getEmitDiagnostics() != null) {
            diagnostics = compilation.getEmitDiagnostics();
        }
        if (!diagnostics.hasNext()) fail("expected a diagnostic");
        return diagnostics.get(0).getMessage();
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

    private String interpret(String source, String name) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            Compilation compilation = new Compilation(
                    SyntaxTree.parse(source), new ModuleRegistry(), name + ".siyo");
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            if (result.diagnostics().hasNext()) {
                fail("Interpreter diagnostics: " + result.diagnostics().get(0).getMessage());
            }
        } finally {
            System.setOut(oldOut);
        }
        return output.toString().trim();
    }

    private String run(String source, String className) throws Exception {
        SyntaxTree tree = SyntaxTree.parse(source);
        Compilation compilation = new Compilation(tree, new ModuleRegistry(), className + ".siyo");
        byte[] bytes = compilation.compile(className);
        if (bytes == null) {
            String message = compilation.getGlobalScope().getDiagnostics().hasNext()
                    ? compilation.getGlobalScope().getDiagnostics().get(0).getMessage()
                    : tree.diagnostics().hasNext()
                    ? tree.diagnostics().get(0).getMessage()
                    : compilation.getEmitDiagnostics() != null
                    ? compilation.getEmitDiagnostics().get(0).getMessage()
                    : "unknown error";
            fail("Compilation failed: " + message);
        }
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) return defineClass(name, bytes, 0, bytes.length);
                return super.findClass(name);
            }
        };
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> cls = loader.loadClass(className);
        PrintStream oldOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        return output.toString().trim();
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
