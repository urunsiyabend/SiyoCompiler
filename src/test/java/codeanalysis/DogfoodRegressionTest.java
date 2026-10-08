package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // --- P12: a match whose value is discarded ---------------------------------

    @Test
    void aMatchStatementsArmsNeedNotAgreeOnAType() throws Exception {
        String source = """
                type Line = Num(int) | Word(string) | Blank | Note(string)
                fn main() {
                    imut lines = [Num(1), Blank, Word("a"), Note("n"), Num(2), Word("b")]
                    mut total = 0
                    mut words: string[] = []
                    for line in lines {
                        match line {
                            Blank => {},
                            Num(v) => { total = total + v },
                            Word(w) => { push(words, w) },
                            Note(n) => println("note " + n)
                        }
                    }
                    println(toString(total) + " " + toString(words))
                }
                """;
        assertEquals("note n\n3 [a, b]", run(source, "MatchStatementArms"));
        assertEquals("note n\n3 [a, b]", interpret(source, "MatchStatementArms"));
    }

    @Test
    void aMatchStatementMayDiscardValuesOfDifferentTypes() throws Exception {
        String source = """
                fn sideEffect(n: int) -> int { println("ran " + toString(n))
                    n }
                fn main() {
                    imut k = 2
                    match k {
                        1 => "one",
                        2 => sideEffect(2),
                        _ => 3.5
                    }
                    println("done")
                }
                """;
        assertEquals("ran 2\ndone", run(source, "MatchStatementValues"));
        assertEquals("ran 2\ndone", interpret(source, "MatchStatementValues"));
    }

    @Test
    void aMatchWhoseArmsDisagreeIsStillRejectedAsAReturnValue() {
        String message = firstDiagnostic("""
                fn pick(k: int) -> int {
                    match k {
                        1 => 1,
                        _ => println("no")
                    }
                }
                fn main() { println(toString(pick(1))) }
                """);
        assertEquals("All match arms must return the same type; cannot mix void and value arms", message);
    }

    // --- P13: a struct field declared with a generic type ---------------------

    @Test
    void aStructFieldMayHaveAGenericType() throws Exception {
        String source = """
                struct Item { n: int }
                struct Box { items: Array<Item>, counts: Map<string, int>, groups: Map<string, Array<Item>> }
                fn main() {
                    imut b = Box { items: [Item { n: 4 }], counts: {"a": 1}, groups: {} }
                    b.groups["g"] = [Item { n: 7 }]
                    println(toString(b.items[0].n))
                    println(toString(b.counts["a"] + 1))
                    for it in b.items { println(toString(it.n)) }
                    println(toString(b.groups["g"][0].n))
                }
                """;
        assertEquals("4\n2\n4\n7", run(source, "GenericFields"));
        assertEquals("4\n2\n4\n7", interpret(source, "GenericFields"));
    }

    // --- P17/P18: json.stringify writes values by their runtime type ---------

    private static final String STRINGIFY_SOURCE = """
            import "std/json"
            fn main() {
                mut m: map = {}
                m["msg"] = "123"
                m["service"] = "01"
                m["fraction"] = "1.5"
                m["negZero"] = "-0"
                m["exponent"] = "1e3"
                m["word"] = "true"
                m["padded"] = " 42"
                m["int"] = 7
                m["long"] = 9000000000L
                m["float"] = 2.5
                m["bool"] = false
                mut list: object[] = []
                push(list, "5")
                push(list, 5)
                m["list"] = list
                mut ctrl = ""
                for i in range(0, 32) { ctrl = ctrl + chr(i) }
                m["ctrl"] = ctrl + chr(127) + "\\"\\\\/"
                imut text = json.stringify(m)
                println(text)
                match json.parse(text) {
                    Parsed(back) => {
                        mut same = true
                        for k in m { if toString(back[k]) != toString(m[k]) || typeOf(back[k]) != typeOf(m[k]) { same = false } }
                        println("roundtrip " + toString(same))
                    },
                    Invalid(why) => println("roundtrip invalid: " + why)
                }
            }
            """;

    @Test
    void stringifyKeepsNumericLookingStringsAsStrings() throws Exception {
        for (String output : List.of(run(STRINGIFY_SOURCE, "StringifyTypes"), interpret(STRINGIFY_SOURCE, "StringifyTypes"))) {
            String json = output.lines().findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) StrictJson.parse(json);
            assertEquals("123", m.get("msg"));
            assertEquals("01", m.get("service"));
            assertEquals("1.5", m.get("fraction"));
            assertEquals("-0", m.get("negZero"));
            assertEquals("1e3", m.get("exponent"));
            assertEquals("true", m.get("word"));
            assertEquals(" 42", m.get("padded"));
            assertEquals(new BigDecimal("7"), m.get("int"));
            assertEquals(new BigDecimal("9000000000"), m.get("long"));
            assertEquals(new BigDecimal("2.5"), m.get("float"));
            assertEquals(Boolean.FALSE, m.get("bool"));
            assertEquals(List.of("5", new BigDecimal("5")), m.get("list"));
            assertEquals("roundtrip true", output.lines().skip(1).findFirst().orElseThrow());
        }
    }

    @Test
    void stringifyEscapesEveryControlCharacter() throws Exception {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 32; i++) expected.append((char) i);
        expected.append((char) 127).append("\"\\/");
        for (String output : List.of(run(STRINGIFY_SOURCE, "StringifyCtrl"), interpret(STRINGIFY_SOURCE, "StringifyCtrl"))) {
            String json = output.lines().findFirst().orElseThrow();
            for (char c : json.toCharArray()) {
                assertEquals(false, c < 0x20, "raw control character U+" + (int) c + " in " + json);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) StrictJson.parse(json);
            assertEquals(expected.toString(), m.get("ctrl"));
        }
    }

    // --- P19: a field a struct does not have ----------------------------------

    @Test
    void readingAFieldAStructDoesNotHaveIsRejected() {
        assertEquals("Struct 'P' has no field 'nope'", firstDiagnostic("""
                struct P { x: int }
                fn main() {
                    imut p = P { x: 1 }
                    println(toString(p.nope))
                }
                """));
    }

    @Test
    void writingAFieldAStructDoesNotHaveIsRejected() {
        assertEquals("Struct 'P' has no field 'nope'", firstDiagnostic("""
                struct P { x: int }
                fn main() {
                    mut p = P { x: 1 }
                    p.nope = 2
                }
                """));
    }

    @Test
    void aStructLiteralWithAFieldTheStructDoesNotHaveIsRejected() {
        assertEquals("Struct 'P' has no field 'y'", firstDiagnostic("""
                struct P { x: int }
                fn main() { println(toString(P { x: 1, y: 2 }.x)) }
                """));
    }

    @Test
    void knownFieldsStillWorkThroughAnInterface() throws Exception {
        String source = """
                interface Named { fn name() -> string }
                struct Dog { label: string }
                impl Named for Dog { fn name(self) -> string { self.label } }
                fn main() {
                    imut xs: Array<Named> = [Dog { label: "rex" }]
                    for x in xs { println(x.name()) }
                    mut d = Dog { label: "a" }
                    d.label = "b"
                    println(d.label)
                }
                """;
        assertEquals("rex\nb", run(source, "FieldsViaInterface"));
        assertEquals("rex\nb", interpret(source, "FieldsViaInterface"));
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

    private String firstDiagnostic(String source) {
        SyntaxTree tree = SyntaxTree.parse(source);
        if (tree.diagnostics().hasNext()) return tree.diagnostics().get(0).getMessage();
        Compilation compilation = new Compilation(tree, new ModuleRegistry(), sourcePath("Diag"));
        if (compilation.compile("Diag") != null) fail("expected the program to be rejected");
        DiagnosticBox diagnostics = compilation.getGlobalScope().getDiagnostics();
        if (!diagnostics.hasNext()) fail("expected a diagnostic");
        return diagnostics.get(0).getMessage();
    }

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
