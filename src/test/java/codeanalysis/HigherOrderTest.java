package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The higher-order collection builtins: map, filter, reduce and forEach.
 *
 * <p>Every case runs on both backends, since a closure is dispatched one way
 * in the interpreter and another in bytecode.</p>
 */
class HigherOrderTest {

    @Test
    void mapAppliesAFunctionToEveryElement() throws Exception {
        String source = """
                fn main() {
                    imut doubled = map([1, 2, 3], fn(x: int) -> int { x * 2 })
                    println(toString(doubled))
                }
                """;
        assertEquals("[2, 4, 6]", run(source, "MapBasic"));
        assertEquals("[2, 4, 6]", interpret(source, "MapBasic"));
    }

    @Test
    void mapMayChangeTheElementType() throws Exception {
        String source = """
                fn main() {
                    imut names = map([1, 2], fn(x: int) -> string { "n" + toString(x) })
                    println(toString(names))
                }
                """;
        assertEquals("[n1, n2]", run(source, "MapToString"));
        assertEquals("[n1, n2]", interpret(source, "MapToString"));
    }

    @Test
    void filterKeepsTheElementsThePredicateAccepts() throws Exception {
        String source = """
                fn main() {
                    imut odd = filter([1, 2, 3, 4, 5], fn(x: int) -> bool { x % 2 == 1 })
                    println(toString(odd))
                }
                """;
        assertEquals("[1, 3, 5]", run(source, "FilterBasic"));
        assertEquals("[1, 3, 5]", interpret(source, "FilterBasic"));
    }

    @Test
    void filterKeepsTheElementTypeItWasGiven() throws Exception {
        assertEquals("6", run("""
                fn main() {
                    imut kept = filter([1, 2, 3, 4], fn(x: int) -> bool { x % 2 == 0 })
                    mut total = 0
                    for n in kept { total += n }
                    println(toString(total))
                }
                """, "FilterElementType"));
    }

    @Test
    void reduceFoldsTheElementsIntoOneValue() throws Exception {
        String source = """
                fn main() {
                    imut total = reduce([1, 2, 3, 4], fn(acc: int, x: int) -> int { acc + x }, 0)
                    println(toString(total))
                }
                """;
        assertEquals("10", run(source, "ReduceSum"));
        assertEquals("10", interpret(source, "ReduceSum"));
    }

    @Test
    void reduceMayBuildAString() throws Exception {
        assertEquals("|a|b|c", run("""
                fn main() {
                    println(toString(reduce(["a", "b", "c"],
                        fn(acc: string, s: string) -> string { acc + "|" + s }, "")))
                }
                """, "ReduceConcat"));
    }

    @Test
    void forEachRunsAFunctionForItsEffects() throws Exception {
        String source = """
                fn main() {
                    forEach([1, 2, 3], fn(x: int) { println("n=" + toString(x)) })
                }
                """;
        assertEquals("n=1\nn=2\nn=3", run(source, "ForEachPrint"));
        assertEquals("n=1\nn=2\nn=3", interpret(source, "ForEachPrint"));
    }

    @Test
    void aHigherOrderCallMayBeChained() throws Exception {
        String source = """
                fn main() {
                    imut total = reduce(
                        map(filter([1, 2, 3, 4, 5, 6], fn(x: int) -> bool { x % 2 == 0 }),
                            fn(x: int) -> int { x * x }),
                        fn(acc: int, x: int) -> int { acc + x }, 0)
                    println(toString(total))
                }
                """;
        assertEquals("56", run(source, "ChainedHigherOrder"));
        assertEquals("56", interpret(source, "ChainedHigherOrder"));
    }

    @Test
    void aClosureUsedByMapMayCaptureAVariable() throws Exception {
        assertEquals("[11, 12]", run("""
                fn main() {
                    imut base = 10
                    println(toString(map([1, 2], fn(x: int) -> int { x + base })))
                }
                """, "MapWithCapture"));
    }

    @Test
    void aNamedFunctionMayBePassedToMap() throws Exception {
        assertEquals("[2, 4]", run("""
                fn twice(x: int) -> int { x * 2 }
                fn main() { println(toString(map([1, 2], twice))) }
                """, "MapNamedFunction"));
    }

    @Test
    void mapWithNoArgumentsIsStillTheMapConstructor() throws Exception {
        String source = """
                fn main() {
                    mut m = map()
                    m.set("k", 1)
                    println(toString(m.get("k")))
                }
                """;
        assertEquals("1", run(source, "MapConstructor"));
        assertEquals("1", interpret(source, "MapConstructor"));
    }

    // --- indexing a map ------------------------------------------------------

    @Test
    void aMapIsReadAndWrittenByIndex() throws Exception {
        String source = """
                fn main() {
                    mut m = map()
                    m["k"] = 1
                    println(toString(m["k"]))
                }
                """;
        assertEquals("1", run(source, "MapIndex"));
        assertEquals("1", interpret(source, "MapIndex"));
    }

    @Test
    void aMapLiteralIsReadByIndex() throws Exception {
        String source = """
                fn main() {
                    imut ages = {"ada": 36, "alan": 41}
                    println(toString(ages["alan"]))
                }
                """;
        assertEquals("41", run(source, "MapLiteralIndex"));
        assertEquals("41", interpret(source, "MapLiteralIndex"));
    }

    @Test
    void aMissingKeyReadsAsNull() throws Exception {
        String source = """
                fn main() {
                    imut m = {"a": 1}
                    if m["b"] == null { println("absent") }
                }
                """;
        assertEquals("absent", run(source, "MapMissingKey"));
        assertEquals("absent", interpret(source, "MapMissingKey"));
    }

    @Test
    void aMapMayBeKeyedByAnInt() throws Exception {
        String source = """
                fn main() {
                    mut m = map()
                    m[7] = "seven"
                    println(toString(m[7]))
                }
                """;
        assertEquals("seven", run(source, "MapIntKey"));
        assertEquals("seven", interpret(source, "MapIntKey"));
    }

    @Test
    void mapWorksOnAnArrayOfStructs() throws Exception {
        assertEquals("[3, 7]", run("""
                struct Point { x: int, y: int }
                fn main() {
                    imut points = [Point { x: 1, y: 2 }, Point { x: 3, y: 4 }]
                    println(toString(map(points, fn(p: Point) -> int { p.x + p.y })))
                }
                """, "MapStructs"));
    }

    // --- helpers -------------------------------------------------------------

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
}
