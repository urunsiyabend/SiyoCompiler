package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gaps found by writing LogScope (projects/logscope), each pinned on both
 * backends. See projects/logscope/PAIN_POINTS.md for the reproducers.
 */
class DogfoodRegressionTest {

    // --- P2: a lambda's tail value ---------------------------------------------

    @Test
    void aLambdaReturnsItsIfElseTailValue() throws Exception {
        String source = """
                fn main() {
                    imut f1 = fn(a: int) -> int { if a > 0 { 1 } else { 2 } }
                    imut f2 = fn(a: int) -> int { if a > 0 { 1 } else if a < 0 { 3 } else { 2 } }
                    imut f3 = fn(a: int) -> string { if a > 0 { "pos" } else { "neg" } }
                    println(toString(f1(5)) + " " + toString(f1(-5)))
                    println(toString(f2(5)) + " " + toString(f2(-5)) + " " + toString(f2(0)))
                    println(f3(5) + " " + f3(-5))
                }
                """;
        assertEquals("1 2\n1 3 2\npos neg", run(source, "LambdaIfTail"));
        assertEquals("1 2\n1 3 2\npos neg", interpret(source, "LambdaIfTail"));
    }

    @Test
    void anInlineComparatorWithAnIfElseTailSorts() throws Exception {
        String source = """
                fn main() {
                    mut xs = ["b", "c", "a"]
                    sort(xs, fn(a: string, b: string) -> int { if a < b { -1 } else if a > b { 1 } else { 0 } })
                    println(toString(xs))
                }
                """;
        assertEquals("[a, b, c]", run(source, "LambdaComparator"));
        assertEquals("[a, b, c]", interpret(source, "LambdaComparator"));
    }

    @Test
    void aLambdaTailValueIsWidenedToItsDeclaredType() throws Exception {
        String source = """
                fn main() {
                    imut f = fn() -> float { 3 }
                    println(toString(f()))
                }
                """;
        assertEquals("3.0", run(source, "LambdaWidenTail"));
        assertEquals("3.0", interpret(source, "LambdaWidenTail"));
    }

    // --- P3: a container's value type survives indexing ------------------------

    @Test
    void aMapOfArraysKeepsTheElementStruct() throws Exception {
        String source = """
                struct Rec { n: int }
                fn main() {
                    mut groups: Map<string, Array<Rec>> = {}
                    groups["e"] = [Rec { n: 1 }, Rec { n: 2 }]
                    push(groups["e"], Rec { n: 3 })
                    println(toString(groups["e"][1].n))
                    println(toString(len(groups["e"])))
                }
                """;
        assertEquals("2\n3", run(source, "MapOfArrays"));
        assertEquals("2\n3", interpret(source, "MapOfArrays"));
    }

    @Test
    void aValueTakenFromAMapOfArraysKeepsItsType() throws Exception {
        String source = """
                struct Rec { n: int }
                fn main() {
                    mut groups: Map<string, Array<Rec>> = {}
                    groups["e"] = [Rec { n: 4 }, Rec { n: 5 }]
                    imut recs = groups["e"]
                    println(toString(recs[1].n + 1))
                    mut total = 0
                    for r in groups["e"] { total = total + r.n }
                    println(toString(total))
                }
                """;
        assertEquals("6\n9", run(source, "MapOfArraysLocal"));
        assertEquals("6\n9", interpret(source, "MapOfArraysLocal"));
    }

    @Test
    void aNestedMapKeepsItsInnerValueType() throws Exception {
        String source = """
                struct Rec { n: int }
                fn main() {
                    mut m: Map<string, Map<string, Rec>> = {}
                    m["a"] = {"x": Rec { n: 1 }}
                    println(toString(m["a"]["x"].n + 1))
                }
                """;
        assertEquals("2", run(source, "NestedMap"));
        assertEquals("2", interpret(source, "NestedMap"));
    }

    // --- P5: a triple-quoted string that starts on its opening line -----------

    @Test
    void aTripleQuotedStringMayStartOnTheOpeningLine() throws Exception {
        String source = """
                fn usage() -> string {
                    \"""usage: tool <file>

                  --flag  does "things" here\"""
                }
                fn main() {
                    println(usage())
                    println(\"""one line\""")
                }
                """;
        String expected = "usage: tool <file>\n\n  --flag  does \"things\" here\none line";
        assertEquals(expected, run(source, "TripleQuoteInline"));
        assertEquals(expected, interpret(source, "TripleQuoteInline"));
    }

    @Test
    void aTripleQuotedStringOnItsOwnLineStillDropsTheLeadingNewline() throws Exception {
        String source = """
                fn main() {
                    println(\"""
                a
                b\""")
                }
                """;
        assertEquals("a\nb", run(source, "TripleQuoteBlock"));
        assertEquals("a\nb", interpret(source, "TripleQuoteBlock"));
    }

    // --- P6: an arm that throws has no type of its own ------------------------

    @Test
    void aMatchArmThatThrowsSitsBesideValueArms() throws Exception {
        String source = """
                struct Opts { name: string }
                type Parsed = Ok(Opts) | Bad(string)
                fn unwrap(p: Parsed) -> Opts {
                    match p {
                        Ok(o) => o,
                        Bad(why) => { throw "bad: " + why }
                    }
                }
                fn main() {
                    println(unwrap(Ok(Opts { name: "x" })).name)
                    try { unwrap(Bad("nope")) } catch e { println(e) }
                }
                """;
        assertEquals("x\nbad: nope", run(source, "MatchArmThrows"));
        assertEquals("x\nbad: nope", interpret(source, "MatchArmThrows"));
    }

