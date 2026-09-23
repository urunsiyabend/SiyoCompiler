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
 * Types written with type arguments, and the declarations that take them.
 *
 * <p>A container's contents had to be described by a bare {@code T[]} or not at
 * all, a function over any type had to be rewritten per type, and a struct
 * could only be turned into data by naming every field by hand.
 */
class GenericsTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-generics");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> { try { Files.delete(path); } catch (Exception ignored) { } });
        }
    }

    // --- Array, Map, Set with type arguments -----------------------------------

    @Test
    void anArrayIsDeclaredWithItsElementType() throws Exception {
        String source = """
                fn main() {
                    imut xs: Array<int> = [1, 2, 3]
                    mut total = 0
                    for x in xs { total = total + x }
                    println(toString(total))
                }
                """;
        assertEquals("6", run(source, "ArrayOfInt"));
        assertEquals("6", interpret(source, "ArrayOfInt"));
    }

    @Test
    void anArrayTypeIsAcceptedAsAParameterAndAReturn() throws Exception {
        String source = """
                fn doubled(xs: Array<int>) -> Array<int> {
                    mut out: Array<int> = []
                    for x in xs { push(out, x * 2) }
                    return out
                }
                fn main() { println(toString(doubled([1, 2, 3]))) }
                """;
        assertEquals("[2, 4, 6]", run(source, "ArrayParamReturn"));
        assertEquals("[2, 4, 6]", interpret(source, "ArrayParamReturn"));
    }

    @Test
    void aMapsValuesKeepTheirDeclaredType() throws Exception {
        String source = """
                fn main() {
                    imut m: Map<string, int> = {"a": 1, "b": 2}
                    println(toString(m["a"] + m["b"]))
                }
                """;
        assertEquals("3", run(source, "MapValueType"));
        assertEquals("3", interpret(source, "MapValueType"));
    }

    @Test
    void aSetIsDeclaredWithItsElementType() throws Exception {
        String source = """
                fn main() {
                    imut s: Set<int> = #{1, 2, 3, 3}
                    println(toString(len(s)))
                }
                """;
        assertEquals("3", run(source, "SetOfInt"));
        assertEquals("3", interpret(source, "SetOfInt"));
    }

    @Test
    void typeArgumentsNest() throws Exception {
        String source = """
                fn main() {
                    imut m: Map<string, Array<int>> = {"a": [1, 2], "b": [3]}
                    println(toString(len(m)))
                }
                """;
        assertEquals("2", run(source, "NestedTypeArguments"));
        assertEquals("2", interpret(source, "NestedTypeArguments"));
    }

    @Test
    void aLessThanComparisonIsStillAComparison() throws Exception {
        String source = """
                fn main() {
                    imut a = 1
                    imut b = 2
                    println(toString(a < b))
                    println(toString(a < b == true))
                }
                """;
        assertEquals("true\ntrue", run(source, "LessThanStillWorks"));
        assertEquals("true\ntrue", interpret(source, "LessThanStillWorks"));
    }

    // --- generic functions ------------------------------------------------------

    @Test
    void aGenericFunctionKeepsTheTypeItWasGiven() throws Exception {
        String source = """
                fn identity<T>(x: T) -> T { x }
                fn main() {
                    println(toString(identity(5) + 1))
                    println(identity("hi") + "!")
                }
                """;
        assertEquals("6\nhi!", run(source, "GenericIdentity"));
        assertEquals("6\nhi!", interpret(source, "GenericIdentity"));
    }

    @Test
    void aGenericFunctionTakesAContainerOfTheTypeParameter() throws Exception {
        String source = """
                fn first<T>(xs: T[]) -> T { xs[0] }
                fn main() {
                    println(toString(first([7, 8, 9]) + 1))
                    println(first(["a", "b"]) + "!")
                }
                """;
        assertEquals("8\na!", run(source, "GenericFirst"));
        assertEquals("8\na!", interpret(source, "GenericFirst"));
    }

    @Test
    void aGenericFunctionMayDeclareSeveralTypeParameters() throws Exception {
        String source = """
                fn firstOf<A, B>(a: A, b: B) -> A { a }
                fn main() { println(toString(firstOf(3, "x") * 2)) }
                """;
        assertEquals("6", run(source, "TwoTypeParameters"));
        assertEquals("6", interpret(source, "TwoTypeParameters"));
    }

    @Test
    void aGenericFunctionIsCalledAtMoreThanOneType() throws Exception {
        String source = """
                fn pairUp<T>(a: T, b: T) -> T[] { [a, b] }
                fn main() {
                    println(toString(pairUp(1, 2)))
                    println(toString(pairUp("a", "b")))
                }
                """;
        assertEquals("[1, 2]\n[a, b]", run(source, "GenericPair"));
        assertEquals("[1, 2]\n[a, b]", interpret(source, "GenericPair"));
    }

    // --- generic sum types ------------------------------------------------------

    @Test
    void aSumTypeIsDeclaredOverATypeParameter() throws Exception {
        String source = """
                type Option<T> = Some(T) | None
                fn main() {
                    println(toString(Some(5)))
                    println(toString(Some("text")))
                    println(toString(None))
                }
                """;
        assertEquals("Some(5)\nSome(text)\nNone", run(source, "GenericUnion"));
        assertEquals("Some(5)\nSome(text)\nNone", interpret(source, "GenericUnion"));
    }

    @Test
    void aGenericSumTypeIsMatchedAtTheTypeItWasDeclaredAt() throws Exception {
        String source = """
                type Option<T> = Some(T) | None
                fn describe(o: Option<int>) -> string {
                    match o {
                        Some(v) => "got " + toString(v),
                        None => "nothing"
                    }
                }
                fn main() {
                    println(describe(Some(3)))
                    println(describe(None))
                }
                """;
        assertEquals("got 3\nnothing", run(source, "GenericUnionMatch"));
        assertEquals("got 3\nnothing", interpret(source, "GenericUnionMatch"));
    }

    @Test
    void aGenericPayloadIsUsableAsTheTypeItHolds() throws Exception {
        String source = """
                type Option<T> = Some(T) | None
                fn unwrap(o: Option<int>) -> int {
                    match o { Some(v) => v + 1, None => 0 }
                }
                fn main() { println(toString(unwrap(Some(3)))) }
                """;
        assertEquals("4", run(source, "GenericPayload"));
        assertEquals("4", interpret(source, "GenericPayload"));
    }

    @Test
    void aMatchOverAGenericSumTypeIsStillCheckedForExhaustiveness() {
        assertTrue(firstDiagnostic("""
                type Option<T> = Some(T) | None
                fn describe(o: Option<int>) -> string {
                    match o { Some(v) => toString(v) }
                }
                fn main() { println(describe(None)) }
                """).startsWith("This match on 'Option' does not cover None"));
    }

    @Test
    void aGenericSumTypeCrossesAModuleBoundary() throws Exception {
        Files.writeString(tempDir.resolve("opt.siyo"), """
                pub type Option<T> = Some(T) | None
                pub fn wrap(n: int) -> Option<int> { Some(n) }
                """);

        assertEquals("got 4", compileAndRun("""
                import "opt"
                fn main() {
                    imut o: Option<int> = opt.wrap(4)
                    println(match o {
                        Some(v) => "got " + toString(v),
                        None => "nothing"
                    })
                }
                """, "CrossModuleGenericUnion"));
    }

    // --- reflection over a struct's fields --------------------------------------

    @Test
    void aStructsFieldNamesAreReadable() throws Exception {
        String source = """
                struct Point { x: int, y: int }
                fn main() {
                    imut p = Point { x: 1, y: 2 }
                    println(toString(fields(p)))
                }
                """;
        assertEquals("[x, y]", run(source, "StructFields"));
        assertEquals("[x, y]", interpret(source, "StructFields"));
    }

    @Test
    void aFieldIsReadAndWrittenByName() throws Exception {
        String source = """
                struct Point { x: int, y: int }
                fn main() {
                    mut p = Point { x: 1, y: 2 }
                    println(toString(field(p, "y")))
                    setField(p, "y", 9)
                    println(toString(p.y))
                }
                """;
        assertEquals("2\n9", run(source, "FieldByName"));
        assertEquals("2\n9", interpret(source, "FieldByName"));
    }

    @Test
    void aStructBecomesAMapWithoutNamingEachField() throws Exception {
        String source = """
                struct User { name: string, age: int }
                fn main() {
                    imut u = User { name: "ada", age: 36 }
                    imut m = toMap(u)
                    println(toString(len(m)))
                    println(toString(m["name"]))
                }
                """;
        assertEquals("2\nada", run(source, "StructToMap"));
        assertEquals("2\nada", interpret(source, "StructToMap"));
    }

    @Test
    void aValueSaysWhichStructItIs() throws Exception {
        String source = """
                struct Point { x: int }
                fn main() {
                    println(typeName(Point { x: 1 }))
                    println("[" + typeName(5) + "]")
                }
                """;
        assertEquals("Point\n[]", run(source, "TypeNameOfValue"));
        assertEquals("Point\n[]", interpret(source, "TypeNameOfValue"));
    }

    @Test
    void oneFunctionDescribesAnyStruct() throws Exception {
        String source = """
                struct Point { x: int, y: int }
                struct User { name: string }
                fn describe(v: object) -> string {
                    mut out = typeName(v) + ":"
                    for f in fields(v) { out = out + " " + f + "=" + toString(field(v, f)) }
                    return out
                }
                fn main() {
                    println(describe(Point { x: 1, y: 2 }))
                    println(describe(User { name: "ada" }))
                }
                """;
        assertEquals("Point: x=1 y=2\nUser: name=ada", run(source, "DescribeAnyStruct"));
        assertEquals("Point: x=1 y=2\nUser: name=ada", interpret(source, "DescribeAnyStruct"));
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