    @Test
    void anIfExpressionTakesItsTypeFromTheArmThatDoesNotThrow() throws Exception {
        String source = """
                fn half(n: int) -> int {
                    imut h = if n % 2 == 0 { n / 2 } else { throw "odd" }
                    h + 0
                }
                fn label(n: int) -> string {
                    imut s = if n < 0 { throw "negative" } else { "n=" + toString(n) }
                    s
                }
                fn main() {
                    println(toString(half(8)))
                    println(label(3))
                    try { half(3) } catch e { println(e) }
                    try { label(-1) } catch e { println(e) }
                }
                """;
        assertEquals("4\nn=3\nodd\nnegative", run(source, "IfArmThrows"));
        assertEquals("4\nn=3\nodd\nnegative", interpret(source, "IfArmThrows"));
    }

    // --- P9: a loop inside a block-bodied arm ----------------------------------

    @Test
    void aLoopInsideAMatchOrIfArmRunsInterpreted() throws Exception {
        String source = """
                fn main() {
                    imut n = 2
                    match n {
                        2 => {
                            mut out = 0
                            for k in [1, 2, 3] { out = out + k }
                            println(toString(out))
                        },
                        _ => println("other")
                    }
                    imut s = if n > 1 {
                        mut t = 0
                        while t < 3 { t = t + 1 }
                        t
                    } else { 0 }
                    println(toString(s))
                }
                """;
        assertEquals("6\n3", run(source, "LoopInArm"));
        assertEquals("6\n3", interpret(source, "LoopInArm"));
    }

    // --- P11: a caught exception unwinds the frames it passed through ----------

    @Test
    void aLoopKeepsGoingAfterCatchingAnExceptionFromACall() throws Exception {
        String source = """
                fn deep(k: int) -> int {
                    imut z = k
                    throw "deep " + toString(z)
                }
                fn runAll(names: string[]) -> int {
                    mut failed = 0
                    for mut i = 0 i < len(names) i = i + 1 {
                        try { deep(i) } catch e { failed += 1 }
                    }
                    failed
                }
                fn main() {
                    println("failed " + toString(runAll(["a", "b", "c"])))
                }
                """;
        assertEquals("failed 3", run(source, "CatchInLoop"));
        assertEquals("failed 3", interpret(source, "CatchInLoop"));
    }

    @Test
    void aCallerReadsItsOwnParametersAfterCatching() throws Exception {
        String source = """
                fn fail(n: int) -> int { throw "no" }
                fn guarded(label: string, n: int) -> string {
                    imut r = try { fail(n) } catch e { -1 }
                    label + " " + toString(n) + " " + toString(r)
                }
                fn main() { println(guarded("x", 4)) }
                """;
        assertEquals("x 4 -1", run(source, "CatchParams"));
        assertEquals("x 4 -1", interpret(source, "CatchParams"));
    }

    // --- P8: asking what kind of value an erased value is ---------------------

    @Test
    void typeOfNamesEveryKindOfValue() throws Exception {
        String source = """
                struct Point { x: int }
                type Shape = Circle(int) | Dot
                fn main() {
                    mut values: object[] = []
                    push(values, 1)
                    push(values, 9000000000L)
                    push(values, 1.5)
                    push(values, true)
                    push(values, "s")
                    push(values, [1])
                    push(values, {"k": 1})
                    push(values, #{1})
                    push(values, Point { x: 1 })
                    push(values, Circle(2))
                    push(values, Dot)
                    push(values, fn(a: int) -> int { a })
                    push(values, null)
                    mut out = ""
                    for v in values { out = out + typeOf(v) + " " }
                    println(trim(out))
                }
                """;
        String expected = "int long float bool string array map set Point Shape Shape fn null";
        assertEquals(expected, run(source, "TypeOfKinds"));
        assertEquals(expected, interpret(source, "TypeOfKinds"));
    }

    @Test
    void typeOfTellsJsonFieldsApart() throws Exception {
        String source = """
                import "std/json"
                fn main() {
                    match json.parse("{\\"a\\":\\"x\\",\\"b\\":2,\\"c\\":2.5,\\"d\\":false,\\"e\\":null,\\"f\\":[1],\\"g\\":{}}") {
                        Parsed(m) => {
                            mut out = ""
                            for k in ["a", "b", "c", "d", "e", "f", "g", "missing"] { out = out + typeOf(m[k]) + " " }
                            println(trim(out))
                        },
                        Invalid(why) => println(why)
                    }
                }
                """;
        String expected = "string int float bool null array map null";
        assertEquals(expected, run(source, "TypeOfJson"));
        assertEquals(expected, interpret(source, "TypeOfJson"));
    }

    // --- helpers -------------------------------------------------------------

    /** An absolute path, so an import has a directory to resolve against. */
    private static String sourcePath(String name) {
        return java.nio.file.Path.of(name + ".siyo").toAbsolutePath().toString();
    }

    private String interpret(String source, String name) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            Compilation compilation = new Compilation(
                    SyntaxTree.parse(source), new ModuleRegistry(), sourcePath(name));
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
        ModuleRegistry registry = new ModuleRegistry();
        Compilation compilation = new Compilation(tree, registry, sourcePath(className));
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
                for (ModuleSymbol module : registry.getAllModules()) {
                    if (!name.equals(module.getClassName())) continue;
                    byte[] moduleBytes = Compilation.emitModule(module);
                    return defineClass(name, moduleBytes, 0, moduleBytes.length);
                }
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
