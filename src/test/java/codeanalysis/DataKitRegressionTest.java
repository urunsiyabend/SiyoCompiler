package codeanalysis;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gaps found by writing DataKit (projects/datakit), each pinned on both
 * backends. See projects/datakit/PAIN_POINTS.md for the reproducers.
 */
class DataKitRegressionTest {
    private final DogfoodRegressionTest harness = new DogfoodRegressionTest();

    private void assertBoth(String expected, String source, String className) throws Exception {
        assertEquals(expected, harness.run(source, className), "compiled");
        assertEquals(expected, harness.interpret(source, className), "interpreted");
    }

    // --- G1: a generic call's result keeps the type its arguments gave it ------

    private static final String ROWS = """
            struct Row { name: string, dept: string, salary: int }
            fn rows() -> Array<Row> {
                [Row { name: "a", dept: "x", salary: 10 }, Row { name: "b", dept: "y", salary: 5 },
                 Row { name: "c", dept: "x", salary: 7 }]
            }
            """;

    @Test
    void aGenericCallReturningAnArrayOfTKeepsT() throws Exception {
        assertBoth("a\nc\nb", ROWS + """
                fn keep<T>(xs: Array<T>, p: fn(T) -> bool) -> Array<T> {
                    mut out: Array<T> = []
                    for x in xs { if p(x) { push(out, x) } }
                    out
                }
                fn wrap<T>(x: T) -> T[] { [x] }
                fn main() {
                    imut rs = rows()
                    println(keep(rs, fn(r: Row) -> bool { r.salary > 6 })[0].name)
                    println(keep(rs, fn(r: Row) -> bool { r.salary > 6 })[1].name)
                    println(wrap(rs[1])[0].name)
                }
                """, "GenericArrayResult");
    }

    @Test
    void aGenericCallReturningAMapOfArraysOfTKeepsT() throws Exception {
        assertBoth("x 2 17 a\ny 1 5 b", ROWS + """
                fn groupBy<T>(xs: Array<T>, key: fn(T) -> string) -> Map<string, Array<T>> {
                    mut out: Map<string, Array<T>> = {}
                    for x in xs {
                        imut k = key(x)
                        if !out.has(k) { out[k] = [] }
                        push(out[k], x)
                    }
                    out
                }
                fn main() {
                    imut g = groupBy(rows(), fn(r: Row) -> string { r.dept })
                    for k in g {
                        mut total = 0
                        for r in g[k] { total += r.salary }
                        println(k + " " + toString(len(g[k])) + " " + toString(total) + " " + g[k][0].name)
                    }
                }
                """, "GenericMapResult");
    }

    @Test
    void aGenericResultFlowsThroughAVariableALoopAndAnotherCall() throws Exception {
        assertBoth("c\na\n2", ROWS + """
                fn rev<T>(xs: Array<T>) -> Array<T> {
                    mut out: Array<T> = []
                    for mut i = len(xs) - 1 i >= 0 i = i - 1 { push(out, xs[i]) }
                    out
                }
                fn firstOf<T>(xs: Array<T>) -> T { xs[0] }
                fn pairs<A, B>(a: A, b: B) -> Map<string, B> { {"k": b} }
                fn main() {
                    imut r = rev(rows())
                    println(r[0].name)
                    for x in rev(r) { println(x.name)
                                      break }
                    println(toString(pairs("x", firstOf(rev([1, 2])))["k"]))
                }
                """, "GenericResultFlows");
    }

    @Test
    void aGenericResultOverNumbersAndStringsIsErasedAsInZeroNine() throws Exception {
        // Only a struct, sum type or interface type argument is trusted, so a
        // generic array of numbers holds what it is given, as in 0.9.0.
        assertBoth("3\nab\n2\n2.5 two", """
                fn twice<T>(x: T) -> Array<T> { [x, x] }
                fn wrap<T>(x: T) -> Array<T> { [x] }
                fn main() {
                    imut xs = twice(1)
                    imut a: int = xs[0]
                    imut b: int = xs[1]
                    println(toString(a + b + 1))
                    imut ss = twice("a")
                    println(toString(ss[0]) + "b")
                    imut anything = twice(twice(2))
                    println(toString(len(anything)))
                    mut ws = wrap(1)
                    push(ws, "two")
                    ws[0] = 2.5
                    println(toString(ws[0]) + " " + toString(ws[1]))
                }
                """, "GenericErasedScalars");
    }

    // --- G2: generic structs ----------------------------------------------------

    @Test
    void aGenericStructKeepsItsFieldTypes() throws Exception {
        assertBoth("4\nab\nb 5\n2.5", ROWS + """
                struct Box<T> { value: T }
                struct Group<T> { key: string, items: Array<T> }
                struct Pair<A, B> { left: A, right: B }
                fn main() {
                    imut b = Box { value: 3 }
                    println(toString(b.value + 1))
                    imut s = Box { value: "a" }
                    println(s.value + "b")
                    imut g = Group { key: "x", items: rows() }
                    println(g.items[1].name + " " + toString(g.items[1].salary))
                    imut p: Pair<string, float> = Pair { left: "k", right: 2.5 }
                    println(toString(p.right))
                }
                """, "GenericStructFields");
    }

    @Test
    void aGenericStructFlowsThroughParametersLoopsAndIndexing() throws Exception {
        assertBoth("x:a,c\ny:b\nx 17", ROWS + """
                struct Group<T> { key: string, items: Array<T> }
                fn groupBy<T>(xs: Array<T>, key: fn(T) -> string) -> Array<Group<T>> {
                    mut out: Array<Group<T>> = []
                    for x in xs {
                        imut k = key(x)
                        mut found = false
                        for g in out { if g.key == k { push(g.items, x)
                                                       found = true } }
                        if !found { push(out, Group { key: k, items: [x] }) }
                    }
                    out
                }
                fn names(g: Group<Row>) -> string {
                    mut parts: string[] = []
                    for r in g.items { push(parts, r.name) }
                    g.key + ":" + parts[0] + (if len(parts) > 1 { "," + parts[1] } else { "" })
                }
                fn total(g: Group<Row>) -> int {
                    mut t = 0
                    for r in g.items { t += r.salary }
                    t
                }
                fn main() {
                    imut gs = groupBy(rows(), fn(r: Row) -> string { r.dept })
                    for g in gs { println(names(g)) }
                    println(gs[0].key + " " + toString(total(gs[0])))
                }
                """, "GenericStructFlows");
    }

    @Test
    void aGenericStructFieldWidensAndAssigns() throws Exception {
        assertBoth("3.0\n7\n2", """
                struct Box<T> { value: T }
                fn main() {
                    imut f: Box<float> = Box { value: 3 }
                    println(toString(f.value))
                    mut b = Box { value: 1 }
                    b.value = 7
                    println(toString(b.value))
                    mut bs: Array<Box<int>> = []
                    push(bs, Box { value: 1 })
                    push(bs, b)
                    println(toString(len(bs)))
                }
                """, "GenericStructWidens");
    }

    @Test
    void aPlainStructAndAnErasedGenericStructStillWork() throws Exception {
        assertBoth("1\nBox { value: 1 }\n{\"value\":2}", """
                import "std/json"
                struct Box<T> { value: T }
                struct Plain { n: int }
                fn erased(b: Box) -> string { toString(b) }
                fn main() {
                    println(toString(Plain { n: 1 }.n))
                    println(erased(Box { value: 1 }))
                    println(json.stringify(Box { value: 2 }))
                }
                """, "GenericStructErased");
    }

    @Test
    void aMalformedTypeNameIsADiagnosticNotACrash() {
        String message = harness.firstDiagnostic("""
                struct A { x: <T> }
                fn main() { }
                """);
        assertTrue(message.contains("Unexpected token"), message);
    }

    @Test
    void aGenericStructCrossesAModuleBoundary(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pipe.siyo"), """
                pub struct Group<T> { key: string, items: Array<T> }
                pub fn single<T>(key: string, x: T) -> Group<T> { Group { key: key, items: [x] } }
                """);
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "pipe"
                struct Row { name: string }
                fn main() {
                    imut g = pipe.single("k", Row { name: "r" })
                    println(g.key + " " + g.items[0].name)
                    imut h: Group<Row> = Group { key: "h", items: [] }
                    println(toString(len(h.items)))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("k r\n0", result.stdout(), mode + ": " + result.stderr());
            assertEquals(0, result.exitCode(), mode);
        }
    }

    @Test
    void aGenericStructsFieldMayBeTakenIntoAVariable() throws Exception {
        assertBoth("a\nb 5", ROWS + """
                struct Box<T> { value: T }
                struct Group<T> { key: string, items: Array<T> }
                fn main() {
                    imut g = Group { key: "k", items: rows() }
                    imut its = g.items
                    println(its[0].name)
                    imut b = Box { value: its[1] }
                    imut r = b.value
                    println(r.name + " " + toString(r.salary))
                }
                """, "GenericStructFieldVariable");
    }

    // --- G6, G7: another module's generic function and number widening -------

    @Test
    void anImportedGenericFunctionKeepsItsResultTypeAndWidensItsArguments(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("lib.siyo"), """
                pub fn ident<T>(x: T) -> T { x }
                pub fn half(x: float) -> float { x / 2 }
                pub fn keep<T>(xs: Array<T>, p: fn(T) -> bool) -> Array<T> {
                    mut out: Array<T> = []
                    for x in xs { if p(x) { push(out, x) } }
                    out
                }
                """);
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                struct R { n: string }
                fn main() {
                    println(lib.ident(R { n: "a" }).n)
                    println(toString(lib.ident(2) + 1))
                    println(toString(lib.half(5)))
                    imut rs = [R { n: "p" }, R { n: "q" }]
                    println(lib.keep(rs, fn(r: R) -> bool { r.n == "q" })[0].n)
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("a\n3\n2.5\nq", result.stdout(), mode + ": " + result.stderr());
            assertEquals(0, result.exitCode(), mode);
        }
    }

    // --- G8: a sum type's payload keeps its written type --------------------------

    @Test
    void aPayloadKeepsItsWrittenGenericType() throws Exception {
        assertBoth("a\nb\nc\na", """
                struct S { n: string }
                type R = T(Array<S>) | One(S) | Many(Map<string, Array<S>>)
                fn mk(k: int) -> R {
                    if k == 0 { T([S { n: "a" }]) } else if k == 1 { One(S { n: "b" }) }
                    else { Many({"k": [S { n: "c" }]}) }
                }
                fn main() {
                    match mk(0) { T(xs) => println(xs[0].n), One(s) => println(s.n), Many(m) => println("m") }
                    match mk(1) { T(xs) => println("t"), One(s) => println(s.n), Many(m) => println("m") }
                    match mk(2) { T(xs) => println("t"), One(s) => println(s.n), Many(m) => println(m["k"][0].n) }
                    match mk(0) { T(xs) => { for x in xs { println(x.n) } }, One(s) => println(s.n), Many(m) => println("m") }
                }
                """, "PayloadGenericType");
    }

    @Test
    void aGenericSumTypesPayloadIsReadAtItsTypeArgument() throws Exception {
        assertBoth("found b 5\n3\nnone", ROWS + """
                type Option<T> = Some(T) | None
                fn find(rs: Array<Row>, name: string) -> Option<Row> {
                    for r in rs { if r.name == name { return Some(r) } }
                    None
                }
                fn main() {
                    match find(rows(), "b") { Some(r) => println("found " + r.name + " " + toString(r.salary)), None => println("none") }
                    imut n: Option<int> = Some(2)
                    match n { Some(v) => { imut k: int = v
                                           println(toString(k + 1)) }, None => println("none") }
                    match find(rows(), "zz") { Some(r) => println(r.name), None => println("none") }
                }
                """, "GenericPayload");
    }

    // --- G9: a signature may name a type declared further down -----------------

    @Test
    void aSignatureMayNameATypeDeclaredLaterInTheFile() throws Exception {
        assertBoth("2\n1\nk", """
                fn pick(k: int) -> Opt { if k > 0 { Yes(k) } else { No } }
                fn later() -> Later { Later { n: 1 } }
                fn boxes() -> Array<Later> { [later()] }
                fn find(xs: Array<Later>) -> Maybe<Later> { if len(xs) > 0 { Got(xs[0]) } else { Nothing } }
                fn named(s: Shape) -> string { s.name() }
                type Opt = Yes(int) | No
                type Maybe<T> = Got(T) | Nothing
                struct Later { n: int }
                interface Shape { fn name() -> string }
                struct K { id: int }
                impl Shape for K { fn name(self) -> string { "k" } }
                fn main() {
                    match pick(2) { Yes(v) => println(toString(v)), No => println("no") }
                    match find(boxes()) { Got(b) => println(toString(b.n)), Nothing => println("none") }
                    println(named(K { id: 1 }))
                }
                """, "TypesDeclaredLater");
    }

    // --- G10: a match ending a function returning object may mix value types -----

    @Test
    void aMatchEndingAnObjectFunctionMayGiveValuesOfDifferentTypes() throws Exception {
        assertBoth("int 1, string a, null null, float 2.5, bool true", """
                type V = I(int) | S(string) | F(float) | B(bool) | M
                fn conv(v: V) -> object {
                    match v {
                        I(n) => n,
                        S(s) => s,
                        F(x) => x,
                        B(b) => b,
                        M => null
                    }
                }
                fn show(v: V) -> string { typeOf(conv(v)) + " " + toString(conv(v)) }
                fn main() {
                    println(show(I(1)) + ", " + show(S("a")) + ", " + show(M) + ", " + show(F(2.5)) + ", " + show(B(true)))
                }
                """, "ObjectTailMatch");
    }

    @Test
    void armsOfDifferentTypesAreStillRejectedWhereATypeIsDeclared() {
        String asInt = harness.firstDiagnostic("""
                fn pick(k: int) -> int {
                    match k {
                        1 => 1,
                        _ => "no"
                    }
                }
                fn main() { println(toString(pick(2))) }
                """);
        assertEquals("Match arms give different types, int and string; they may differ only where the match ends a function returning object", asInt);
        String asVariable = harness.firstDiagnostic("""
                fn main() {
                    imut x: int = match 2 { 1 => 1, _ => "no" }
                    println(toString(x + 1))
                }
                """);
        assertEquals("Match arms give different types, int and string; they may differ only where the match ends a function returning object", asVariable);
        String voidBesideValue = harness.firstDiagnostic("""
                fn conv(k: int) -> object {
                    match k {
                        1 => 1,
                        _ => println("no")
                    }
                }
                fn main() { println(toString(conv(1))) }
                """);
        assertEquals("All match arms must return the same type; cannot mix void and value arms", voidBesideValue);
    }

    // --- G13: a payload widens as a parameter does ------------------------------

    @Test
    void aPayloadWidensToItsDeclaredNumberType() throws Exception {
        assertBoth("3 3.0 1.0,2.0 9000000001", """
                type V = I(long) | F(float) | A(Array<float>)
                fn show(v: V) -> string {
                    match v {
                        I(n) => toString(n + 1),
                        F(x) => toString(x),
                        A(xs) => toString(xs[0]) + "," + toString(xs[1])
                    }
                }
                fn main() {
                    imut big = I(9000000000)
                    println(show(I(2)) + " " + show(F(3)) + " " + show(A([1, 2])) + " " + show(big))
                }
                """, "PayloadWidens");
    }

    // --- G3, G4: float formatting and recognising a float ----------------------

    @Test
    void formatFloatRoundsHalfUpFromTheDecimalForm() throws Exception {
        assertBoth("0.67 2.68 10.00 -2 0.0 0.00 3 1.50 Infinity -Infinity NaN 123456789.1235 0.10000000000000000",
                """
                import "std/strings"
                fn main() {
                    imut zero = 0.0
                    imut xs = [strings.formatFloat(2.0 / 3.0, 2), strings.formatFloat(2.675, 2),
                               strings.formatFloat(9.995, 2), strings.formatFloat(-1.5, 0),
                               strings.formatFloat(-0.0, 1), strings.formatFloat(-0.001, 2),
                               strings.formatFloat(3, 0), strings.formatFloat(1.5, 2),
                               strings.formatFloat(1.0 / zero, 2), strings.formatFloat(-1.0 / zero, 2),
                               strings.formatFloat(zero / zero, 2), strings.formatFloat(123456789.12345, 4),
                               strings.formatFloat(0.1, 17)]
                    println(strings.join(xs, " "))
                }
                """, "FormatFloat");
    }

    @Test
    void formatFloatRejectsADigitCountOutOfRange() throws Exception {
        assertBoth("formatFloat: digits must be between 0 and 17, got 18\nformatFloat: digits must be between 0 and 17, got -1",
                """
                import "std/strings"
                fn main() {
                    try { println(strings.formatFloat(1.0, 18)) } catch e { println(e) }
                    try { println(strings.formatFloat(1.0, -1)) } catch e { println(e) }
                }
                """, "FormatFloatRange");
    }

    @Test
    void isFloatAcceptsOnlyAFiniteDecimal() throws Exception {
        assertBoth("yes: 1 -2.5 1e3 .5 -0 2. 1E-7 007.5\nno: | 1|1 |NaN|Infinity|-Infinity|1f|1d|0x1p3|1e|1.2.3|+1|-|.|e5|1e999|1,5",
                """
                import "std/strings"
                fn main() {
                    imut good = ["1", "-2.5", "1e3", ".5", "-0", "2.", "1E-7", "007.5"]
                    imut bad = ["", " 1", "1 ", "NaN", "Infinity", "-Infinity", "1f", "1d", "0x1p3", "1e",
                                "1.2.3", "+1", "-", ".", "e5", "1e999", "1,5"]
                    mut yes: string[] = []
                    for g in good { if strings.isFloat(g) { push(yes, g) } }
                    mut no: string[] = []
                    for b in bad { if !strings.isFloat(b) { push(no, b) } }
                    println("yes: " + strings.join(yes, " "))
                    println("no: " + strings.join(no, "|"))
                }
                """, "IsFloat");
    }

    // --- G5: std/csv -----------------------------------------------------------

    private static final String SHOW_CSV = """
            import "std/csv"
            import "std/strings"
            fn show(text: string) -> string {
                match csv.parse(text) {
                    Table(rows) => {
                        mut parts: string[] = []
                        for r in rows {
                            imut row = r.cells
                            mut cells: string[] = []
                            for cell in row { push(cells, "[" + replace(replace(cell, "\\r", "CR"), "\\n", "LF") + "]") }
                            push(parts, toString(r.line) + "/" + toString(len(row)) + ":" + strings.join(cells, ""))
                        }
                        "rows " + toString(len(rows)) + " " + strings.join(parts, " ")
                    },
                    BadCsv(why) => "bad " + why
                }
            }
            """;

    @Test
    void csvReadsQuotedFieldsLineEndingsAndAByteOrderMark() throws Exception {
        assertBoth(String.join("\n",
                        "rows 2 1/2:[a][b] 2/2:[c][d]",
                        "rows 2 1/2:[a][b] 2/2:[c][d]",
                        "rows 2 1/3:[a,1][say \"hi\"][] 2/1:[x]",
                        "rows 2 1/2:[twoCRLFlines][z] 3/1:[]",
                        "rows 1 1/2:[a][b]",
                        "rows 0 ",
                        "rows 0 ",
                        "rows 1 1/3:[a][][]",
                        "rows 3 1/1:[a] 2/1:[] 3/1:[b]",
                        "rows 2 1/1:[a] 2/1:[b]",
                        "rows 1 1/1:[\"]"),
                SHOW_CSV + """
                fn main() {
                    println(show("a,b\\nc,d\\n"))
                    println(show("a,b\\r\\nc,d"))
                    println(show("\\"a,1\\",\\"say \\"\\"hi\\"\\"\\",\\"\\"\\nx"))
                    println(show("\\"two\\r\\nlines\\",z\\n\\"\\"\\n"))
                    println(show(chr(65279) + "a,b"))
                    println(show(""))
                    println(show(chr(65279)))
                    println(show("a,,"))
                    println(show("a\\n\\nb"))
                    println(show("a\\rb\\r"))
                    println(show("\\"\\"\\"\\""))
                }
                """, "CsvReads");
    }

    @Test
    void csvReportsMalformedQuotingWithItsLine() throws Exception {
        assertBoth(String.join("\n",
                        "bad line 1: text after the closing quote of a field",
                        "bad line 2: a quoted field is not closed",
                        "bad line 3: a quote inside an unquoted field",
                        "bad line 3: text after the closing quote of a field"),
                SHOW_CSV + """
                fn main() {
                    println(show("a,\\"b\\"c"))
                    println(show("a\\n\\"open,\\nstill open"))
                    println(show("a\\nb\\nc\\"d"))
                    println(show("\\"multi\\nline\\nfield\\"x"))
                }
                """, "CsvMalformed");
    }

    @Test
    void csvFormatRowQuotesWhatNeedsItAndRoundTrips() throws Exception {
        assertBoth(String.join("\n",
                        "plain,\"a,b\",\"say \"\"x\"\"\",\"two\nlines\",\" pad\",,1.5",
                        "rows 1 1/7:[plain][a,b][say \"x\"][twoLFlines][ pad][][1.5]"),
                SHOW_CSV + """
                fn main() {
                    imut line = csv.formatRow(["plain", "a,b", "say \\"x\\"", "two\\nlines", " pad", "", "1.5"])
                    println(line)
                    println(show(line))
                }
                """, "CsvFormat");
    }

    // --- G14: io.readText keeps a file as written ---------------------------------

    @Test
    void readTextKeepsLineEndingsAndTheFinalNewline(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("d.csv");
        Files.write(data, "a,\"x\r\ny\"\r\nb,\"p\rq\"\n\u00e9,".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(dir.resolve("bad.txt"), new byte[]{'o', 'k', (byte) 0xff});
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "std/io"
                fn codes(s: string) -> string {
                    mut out = ""
                    for mut i = 0 i < len(s) i = i + 1 { out = out + toString(ord(substring(s, i, i + 1))) + " " }
                    out
                }
                fn main() {
                    println(codes(io.readText("d.csv")))
                    println(codes(io.readText("bad.txt")))
                    println(toString(len(io.readFile("d.csv"))))
                    try { io.readText("missing.txt") } catch e { println(e) }
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("97 44 34 120 13 10 121 34 13 10 98 44 34 112 13 113 34 10 233 44 \n111 107 65533 \n18\ncannot read missing.txt",
                    result.stdout(), mode + ": " + result.stderr());
        }
    }

    // --- Found in review: what the type substitutions must not change ------------

    @Test
    void aNumberPayloadOfAGenericSumTypeReadsAsItDidInZeroNine() throws Exception {
        // A number or bool payload is left erased, so it may hold null; the
        // value is what was given, as in 0.9.0.
        assertBoth("0\n2\nSome(5)", """
                type Option<T> = Some(T) | None
                fn main() {
                    imut n: Option<float> = Some(2)
                    match n { Some(v) => println(toString(v / 4)), None => println("none") }
                    match n { Some(v) => println(toString(v)), None => println("none") }
                    imut m: Option<float> = Some(5)
                    println(toString(m))
                }
                """, "GenericNumberPayload");
    }

    @Test
    void aGenericValueOfTheWrongKindFailsTheSameWayOnBothBackends() throws Exception {
        // A number payload stays erased, as in 0.9.0, so it prints as it is held;
        // a generic struct's field fails when read, on both backends.
        assertBoth("a\nfield failed", """
                type Option<T> = Some(T) | None
                struct Box<T> { value: T }
                fn find(xs: Array<string>) -> Option<int> {
                    for x in xs { return Some(x) }
                    None
                }
                fn set(b: Box) { b.value = "s" }
                fn main() {
                    match find(["a"]) { Some(i) => println(toString(i)), None => println("none") }
                    imut b = Box { value: 1 }
                    set(b)
                    try { println(toString(b.value + 1)) } catch e { println("field failed") }
                }
                """, "GenericWrongKind");
    }

    @Test
    void anArrayDeclaredByAnAnnotationTakesOnlyItsKind() {
        String message = harness.firstDiagnostic("""
                fn main() {
                    mut xs: Array<int> = [1]
                    push(xs, "two")
                }
                """);
        assertEquals("push: the array holds int, not string", message);
    }

    @Test
    void whatZeroNineAcceptedWithoutDeclaredTypesIsUnchanged() throws Exception {
        // A literal-inferred array, a plain array payload and a field taken into
        // a variable read as they did in 0.9.0.
        assertBoth("0 2\na\ntwo", """
                type V = F(float[]) | I(int[]) | N
                struct S { items: int[] }
                fn main() {
                    imut xs = [1, 2, 3]
                    match F(xs) { F(ys) => println(toString(ys[0] / 2) + " " + toString(ys[1])), I(zs) => println("i"), N => println("n") }
                    match I(["a"]) { F(ys) => println("f"), I(zs) => println(toString(zs[0])), N => println("n") }
                    imut s = S { items: [1] }
                    imut items = s.items
                    push(items, "two")
                    println(toString(items[1]))
                }
                """, "ZeroNineUnchanged");
    }

    @Test
    void aNumberReturnedFromAnObjectFunctionIsBoxed() throws Exception {
        assertBoth("3 neg 4", """
                type V = I(int) | N
                fn r(n: int) -> object {
                    if n > 0 { return n }
                    return "neg"
                }
                fn viaArm(v: V) -> object {
                    match v {
                        I(n) => { return n + 1 }
                        _ => "x"
                    }
                }
                fn main() { println(toString(r(3)) + " " + toString(r(-1)) + " " + toString(viaArm(I(3)))) }
                """, "ObjectReturnBoxed");
    }

    @Test
    void aMissingValueThroughAGenericCallOrPayloadReadsAsNullOnBothBackends() throws Exception {
        // Found in the second review: V bound through a Map and T through a
        // payload were unboxed compiled, so a missing value crashed.
        assertBoth("a=1 zz=null\ngot null\n2.5 1", """
                type Option<T> = Some(T) | None
                fn get<K, V>(m: Map<K, V>, k: K) -> V { m[k] }
                fn lookup(m: Map<string, object>, k: string) -> Option<int> { Some(m[k]) }
                fn both<T>(a: T, b: T) -> Array<T> { [a, b] }
                fn main() {
                    imut m: Map<string, int> = {"a": 1}
                    println("a=" + toString(get(m, "a")) + " zz=" + toString(get(m, "zz")))
                    imut o: Map<string, object> = {"a": 1}
                    match lookup(o, "zz") { Some(v) => println("got " + toString(v)), None => println("none") }
                    mut xs = both(1, 2.5)
                    push(xs, 3.5)
                    println(toString(xs[1]) + " " + toString(xs[0]))
                }
                """, "GenericMissingValues");
    }

    // --- Third review: every reproducer reads as 0.9.0 did, or fails alike -------

    @Test
    void anErasedOrNullArgumentErasesTheTypeParameter(@TempDir Path dir) throws Exception {
        String program = """
                fn both<T>(a: T, b: T) -> Array<T> { [a, b] }
                fn get<K, V>(m: Map<K, V>, k: K) -> Array<V> { [m[k]] }
                fn inMap<K, V>(m: Map<K, V>, k: K) -> Map<string, V> { {"key": m[k]} }
                fn first<T>(xs: Array<T>) -> T { xs[0] }
                fn main() {
                    imut o: object = 1
                    println(toString(both(o, 2.5)[0]) + " " + toString(both(1, null)[1]))
                    imut m: Map<string, int> = {"a": 1}
                    println(toString(get(m, "absent")[0]) + " " + toString(inMap(m, "absent")["key"]))
                    imut source = [1, 2]
                    imut fs: Array<float> = source
                    println(toString(first(fs)) + " " + toString(fs[0]))
                }
                """;
        assertBoth("1 null\nnull null\n1 1", program, "ErasedArguments");
        Files.writeString(dir.resolve("lib.siyo"), """
                pub fn both<T>(a: T, b: T) -> Array<T> { [a, b] }
                pub fn get<K, V>(m: Map<K, V>, k: K) -> Array<V> { [m[k]] }
                """);
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                fn main() {
                    imut o: object = 1
                    imut m: Map<string, int> = {"a": 1}
                    println(toString(lib.both(o, 2.5)[0]) + " " + toString(lib.get(m, "absent")[0]))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("1 null", result.stdout(), mode + ": " + result.stderr());
        }
    }

    @Test
    void aGenericStructFieldOfTheWrongKindOrNullFailsAlikeOnBothBackends() throws Exception {
        assertBoth("failed failed failed failed failed 1.0 3.0 failed", """
                struct Box<T> { value: T }
                fn setNull(b: Box) { b.value = null }
                fn setText(b: Box) { b.value = "abc" }
                fn main() {
                    imut a: Box<int> = Box { value: 1 }
                    setNull(a)
                    mut out = ""
                    try { out = out + toString(a.value + 1) } catch e { out = out + "failed" }
                    mut r = Box { value: 1 }
                    r = Box { value: null }
                    try { out = out + " " + toString(r.value + 1) } catch e { out = out + " failed" }
                    imut c: Box<int> = Box { value: 1 }
                    imut alias: Box<object> = c
                    alias.value = null
                    try { out = out + " " + toString(c.value + 1) } catch e { out = out + " failed" }
                    imut d: Box<string> = Box { value: "s" }
                    setText(d)
                    imut e: Box<int> = Box { value: 1 }
                    setText(e)
                    try { out = out + " " + toString(e.value + 1) } catch x { out = out + " failed" }
                    imut arrays: Box<Array<int>> = Box { value: [1] }
                    setText(arrays)
                    try { out = out + " " + toString(len(arrays.value)) } catch x { out = out + " failed" }
                    imut floats: Box<Array<float>> = Box { value: [1, 2] }
                    out = out + " " + toString(floats.value[0])
                    floats.value = [3, 4]
                    out = out + " " + toString(floats.value[0])
                    imut f2: Box<Array<int>> = Box { value: [1] }
                    f2.value = [2.5]
                    try { out = out + " " + toString(f2.value[0]) } catch x { out = out + " failed" }
                    println(out)
                }
                """, "GenericFieldChecks");
    }

    @Test
    void aGenericPayloadOfCollectionsReadsAsInZeroNineAndAStructOneIsChecked() throws Exception {
        assertBoth("3", """
                type Opt<T> = Some(T) | None
                fn main() {
                    imut o: Opt<Array<int>> = Some("abc")
                    match o { Some(xs) => println(toString(len(xs))), None => println("none") }
                }
                """, "PayloadCollection");
        String message = harness.firstDiagnostic("""
                struct R { n: int }
                type Opt<T> = Some(T) | None
                fn main() {
                    imut o: Opt<R> = Some("abc")
                    match o { Some(x) => println(toString(x)), None => println("none") }
                }
                """);
        assertEquals("Cannot convert type <class java.lang.String> to <class codeanalysis.SiyoStruct>", message);
    }

    @Test
    void isFloatIsLinearOnALongRunOfDigits() throws Exception {
        String digits = "1".repeat(200_000);
        long start = System.nanoTime();
        assertEquals(false, SiyoRuntime.isFloat(digits + "x"));
        assertEquals(true, SiyoRuntime.isFloat("1".repeat(300) + ".5"));
        assertEquals(false, SiyoRuntime.isFloat(digits + ".5"));  // past the largest float
        assertEquals(false, SiyoRuntime.isFloat(digits + "." + digits + "e"));
        long millis = (System.nanoTime() - start) / 1_000_000;
        // A backtracking pattern took about 5 s for 32,000 digits.
        org.junit.jupiter.api.Assertions.assertTrue(millis < 2000, millis + " ms");
    }

    @Test
    void aLoneEmptyCellRoundTripsAndABlankLineIsMarked() throws Exception {
        assertBoth("\"\"\n1 false\n2/1 true 3/1 false", SHOW_CSV + """
                fn describe(r: CsvRow) -> string { toString(r.line) + "/" + toString(len(r.cells)) + " " + toString(r.blank) }
                fn main() {
                    imut line = csv.formatRow([""])
                    println(line)
                    match csv.parse(line) {
                        Table(rows) => println(toString(len(rows)) + " " + toString(rows[0].blank)),
                        BadCsv(why) => println(why)
                    }
                    match csv.parse("v\\n\\n\\"\\"\\n") {
                        Table(rows) => println(describe(rows[1]) + " " + describe(rows[2])),
                        BadCsv(why) => println(why)
                    }
                }
                """, "CsvBlank");
    }

    @Test
    void writeNewNeverOpensWhatIsAlreadyThere(@TempDir Path dir) throws Exception {
        Path sentinel = dir.resolve("sentinel.txt");
        Files.writeString(sentinel, "KEEP");
        Files.writeString(dir.resolve("exists.txt"), "OLD");
        Files.createSymbolicLink(dir.resolve("link.txt"), sentinel);
        Files.createSymbolicLink(dir.resolve("dangling.txt"), dir.resolve("nowhere.txt"));
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "std/io"
                fn attempt(path: string) -> string {
                    try { io.writeNew(path, "NEW")
                          return "wrote" } catch e { return toString(e) }
                }
                fn main() {
                    println(attempt("exists.txt"))
                    println(attempt("link.txt"))
                    println(attempt("dangling.txt"))
                    println(attempt("fresh.txt"))
                    println(attempt("fresh.txt"))
                    println(attempt("missing/dir.txt"))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            Files.deleteIfExists(dir.resolve("fresh.txt"));
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("exists: exists.txt\nexists: link.txt\nexists: dangling.txt\nwrote\nexists: fresh.txt\ncannot write: missing/dir.txt",
                    result.stdout(), mode + ": " + result.stderr());
            assertEquals("KEEP", Files.readString(sentinel));
            assertEquals("OLD", Files.readString(dir.resolve("exists.txt")));
            assertEquals(false, Files.exists(dir.resolve("nowhere.txt")));
            assertEquals("NEW", Files.readString(dir.resolve("fresh.txt")));
        }
    }

    @Test
    void aStructNameCarriesIdentityWhileTheValueStaysAsZeroNineHadIt() throws Exception {
        // Fourth review: a generic struct's numbers read at their type argument,
        // a struct array from a generic call holds whatever was pushed (the
        // member read casts), map and array fields keep their types, and an
        // annotated payload from an erased source prints as in 0.9.0.
        assertBoth("3.0 18000000000\n1.0 3.0 \nR { n: 1 }|5|1\ntext\n1\n1.5\n5", """
struct R { n: int }
struct Box<T> { value: T }
type Opt<T> = Some(T) | None
fn mk<T>(x: T) -> Box<T> { Box { value: x } }
fn wrap<T>(x: T) -> Array<T> { [x] }
fn opt(x: object) -> Opt { Some(x) }
fn setAll(xs: Array<object>) { push(xs, "text") }
fn main() {
    println(toString(mk(1.5).value * 2) + " " + toString(mk(9000000000).value * 2))
    for b in [mk(0.5), mk(1.5)] { print(toString(b.value * 2) + " ") }
    println("")
    mut rs = wrap(R { n: 1 })
    push(rs, 5)
    for r in rs { print(toString(r) + "|") }
    println(toString(rs[0].n))
    mut ws = wrap("text")
    setAll(ws)
    println(toString(ws[1]))
    imut m: Box<Map<string, int>> = Box { value: {"a": 1} }
    println(toString(m.value["a"]))
    imut f: Box<Array<float>> = Box { value: [1, 2] }
    f.value = [3, 4]
    println(toString(f.value[0] / 2))
    imut o: Opt<R> = opt(5)
    match o { Some(x) => println(toString(x)), None => println("none") }
}
                """, "NameCarriesIdentity");
    }

    @Test
    void aListOfGenericStructsKeepsTheirTypeArgument() throws Exception {
        // Fifth review: a list literal bound to a variable carried no name, so
        // b.value was erased and erased arithmetic split by backend. Number
        // kinds join at the wider one, which a checked read widens to.
        assertBoth("3.0 5.0\n1.0 3.0\n1.25 0.5\n2 18000000000\nfailed", """
                struct Box<T> { value: T }
                fn mk<T>(x: T) -> Box<T> { Box { value: x } }
                fn main() {
                    imut bs = [Box { value: 1.5 }, Box { value: 2.5 }]
                    println(toString(bs[0].value * 2) + " " + toString(bs[1].value * 2))
                    imut ms = [mk(0.5), mk(1.5)]
                    mut line = ""
                    for b in ms { line = line + toString(b.value * 2) + " " }
                    println(trim(line))
                    imut mixed = [Box { value: 2.5 }, Box { value: 1 }]
                    println(toString(mixed[0].value / 2) + " " + toString(mixed[1].value / 2))
                    imut longs = [Box { value: 1 }, Box { value: 9000000000 }]
                    println(toString(longs[0].value * 2) + " " + toString(longs[1].value * 2))
                    mut ints = [Box { value: 1 }]
                    push(ints, Box { value: "x" })
                    try { println(toString(ints[1].value + 1)) } catch e { println("failed") }
                }
                """, "GenericStructList");
    }

    // --- Re-review at 328a5c7: knowable generic struct names, declared shapes -----

    @Test
    void aGenericStructsTypeArgumentSurvivesAnAliasFilterAndFieldElement() throws Exception {
        assertBoth("3.0 3.0 3.0 3.0", """
                struct Box<T> { value: T }
                fn first<T>(xs: Array<T>) -> T { xs[0] }
                fn main() {
                    imut b = Box { value: 1.5 }
                    imut c = b
                    imut bs = [Box { value: 1.5 }]
                    imut cs = filter(bs, fn(x: Box<float>) -> bool { true })
                    imut held: Box<Array<float>> = Box { value: [1.5] }
                    imut alias = cs
                    println(toString(c.value * 2) + " " + toString(cs[0].value * 2) + " "
                        + toString(first(held.value) * 2) + " " + toString(alias[0].value * 2))
                }
                """, "GenericAlias");
    }

    @Test
    void anImportedGenericStructKeepsItsArgumentThroughAnAlias(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("lib.siyo"), """
                pub struct Box<T> { value: T }
                pub fn mk<T>(x: T) -> Box<T> { Box { value: x } }
                """);
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                fn main() {
                    imut b = lib.mk(1.5)
                    imut c = b
                    println(toString(c.value * 2))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("3.0", result.stdout(), mode + ": " + result.stderr());
        }
    }

    @Test
    void aValueOfTheWrongShapeFailsAtADeclaredReturnOnBothBackends() throws Exception {
        assertBoth("failed failed failed", """
                struct Box<T> { value: T }
                struct R { n: int }
                fn wrong<T>(x: T) -> Box<T> {
                    imut b: object = "abc"
                    b
                }
                fn same<T>(x: T) -> T {
                    imut b: object = "abc"
                    b
                }
                fn count() -> int {
                    imut o: object = "abc"
                    return o
                }
                fn main() {
                    mut out = ""
                    try { out = out + toString(len(wrong(1))) } catch e { out = out + "failed" }
                    imut rs: Array<R> = [R { n: 1 }]
                    try { out = out + " " + toString(len(same(rs))) } catch e { out = out + " failed" }
                    try { out = out + " " + toString(count()) } catch e { out = out + " failed" }
                    println(out)
                }
                """, "DeclaredReturnShape");
    }

    @Test
    void aGenericStructsArgumentReachesABareResultAndAFieldLocal() throws Exception {
        assertBoth("3.0 s! 3.0 5.0 3.0", """
                struct Box<T> { value: T }
                fn g<T>(b: Box<T>) -> T {
                    imut c = b
                    c.value
                }
                fn main() {
                    imut b = Box { value: [1.5, 2.5] }
                    imut c = b
                    imut v = c.value
                    imut held: Box<Map<string, float>> = Box { value: {"a": 1.5} }
                    imut m = held.value
                    println(toString(g(Box { value: 1.5 }) * 2) + " " + g(Box { value: "s" }) + "! "
                        + toString(v[0] * 2) + " " + toString(c.value[1] * 2) + " " + toString(m["a"] * 2))
                }
                """, "GenericToBareAndLocal");
    }

    @Test
    void aLambdasDeclaredReturnIsCheckedOnBothBackendsButNotAnActorsMethod() throws Exception {
        assertBoth("failed failed", """
                struct Box<T> { value: T }
                fn main() {
                    imut h = fn(o: object) -> Box<float> { o }
                    imut s = fn(o: object) -> string { o }
                    mut out = ""
                    try { out = out + toString(len(toString(h("abc")))) } catch e { out = out + "failed" }
                    try { out = out + " " + s(3) } catch e { out = out + " failed" }
                    println(out)
                }
                """, "LambdaReturnShape");
    }

    @Test
    void aMapLiteralGivesAGenericStructItsMapType() throws Exception {
        assertBoth("3.0 3.0 3.0 2.0", """
                struct Box<T> { value: T }
                fn mk<T>(x: T) -> Box<T> { Box { value: x } }
                fn main() {
                    imut a = Box { value: {"x": 1.5} }
                    imut b = mk({"x": 1.5})
                    imut m = a.value
                    imut mixed = Box { value: {"i": 1, "f": 2.5} }
                    println(toString(a.value["x"] * 2) + " " + toString(b.value["x"] * 2) + " "
                        + toString(m["x"] * 2) + " " + toString(mixed.value["i"] * 2))
                }
                """, "GenericMapLiteral");
    }

    @Test
    void aBoxGetterReturnsItsArgumentsElementsMapsAndScalars(@TempDir Path dir) throws Exception {
        // Final gate: g(b: Box<T>) -> T given a Box<Array<float>> lost the
        // element type, so v[0] * 2 raised compiled and gave 3.0 interpreted.
        assertBoth("3.0 5.0 5.0\n3.0 5.0 \n3.0\n3.0 3.0\n4\n1", """
struct Box<T>{value:T}
struct R { n: int }
fn g<T>(b: Box<T>) -> T { b.value }
fn keep<T>(xs: Array<T>) -> Array<T> { xs }
fn main() {
    imut a: Box<Array<float>> = Box { value: [1.5, 2.5] }
    imut v = g(a)
    imut w = v
    println(toString(v[0] * 2) + " " + toString(w[1] * 2) + " " + toString(g(a)[1] * 2))
    for x in g(a) { print(toString(x * 2) + " ") }
    println("")
    imut s = g(Box { value: 1.5 })
    println(toString(s * 2))
    imut m: Box<Map<string, float>> = Box { value: {"k": 1.5} }
    imut mv = g(m)
    println(toString(mv["k"] * 2) + " " + toString(g(m)["k"] * 2))
    imut rs = g(Box { value: [R { n: 4 }] })
    println(toString(rs[0].n))
    println(toString(len(keep([1.5]))))
}
                """, "BoxGetters");
        assertBoth("3.0", """
                struct Box<T> { value: T }
                fn h<T>(b: Box<Array<T>>) -> Array<T> { b.value }
                fn main() {
                    imut b: Box<Array<float>> = Box { value: [1.5] }
                    imut v = h(b)
                    println(toString(v[0] * 2))
                }
                """, "BoxArrayGetter");
        Files.writeString(dir.resolve("lib.siyo"), "pub struct Box<T> { value: T }\npub fn g<T>(b: Box<T>) -> T { b.value }\n");
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                fn main() {
                    imut a: Box<Array<float>> = Box { value: [1.5] }
                    imut v = lib.g(a)
                    imut w = v
                    println(toString(w[0] * 2))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("3.0", result.stdout(), mode + ": " + result.stderr());
        }
    }

    @Test
    void aBoxMadeByALambdaKeepsItsArgumentForAGetter() throws Exception {
        assertBoth("3.0 3.0 3.0", """
                struct Box<T> { value: T }
                fn g<T>(b: Box<T>) -> T { b.value }
                fn main() {
                    imut ha = fn(x: float) -> Box<Array<float>> { Box { value: [x] } }
                    imut b = ha(1.5)
                    imut hm = fn(x: float) -> Box<Map<string, float>> { Box { value: {"k": x} } }
                    println(toString(g(b)[0] * 2) + " " + toString(g(ha(1.5))[0] * 2) + " " + toString(g(hm(1.5))["k"] * 2))
                }
                """, "LambdaBoxGetter");
    }

    // --- Gate at 4b358e3: one provenance rule, checked as a matrix ----------------

    /** A container shape: its type, a literal of it, a read reaching a 1.5, and how a loop item is read. */
    private record Shape(String name, String type, String literal, String read, String itemRead) {
    }

    private static final List<Shape> SHAPES = List.of(
            new Shape("array", "Array<float>", "[1.5]", "[0]", ""),
            new Shape("map", "Map<string,float>", "{\"k\": 1.5}", "[\"k\"]", null),
            new Shape("nested", "Array<Array<float>>", "[[1.5]]", "[0][0]", "[0]"),
            new Shape("maparr", "Map<string,Array<float>>", "{\"k\": [1.5]}", "[\"k\"][0]", null),
            new Shape("arrmap", "Array<Map<string,float>>", "[{\"k\": 1.5}]", "[0][\"k\"]", "[\"k\"]"));

    private static final List<String> SOURCES = List.of("field", "getter", "annotated", "lambda", "imported");

    /** A program reading one source and shape directly, through aliases, an annotation, filter and for. */
    private static String provenanceProgram(String source, Shape shape) {
        StringBuilder body = new StringBuilder();
        String value = switch (source) {
            case "field" -> "Box { value: " + shape.literal() + " }.value";
            case "getter" -> "g(Box { value: " + shape.literal() + " })";
            case "annotated" -> "g(boxed)";
            case "lambda" -> "g(mk(" + shape.literal() + "))";
            default -> "lib.g(Box { value: " + shape.literal() + " })";
        };
        if (source.equals("annotated")) body.append("    imut boxed: Box<").append(shape.type()).append("> = Box { value: ").append(shape.literal()).append(" }\n");
        if (source.equals("lambda")) body.append("    imut mk = fn(v: object) -> Box<").append(shape.type()).append("> { Box { value: v } }\n");
        body.append("    imut a = ").append(value).append("\n    imut b = a\n    imut c = b\n");
        body.append("    imut d: ").append(shape.type()).append(" = a\n    imut e = d\n");
        int reads = 0;
        for (String name : List.of("a", "b", "c", "e")) {
            body.append("    println(toString(").append(name).append(shape.read()).append(" * 2))\n");
            reads++;
        }
        if (shape.itemRead() != null) {
            body.append("    imut f = filter(a, fn(x: object) -> bool { true })\n    imut h = f\n");
            body.append("    println(toString(f").append(shape.read()).append(" * 2))\n");
            body.append("    println(toString(h").append(shape.read()).append(" * 2))\n");
            body.append("    for x in b { println(toString(x").append(shape.itemRead()).append(" * 2)) }\n");
            reads += 3;
        }
        String header = source.equals("imported") ? "import \"lib\"\n"
                : "struct Box<T> { value: T }\nfn g<T>(b: Box<T>) -> T { b.value }\n";
        return header + "fn main() {\n" + body + "}\n" + "// reads: " + reads + "\n";
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> aGenericStructsArgumentReachesEveryReadOfEveryShape(@TempDir Path dir) {
        List<DynamicTest> tests = new java.util.ArrayList<>();
        for (String source : SOURCES) {
            for (Shape shape : SHAPES) {
                tests.add(DynamicTest.dynamicTest(source + " " + shape.name(), () -> {
                    String program = provenanceProgram(source, shape);
                    int reads = Integer.parseInt(program.substring(program.lastIndexOf(": ") + 2).trim());
                    String expected = String.join("\n", java.util.Collections.nCopies(reads, "3.0"));
                    if (!source.equals("imported")) {
                        assertBoth(expected, program, "Provenance_" + source + "_" + shape.name());
                        return;
                    }
                    Path project = Files.createDirectories(dir.resolve(shape.name()));
                    Files.writeString(project.resolve("lib.siyo"),
                            "pub struct Box<T> { value: T }\npub fn g<T>(b: Box<T>) -> T { b.value }\n");
                    Path main = project.resolve("main.siyo");
                    Files.writeString(main, program);
                    for (String mode : List.of("run", "interpret")) {
                        CliParityTest.Result result = CliParityTest.siyoc(project, mode, main.toString());
                        assertEquals(expected, result.stdout(), mode + ": " + result.stderr());
                    }
                }));
            }
        }
        return tests.stream();
    }

    @Test
    void anOrdinaryGenericOverNumbersStaysErasedAsInZeroNine() throws Exception {
        // The provenance rule must not reach a plain generic result: these hold
        // what is pushed into them, as 0.9.0's did.
        assertBoth("two two two", """
                fn keep<T>(xs: Array<T>) -> Array<T> { xs }
                fn get<K, V>(m: Map<K, V>, k: K) -> Array<V> { [m[k]] }
                fn main() {
                    mut a = keep([1])
                    push(a, "two")
                    imut b = a
                    mut c = filter(keep([1]), fn(x: object) -> bool { true })
                    push(c, "two")
                    imut m: Map<string, int> = {"k": 1}
                    mut d = get(m, "k")
                    push(d, "two")
                    imut e = d
                    println(toString(b[1]) + " " + toString(c[1]) + " " + toString(e[1]))
                }
                """, "OrdinaryErased");
    }

    // --- Gate at 897ea1c: provenance carried by every transport -------------------

    private static final List<String> TRANSPORTS = List.of("exported", "bridge", "bridgeLoop", "pop", "mapKey", "ifMatch");

    /**
     * A program moving a struct-derived value of one shape through one transport
     * and reading it through two aliases. Null when the transport does not apply:
     * a map key is only tried with array shapes.
     */
    private static String transportProgram(String transport, Shape shape) {
        String header = "struct Box<T> { value: T }\nfn g<T>(b: Box<T>) -> T { b.value }\n";
        String reads = "    imut b = a\n    imut c = b\n    println(toString(a" + shape.read() + " * 2))\n"
                + "    println(toString(c" + shape.read() + " * 2))\n";
        return switch (transport) {
            case "exported" -> "import \"lib\"\nfn main() {\n    imut a = lib.values\n" + reads + "}\n";
            case "bridge" -> header + "struct Row { items: " + shape.type() + " }\nfn main() {\n"
                    + "    imut row = g(Box { value: Row { items: " + shape.literal() + " } })\n    imut a = row.items\n"
                    + reads + "}\n";
            case "bridgeLoop" -> header + "struct Row { items: " + shape.type() + " }\nfn main() {\n"
                    + "    imut rows = g(Box { value: [Row { items: " + shape.literal() + " }] })\n"
                    + "    for row in rows {\n    imut a = row.items\n" + reads + "    }\n}\n";
            case "pop" -> header + "fn main() {\n    mut all = g(Box { value: [" + shape.literal() + "] })\n"
                    + "    imut a = pop(all)\n" + reads + "}\n";
            case "ifMatch" -> header + "fn main() {\n    imut v = g(Box { value: " + shape.literal() + " })\n"
                    + "    imut w = if len(\"x\") > 0 { v } else { v }\n"
                    + "    imut a = match 1 { 1 => w, _ => v }\n" + reads + "}\n";
            default -> shape.itemRead() == null ? null : header + "fn main() {\n"
                    + "    imut keyed = g(Box { value: {" + shape.literal() + ": 1} })\n"
                    + "    for a in keyed {\n" + reads + "    }\n}\n";
        };
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> aStructDerivedValueSurvivesEveryTransport(@TempDir Path dir) {
        List<DynamicTest> tests = new java.util.ArrayList<>();
        for (String transport : TRANSPORTS) {
            for (Shape shape : SHAPES) {
                String program = transportProgram(transport, shape);
                if (program == null) continue;
                tests.add(DynamicTest.dynamicTest(transport + " " + shape.name(), () -> {
                    if (!transport.equals("exported")) {
                        assertBoth("3.0\n3.0", program, "Transport_" + transport + "_" + shape.name());
                        return;
                    }
                    Path project = Files.createDirectories(dir.resolve(shape.name()));
                    Files.writeString(project.resolve("lib.siyo"), "struct Box<T> { value: T }\n"
                            + "fn g<T>(b: Box<T>) -> T { b.value }\n"
                            + "pub imut values = g(Box { value: " + shape.literal() + " })\n");
                    Path main = project.resolve("main.siyo");
                    Files.writeString(main, program);
                    for (String mode : List.of("run", "interpret")) {
                        CliParityTest.Result result = CliParityTest.siyoc(project, mode, main.toString());
                        assertEquals("3.0\n3.0", result.stdout(), mode + ": " + result.stderr());
                    }
                }));
            }
        }
        return tests.stream();
    }

    @Test
    void anOrdinaryExportedOrPoppedValueStaysAsInZeroNine(@TempDir Path dir) throws Exception {
        // Transport only carries what a generic struct gave: a typed module map
        // and a popped plain array read as 0.9.0 read them.
        Files.writeString(dir.resolve("lib.siyo"), "pub imut counts: Map<string, int> = {\"a\": 1}\npub fn one() -> int { 1 }\n");
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                fn main() {
                    imut m = lib.counts
                    println(toString(m["absent"]))
                    mut all = [[1]]
                    mut row = pop(all)
                    push(row, "two")
                    println(toString(row[1]))
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals("null\ntwo", result.stdout(), mode + ": " + result.stderr());
        }
    }

    @Test
    void aSetLiteralGivesItsElementType() throws Exception {
        assertBoth("3.0\n5.0", """
                struct Box<T> { value: T }
                fn g<T>(b: Box<T>) -> T { b.value }
                fn main() {
                    imut s = g(Box { value: #{1.5, 2.5} })
                    for x in s { println(toString(x * 2)) }
                }
                """, "SetLiteralType");
    }

    @Test
    void anExportedTypeNamingAnotherModulesPrivateStructDoesNotCross(@TempDir Path dir) throws Exception {
        // Provenance crosses an import only when the struct it names here is the
        // one the exporter meant: a library's private P is not this module's P.
        Files.writeString(dir.resolve("lib.siyo"), """
                struct Box<T> { value: T }
                struct P { x: float }
                fn g<T>(b: Box<T>) -> T { b.value }
                pub imut ps = g(Box { value: [P { x: 1.5 }] })
                pub fn one() -> int { 1 }
                """);
        Path main = dir.resolve("main.siyo");
        Files.writeString(main, """
                import "lib"
                struct P { x: string, y: int }
                fn main() {
                    imut a = lib.ps
                    println(a[0].x)
                }
                """);
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, main.toString());
            assertEquals(1, result.exitCode(), mode);
            assertTrue(result.stderr().contains("does not have members"), mode + ": " + result.stderr());
        }
    }

    // --- Final gate at 56a4a07: an imported value keeps its origin's identity -----

    private static final String LIB_HEAD = "struct Box<T> { value: T }\nfn g<T>(b: Box<T>) -> T { b.value }\n";

    /** One import-identity case: the modules, and either the expected output or that access is rejected. */
    private record IdentityCase(String name, Map<String, String> modules, String expected) {
    }

    private static final List<IdentityCase> IDENTITY_CASES = List.of(
            // A public Row whose field is the exporter's private P, beside the importer's own P.
            new IdentityCase("privateBridgeDispatch", Map.of(
                    "lib.siyo", LIB_HEAD + "struct P { x: float }\nimpl P { fn who(self) -> string { \"lib\" } }\n"
                            + "pub struct Row { item: P }\npub imut values = g(Box { value: [Row { item: P { x: 1.5 } }] })\n",
                    "main.siyo", "import \"lib\"\nstruct P { x: float }\nimpl P { fn who(self) -> string { \"main\" } }\n"
                            + "fn main() {\n    imut a = lib.values\n    println(a[0].item.who())\n}\n"), null),
            new IdentityCase("privateBridgeField", Map.of(
                    "lib.siyo", LIB_HEAD + "struct P { x: float }\npub struct Row { item: P }\n"
                            + "pub imut values = g(Box { value: [Row { item: P { x: 1.5 } }] })\n",
                    "main.siyo", "import \"lib\"\nstruct P { x: string, y: int }\n"
                            + "fn main() {\n    imut a = lib.values\n    imut b = a\n    println(b[0].item.x)\n}\n"), null),
            // A later import replaces the public P the exported value was made of.
            new IdentityCase("laterImportReplaces", Map.of(
                    "lib.siyo", LIB_HEAD + "pub struct P { x: float }\npub imut values = g(Box { value: [P { x: 1.5 }] })\n",
                    "other.siyo", "pub struct P { x: int }\npub fn one() -> int { 1 }\n",
                    "main.siyo", "import \"lib\"\nimport \"other\"\n"
                            + "fn main() {\n    imut a = lib.values\n    println(toString(a[0].x * 2))\n}\n"), null),
            // The same two imports the other way round: lib's P is the one here, so it reads as lib's.
            new IdentityCase("earlierImportReplaced", Map.of(
                    "lib.siyo", LIB_HEAD + "pub struct P { x: float }\npub imut values = g(Box { value: [P { x: 1.5 }] })\n",
                    "other.siyo", "pub struct P { x: int }\npub fn one() -> int { 1 }\n",
                    "main.siyo", "import \"other\"\nimport \"lib\"\n"
                            + "fn main() {\n    imut a = lib.values\n    println(toString(a[0].x * 2))\n}\n"), "3.0"),
            // Public structs all the way down, through an aliased import: the genuine lib types.
            new IdentityCase("publicBridgeAliasedImport", Map.of(
                    "lib.siyo", LIB_HEAD + "pub struct P { x: float }\nimpl P { fn who(self) -> string { \"lib\" } }\n"
                            + "pub struct Row { item: P }\npub imut values = g(Box { value: [Row { item: P { x: 1.5 } }] })\n",
                    "main.siyo", "import \"lib\" as L\n"
                            + "fn main() {\n    imut a = L.values\n    imut b = a\n"
                            + "    println(toString(b[0].item.x * 2) + \" \" + b[0].item.who())\n}\n"), "3.0 lib"),
            // A recursive public type: checking identity terminates.
            new IdentityCase("recursiveType", Map.of(
                    "lib.siyo", LIB_HEAD + "pub struct Node { v: float, kids: Array<Node> }\n"
                            + "pub imut values = g(Box { value: [Node { v: 1.5, kids: [Node { v: 2.5, kids: [] }] }] })\n",
                    "main.siyo", "import \"lib\"\n"
                            + "fn main() {\n    imut a = lib.values\n    println(toString(a[0].kids[0].v * 2))\n}\n"), "5.0"),
            // A struct the importer cannot write at all, reached through a third module: no clash.
            new IdentityCase("unnameableStructThroughAThirdModule", Map.of(
                    "shapes.siyo", "pub struct P { x: float }\npub fn one() -> int { 1 }\n",
                    "lib.siyo", "import \"shapes\"\n" + LIB_HEAD + "pub struct Row { item: P, n: int }\n"
                            + "pub imut values = g(Box { value: [Row { item: P { x: 1.5 }, n: 4 }] })\n",
                    "main.siyo", "import \"lib\"\nfn main() {\n    imut a = lib.values\n    println(toString(a[0].n))\n}\n"), "4"),
            // A struct's own type parameter is not a type name, whatever this module declares.
            new IdentityCase("typeParameterNamedLikeAStruct", Map.of(
                    "lib.siyo", LIB_HEAD + "pub struct Wrap<A> { inner: A, tag: string }\n"
                            + "pub imut values = g(Box { value: [Wrap { inner: 1.5, tag: \"t\" }] })\n",
                    "main.siyo", "import \"lib\"\nstruct A { z: int }\n"
                            + "fn main() {\n    imut a = lib.values\n    println(a[0].tag + \" \" + toString(a[0].inner * 2.0))\n}\n"), "t 3.0"),
            // The exporter's private interface beside the importer's own of that name.
            new IdentityCase("privateInterface", Map.of(
                    "lib.siyo", LIB_HEAD + "interface Shape { fn area() -> int }\nstruct Sq { s: int }\n"
                            + "impl Shape for Sq { fn area(self) -> int { self.s * self.s } }\n"
                            + "fn mk() -> Array<Shape> { [Sq { s: 3 }] }\npub imut values = g(Box { value: mk() })\n",
                    "main.siyo", "import \"lib\"\ninterface Shape { fn area() -> int }\nstruct Sq { s: int }\n"
                            + "impl Shape for Sq { fn area(self) -> int { 7 } }\n"
                            + "fn main() {\n    imut a = lib.values\n    for s in a { println(toString(s.area())) }\n}\n"), null),
            // The exporter's private P inside a private sum type inside a public Row.
            new IdentityCase("privateStructInAUnionPayload", Map.of(
                    "lib.siyo", LIB_HEAD + "struct P { x: float }\nimpl P { fn who(self) -> string { \"lib\" } }\n"
                            + "type V = Has(P) | No\npub struct Row { v: V }\n"
                            + "pub imut values = g(Box { value: [Row { v: Has(P { x: 1.5 }) }] })\n",
                    "main.siyo", "import \"lib\"\nstruct P { x: float }\nimpl P { fn who(self) -> string { \"main\" } }\n"
                            + "type V = Has(P) | No\n"
                            + "fn main() {\n    imut a = lib.values\n"
                            + "    match a[0].v { Has(p) => println(p.who()), No => println(\"no\") }\n}\n"), null));

    @TestFactory
    java.util.stream.Stream<DynamicTest> anImportedValueIsReadAsItsOriginsTypesOrRejected(@TempDir Path dir) {
        return IDENTITY_CASES.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            Path project = Files.createDirectories(dir.resolve(c.name()));
            for (var module : c.modules().entrySet()) Files.writeString(project.resolve(module.getKey()), module.getValue());
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                if (c.expected() != null) {
                    assertEquals(c.expected(), result.stdout(), mode + ": " + result.stderr());
                } else {
                    // Rejected, at compile time or where the value is first used: never another
                    // module's type read or dispatched in its place, so nothing is printed.
                    assertEquals(1, result.exitCode(), mode + ": " + result.stdout());
                    assertEquals("", result.stdout(), mode);
                }
            }
        }));
    }

    // --- Gate at 6864526: an import inside a function changes names after a check --

    private static final String LIB_P = LIB_HEAD + "pub struct P { x: float }\nimpl P { fn who(self) -> string { \"lib\" } }\n"
            + "pub imut values = g(Box { value: [P { x: 1.5 }] })\n";
    private static final String OTHER_P = "pub struct P { x: float }\nimpl P { fn who(self) -> string { \"other\" } }\n";

    /**
     * One nested-import case. {@code expected} is the exact output; "lib-or-rejected"
     * means the value may be read through lib's own P or refused, but never through
     * another module's; a prefix ending "|rejected" means that output, then a refusal.
     */
    private record NestedCase(String name, String main, String expected) {
    }

    private static final String NESTED_HEAD = "import \"lib\"\nstruct W<T> { v: T }\ntype Opt<T> = Some(T) | Nothing\n"
                    + "fn wrap<T>(x: T) -> W<T> { W { v: x } }\nfn call0(f: fn() -> int) -> int { f() }\n"
                    + "fn first<A>(x: A, y: int) -> A { x }\n";
    private static final String IMPORTING_LAMBDA = "call0(fn() -> int {\n        import \"other\"\n        0\n    })";

    private static final List<NestedCase> NESTED_CASES = List.of(
            // An unused function reads lib.values first, then main imports other.
            new NestedCase("primedThenNestedImport", "import \"lib\"\nfn unused() { imut ignored = lib.values }\n"
                    + "fn main() {\n    import \"other\"\n    imut a = lib.values\n    println(a[0].who())\n}\n", "|rejected"),
            new NestedCase("unprimedNestedImport", "import \"lib\"\n"
                    + "fn main() {\n    import \"other\"\n    imut a = lib.values\n    println(a[0].who())\n}\n", "|rejected"),
            // An alias made before the import is re-checked where it is used after it.
            new NestedCase("aliasBeforeNestedImport", "import \"lib\"\n"
                    + "fn main() {\n    imut a = lib.values\n    imut b = a\n    import \"other\"\n    println(b[0].who())\n}\n", "|rejected"),
            // A read bound before the import is refused too: other's methods are
            // known from the start, since which runs first is not known statically.
            new NestedCase("readBeforeAndAfterNestedImport", "import \"lib\"\n"
                    + "fn early() { imut a = lib.values\n    println(a[0].who()) }\n"
                    + "fn main() {\n    early()\n    import \"other\"\n    imut b = lib.values\n    println(b[0].who())\n}\n", "|rejected"),
            // A function's own import, declared before or after main.
            new NestedCase("functionImportBeforeMain", "import \"lib\"\nfn side() { import \"other\" }\n"
                    + "fn main() {\n    imut a = lib.values\n    println(a[0].who())\n}\n", "lib-or-rejected"),
            new NestedCase("functionImportAfterMain", "import \"lib\"\n"
                    + "fn main() {\n    imut a = lib.values\n    println(a[0].who())\n}\nfn side() { import \"other\" }\n", "lib-or-rejected"),
            // Captured into a struct literal, a generic wrapper, a lambda's declared
            // return, a loop and a field local before the import: each re-checked.
            new NestedCase("structLiteralBeforeNestedImport", "import \"lib\"\nstruct W<T> { v: T }\n"
                    + "fn main() {\n    imut w = W { v: lib.values }\n    import \"other\"\n    println(w.v[0].who())\n}\n", "|rejected"),
            new NestedCase("genericWrapperBeforeNestedImport", "import \"lib\"\nstruct W<T> { v: T }\n"
                    + "fn wrap<T>(x: T) -> W<T> { W { v: x } }\n"
                    + "fn main() {\n    imut w = wrap(lib.values)\n    import \"other\"\n    println(w.v[0].who())\n}\n", "|rejected"),
            new NestedCase("lambdaReturnBeforeNestedImport", "import \"lib\"\nstruct W<T> { v: T }\n"
                    + "fn main() {\n    imut f = fn() -> W<Array<P>> { W { v: lib.values } }\n    import \"other\"\n"
                    + "    println(f().v[0].who())\n}\n", "|rejected"),
            new NestedCase("loopAndFieldLocalBeforeNestedImport", "import \"lib\"\nstruct W<T> { v: T }\n"
                    + "fn main() {\n    imut w = W { v: lib.values }\n    imut b = w.v\n    imut p = b[0]\n"
                    + "    import \"other\"\n    for q in b { println(q.who()) }\n}\n", "|rejected"),
            // A struct local taken from it: refused before the import as after it,
            // rather than dispatched by name.
            new NestedCase("structLocalMethodBeforeAndAfterNestedImport", "import \"lib\"\n"
                    + "fn main() {\n    imut a = lib.values\n    imut p = a[0]\n    println(p.who())\n"
                    + "    import \"other\"\n    println(p.who())\n}\n", "|rejected"),
            // An import inside the very statement that gives a local its type, in a
            // lambda bound after the value: a declaration, a loop's collection and a
            // match's target. The names mean what they meant before the statement.
            // `as o2` registers other's methods in main as well.
            new NestedCase("importInsideTheInitializer", NESTED_HEAD
                    + "fn main() {\n    imut w = first(wrap(lib.values), " + IMPORTING_LAMBDA + ")\n"
                    + "    import \"other\" as o2\n    println(w.v[0].who())\n}\n", "|rejected"),
            new NestedCase("importInsideTheLoopCollection", NESTED_HEAD
                    + "fn main() {\n    for w in first([wrap(lib.values)], " + IMPORTING_LAMBDA + ") {\n"
                    + "        import \"other\" as o2\n        println(w.v[0].who())\n    }\n}\n", "|rejected"),
            new NestedCase("importInsideTheMatchTarget", NESTED_HEAD
                    + "fn main() {\n    imut o: Opt<W<Array<P>>> = Some(wrap(lib.values))\n"
                    + "    match first(o, " + IMPORTING_LAMBDA + ") {\n"
                    + "        Some(w) => {\n            import \"other\" as o2\n            println(w.v[0].who())\n        }\n"
                    + "        Nothing => println(\"none\")\n    }\n}\n", "|rejected"),
            // The same three without the imports read lib's own P.
            new NestedCase("theSameStatementsWithoutImportsReadLib", NESTED_HEAD
                    + "fn main() {\n    imut w = first(wrap(lib.values), 0)\n    println(w.v[0].who())\n"
                    + "    for x in first([wrap(lib.values)], 0) { println(x.v[0].who()) }\n"
                    + "    imut o: Opt<W<Array<P>>> = Some(wrap(lib.values))\n"
                    + "    match first(o, 0) {\n        Some(y) => println(y.v[0].who())\n        Nothing => println(\"none\")\n    }\n}\n",
                    "lib\nlib\nlib"));

    @TestFactory
    java.util.stream.Stream<DynamicTest> aLaterImportNeverLetsAnImportedValueReadAnotherModulesType(@TempDir Path dir) {
        return NESTED_CASES.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            Path project = Files.createDirectories(dir.resolve(c.name()));
            Files.writeString(project.resolve("lib.siyo"), LIB_P);
            Files.writeString(project.resolve("other.siyo"), OTHER_P);
            Files.writeString(project.resolve("main.siyo"), c.main());
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                String where = c.name() + " " + mode + ": " + result.stdout() + " / " + result.stderr();
                assertFalse(result.stdout().contains("other"), where);
                if (!c.expected().contains("rejected")) {
                    assertEquals(0, result.exitCode(), where);
                    assertEquals(c.expected(), result.stdout(), where);
                } else if (c.expected().equals("lib-or-rejected")) {
                    assertTrue(result.exitCode() == 0 ? result.stdout().equals("lib") : result.stdout().isEmpty(), where);
                } else {
                    String before = c.expected().substring(0, c.expected().indexOf("|rejected"));
                    assertEquals(1, result.exitCode(), where);
                    assertEquals(before, result.stdout(), where);
                }
            }
        }));
    }

    // --- Gate at 40f6f7f: an inferred identity is never regained by repackaging ----

    private static final String TRANSPORT_LIB = LIB_HEAD + "pub struct P { x: float }\n"
            + "impl P { fn who(self) -> string { \"lib\" } }\n"
            + "pub fn mk() -> Array<P> { [P { x: 1.5 }] }\npub fn id<T>(x: T) -> T { x }\n"
            + "pub imut values = g(Box { value: [P { x: 1.5 }] })\n";
    private static final String TRANSPORT_DUP = "import \"lib\"\nimpl P { fn who(self) -> string { \"dup\" } }\n";
    private static final String TRANSPORT_HEAD = "import \"lib\"\nstruct W<T> { value: T }\nfn id<T>(x: T) -> T { x }\n";

    /** Each way a value p is rebuilt or passed on, as {declaration, use}; empty declaration for none. */
    private static final Map<String, String[]> REBUILDS = new java.util.LinkedHashMap<>();

    static {
        REBUILDS.put("alias", new String[]{"imut q = p", "println(q.who())"});
        REBUILDS.put("arrayVar", new String[]{"imut b = [p]", "println(b[0].who())"});
        REBUILDS.put("inlineArray", new String[]{"", "println([p][0].who())"});
        REBUILDS.put("pop", new String[]{"mut b = [p]", "println(pop(b).who())"});
        REBUILDS.put("filter", new String[]{"imut b = filter([p], fn(x: P) -> bool { true })", "println(b[0].who())"});
        REBUILDS.put("genericField", new String[]{"imut w = W { value: p }", "println(w.value.who())"});
        REBUILDS.put("ifValue", new String[]{"imut q = if true { p } else { p }", "println(q.who())"});
        REBUILDS.put("loopItem", new String[]{"imut b = [p]", "for it in b { println(it.who()) }"});
        REBUILDS.put("push", new String[]{"mut b = [p]\n    push(b, p)", "println(b[1].who())"});
        REBUILDS.put("importedId", new String[]{"", "println(lib.id(p).who())"});
        REBUILDS.put("localId", new String[]{"", "println(id(p).who())"});
        REBUILDS.put("idOfArray", new String[]{"imut b = id([p])", "println(b[0].who())"});
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> anInferredIdentityRejectedOnceIsNeverRegainedByRepackaging(@TempDir Path dir) {
        // p is lib's P, taken from an imported call (0.10.0 names its result) or an
        // imported value. dup declares lib's who again, which the interpreter would
        // find by name. Imported before or after p is rebuilt, every rebuilding
        // fails as 0.9.0 failed; with no such import none prints dup, and both
        // backends agree.
        List<DynamicTest> tests = new java.util.ArrayList<>();
        for (String source : List.of("lib.mk()", "lib.values")) {
            for (var transport : REBUILDS.entrySet()) {
                for (String when : List.of("none", "before", "after")) {
                    String name = (source.equals("lib.mk()") ? "call" : "value") + "_" + transport.getKey() + "_" + when;
                    StringBuilder main = new StringBuilder(TRANSPORT_HEAD + "fn main() {\n    imut a = " + source
                            + "\n    imut p = a[0]\n");
                    if (when.equals("before")) main.append("    import \"dup\"\n");
                    if (!transport.getValue()[0].isEmpty()) main.append("    ").append(transport.getValue()[0]).append("\n");
                    if (when.equals("after")) main.append("    import \"dup\"\n");
                    main.append("    ").append(transport.getValue()[1]).append("\n}\n");
                    tests.add(DynamicTest.dynamicTest(name, () -> {
                        Path project = Files.createDirectories(dir.resolve(name));
                        Files.writeString(project.resolve("lib.siyo"), TRANSPORT_LIB);
                        Files.writeString(project.resolve("dup.siyo"), TRANSPORT_DUP);
                        Files.writeString(project.resolve("main.siyo"), main.toString());
                        List<CliParityTest.Result> results = new java.util.ArrayList<>();
                        for (String mode : List.of("run", "interpret")) {
                            results.add(CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString()));
                        }
                        String where = name + ": " + results.get(0).stdout() + " / " + results.get(1).stdout()
                                + " / " + results.get(1).stderr();
                        for (CliParityTest.Result result : results) {
                            assertFalse(result.stdout().contains("dup"), where);
                            if (!when.equals("none")) {
                                assertEquals(1, result.exitCode(), where);
                                assertEquals("", result.stdout(), where);
                            }
                        }
                        assertEquals(results.get(0).stdout(), results.get(1).stdout(), where);
                        assertEquals(results.get(0).exitCode(), results.get(1).exitCode(), where);
                    }));
                }
            }
        }
        return tests.stream();
    }

    private static final List<String[]> HELD_CASES = List.of(
            // dup2's own function imports dup: what that brings in reaches main too.
            new String[]{"aModuleWhoseFunctionImportsItImportedNested", "import \"lib\"\nfn main() {\n    imut p = lib.mk()[0]\n    import \"dup2\"\n    dup2.inner()\n    println(p.who())\n}\n", "rejected"},
            new String[]{"thatModuleImportedAtTopLevel", "import \"lib\"\nimport \"dup2\"\nfn main() {\n    imut p = lib.mk()[0]\n    println(p.who())\n}\n", "lib"},
            new String[]{"closureBoundBeforeTheImport", "import \"lib\"\nfn main() {\n    imut p = lib.mk()[0]\n    imut f = fn() -> string { p.who() }\n    import \"dup\"\n    println(f())\n}\n", "rejected"},
            new String[]{"lambdaOverACallBoundBeforeTheImport", "import \"lib\"\nfn main() {\n    imut f = fn() -> string { lib.mk()[0].who() }\n    import \"dup\"\n    println(f())\n}\n", "rejected"},
            new String[]{"functionBoundBeforeMainsImport", "import \"lib\"\nfn show() {\n    imut p = lib.mk()[0]\n    println(p.who())\n}\nfn main() {\n    import \"dup\"\n    show()\n}\n", "rejected"},
            new String[]{"importInAFunctionDeclaredAfterMain", "import \"lib\"\nfn main() {\n    imut p = lib.mk()[0]\n    loadDup()\n    println(p.who())\n}\nfn loadDup() { import \"dup\" }\n", "rejected"},
            new String[]{"closureWithNoSuchImport", "import \"lib\"\nfn main() {\n    imut p = lib.mk()[0]\n    imut f = fn() -> string { p.who() }\n    println(f())\n}\n", "lib"},
            new String[]{"structAnnotationOverAnInferredValue", "import \"lib\"\nfn main() {\n    import \"dup\"\n    imut q: P = lib.mk()[0]\n    println(q.who())\n}\n", "rejected"},
            new String[]{"anUnrelatedStructStoredALength", "import \"lib\"\nstruct S { q: P, n: int }\nimpl S { fn hello(self) -> string { \"hello\" } }\nstruct W { n: int }\nimpl W { fn hello(self) -> string { \"w\" } }\nfn id<T>(x: T) -> T { x }\nfn main() {\n    imut a = lib.mk()\n    mut w = W { n: 1 }\n    w.n = len(a)\n    import \"dup\"\n    println(w.hello())\n}\n", "w"},
            new String[]{"anUnrelatedStructBuiltFromAField", "import \"lib\"\nstruct S { q: P, n: int }\nimpl S { fn hello(self) -> string { \"hello\" } }\nstruct W { n: int }\nimpl W { fn hello(self) -> string { \"w\" } }\nfn id<T>(x: T) -> T { x }\nfn main() {\n    imut p = lib.mk()[0]\n    imut w = W { n: toInt(p.x) }\n    import \"dup\"\n    println(w.hello())\n}\n", "w"},
            new String[]{"aStructHoldingTheValue", "import \"lib\"\nstruct S { q: P, n: int }\nimpl S { fn hello(self) -> string { \"hello\" } }\nstruct W { n: int }\nimpl W { fn hello(self) -> string { \"w\" } }\nfn id<T>(x: T) -> T { x }\nfn main() {\n    imut a = lib.mk()\n    import \"dup\"\n    println(S { q: a[0], n: 1 }.hello())\n}\n", "hello"},
            new String[]{"aGenericCallOverAStructHoldingTheValue", "import \"lib\"\nstruct S { q: P, n: int }\nimpl S { fn hello(self) -> string { \"hello\" } }\nstruct W { n: int }\nimpl W { fn hello(self) -> string { \"w\" } }\nfn id<T>(x: T) -> T { x }\nfn main() {\n    imut a = lib.mk()\n    imut s = S { q: a[0], n: 1 }\n    import \"dup\"\n    println(id(s).hello())\n}\n", "hello"},
            new String[]{"theHeldValuesOwnMethodThroughIt", "import \"lib\"\nstruct S { q: P, n: int }\nimpl S { fn hello(self) -> string { \"hello\" } }\nstruct W { n: int }\nimpl W { fn hello(self) -> string { \"w\" } }\nfn id<T>(x: T) -> T { x }\nfn main() {\n    imut a = lib.mk()\n    imut s = S { q: a[0], n: 1 }\n    import \"dup\"\n    println(id(s).q.who())\n}\n", "rejected"});

    @TestFactory
    java.util.stream.Stream<DynamicTest> aContestIsHeldWhereverTheCallRunsAndOnlyAgainstItsStruct(@TempDir Path dir) {
        // dup's methods are known before any body is bound, so a call bound before
        // its import, in a closure, an earlier function or beside a function that
        // imports it later, is refused as 0.9.0 refused it. Only the struct a
        // method is called on counts: W or S beside a contested P keep theirs.
        return HELD_CASES.stream().map(c -> DynamicTest.dynamicTest(c[0], () -> {
            Path project = Files.createDirectories(dir.resolve(c[0]));
            Files.writeString(project.resolve("lib.siyo"), TRANSPORT_LIB);
            Files.writeString(project.resolve("dup.siyo"), TRANSPORT_DUP);
            Files.writeString(project.resolve("dup2.siyo"), "pub fn inner() { import \"dup\" }\n");
            Files.writeString(project.resolve("main.siyo"), c[1]);
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                String where = c[0] + " " + mode + ": " + result.stdout() + " / " + result.stderr();
                if (c[2].equals("rejected")) {
                    assertEquals(1, result.exitCode(), where);
                    assertEquals("", result.stdout(), where);
                    assertTrue(result.stderr().contains("No matching method"), where);
                } else {
                    assertEquals(0, result.exitCode(), where);
                    assertEquals(c[2], result.stdout(), where);
                }
            }
        }));
    }

    // --- Gate at 79348c0: stores through aliases, and initialisation order -------

    /** {name, declaration of w, a store of p into w, a use through the alias c}. */
    private static final List<String[]> ALIAS_STORES = List.of(
            new String[]{"field", "mut w = W { value: P { x: 0.0 } }", "w.value = p", "c.value.who()"},
            new String[]{"index", "mut w = [P { x: 0.0 }]", "w[0] = p", "c[0].who()"},
            new String[]{"map", "mut w = {\"k\": P { x: 0.0 }}", "w[\"k\"] = p", "c[\"k\"].who()"},
            new String[]{"push", "mut w = [P { x: 0.0 }]", "push(w, p)", "c[1].who()"},
            new String[]{"fieldRebuilt", "mut w = W { value: P { x: 0.0 } }", "w.value = p", "pop([c.value]).who()"},
            new String[]{"aliasOfAlias", "mut w = W { value: P { x: 0.0 } }", "w.value = p", "d.value.who()"});

    @TestFactory
    java.util.stream.Stream<DynamicTest> aStoreIsSeenThroughEveryAliasTakenBeforeIt(@TempDir Path dir) {
        // c is taken from w before p, inferred from lib.mk(), is stored into w.
        // With dup imported, a method through c fails as through w; without it,
        // both backends agree and none prints dup.
        List<DynamicTest> tests = new java.util.ArrayList<>();
        for (String[] c : ALIAS_STORES) {
            for (boolean contested : List.of(true, false)) {
                String name = c[0] + (contested ? "_dup" : "_clean");
                String main = TRANSPORT_HEAD + "fn main() {\n    imut p = lib.mk()[0]\n    " + c[1]
                        + "\n    imut c = w\n    imut d = c\n" + (contested ? "    import \"dup\"\n" : "")
                        + "    " + c[2] + "\n    println(" + c[3] + ")\n}\n";
                tests.add(DynamicTest.dynamicTest(name, () -> {
                    Path project = Files.createDirectories(dir.resolve(name));
                    Files.writeString(project.resolve("lib.siyo"), TRANSPORT_LIB);
                    Files.writeString(project.resolve("dup.siyo"), TRANSPORT_DUP);
                    Files.writeString(project.resolve("main.siyo"), main);
                    List<CliParityTest.Result> results = new java.util.ArrayList<>();
                    for (String mode : List.of("run", "interpret")) {
                        results.add(CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString()));
                    }
                    String where = name + ": " + results.get(0).stdout() + " / " + results.get(1).stdout()
                            + " / " + results.get(1).stderr();
                    for (CliParityTest.Result result : results) {
                        assertFalse(result.stdout().contains("dup"), where);
                        if (contested) {
                            assertEquals(1, result.exitCode(), where);
                            assertTrue(result.stderr().contains("No matching method"), where);
                        }
                    }
                    assertEquals(results.get(0).stdout(), results.get(1).stdout(), where);
                    assertEquals(results.get(0).exitCode(), results.get(1).exitCode(), where);
                }));
            }
        }
        return tests.stream();
    }

    /** {name, program after the head, expected}: stores reached through calls, closures, loops and holders. */
    private static final List<String[]> ALIAS_ROUTES = List.of(
            // 0.9.0 typed an element of a literal of struct literals, not of a
            // literal holding a variable: pop([w]) is 0.10.0's typing of w's holder.
            new String[]{"aCollectionAnnotationOverAnIf", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = [w]\n    imut b: Array<W<P>> = if true { a } else { a }\n    import \"dup\"\n    println(b[0].value.who())\n}\n", "rejected"},
            new String[]{"aCollectionAnnotationOverAnElement", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = [w]\n    imut h = [a]\n    imut b: Array<W<P>> = h[0]\n    import \"dup\"\n    println(b[0].value.who())\n}\n", "rejected"},
            new String[]{"aCollectionAnnotationOverAGenericCall", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = [w]\n    imut b: Array<W<P>> = id(a)\n    import \"dup\"\n    println(b[0].value.who())\n}\n", "rejected"},
            new String[]{"aClosureCallsResultInALocal", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut f = fn() -> W<P> { w }\n    imut a = f()\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"aClosureCallsResultUsedDirectly", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut f = fn() -> W<P> { w }\n    import \"dup\"\n    println(f().value.who())\n}\n", "rejected"},
            new String[]{"aClosureCallsResultWithNoCollision", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut f = fn() -> W<P> { w }\n    println(f().value.who())\n}\n", "lib"},
            new String[]{"aGenericHolderPoppedFromALiteralOfACall", "fn mkw() -> W<P> { W { value: P { x: 0.0 } } }\nfn main() {\n    imut a = pop([mkw()])\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"aCollectionAnnotationOverAVariable", "fn main() {\n    imut a = [W { value: P { x: 0.0 } }]\n    imut b: Array<W<P>> = a\n    import \"dup\"\n    println(b[0].value.who())\n}\n", "rejected"},
            new String[]{"anAliasOfAnAnnotatedAlias", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = [w]\n    imut a1 = a\n    imut b: Array<W<P>> = a1\n    imut c = b\n    import \"dup\"\n    println(c[0].value.who())\n}\n", "rejected"},
            new String[]{"aGenericHolderPoppedFromALiteralOfIt", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = pop([w])\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"aGenericHolderPoppedThroughFilter", "fn main() {\n    imut w = W { value: P { x: 0.0 } }\n    imut a = pop(filter([w], fn(x: W<P>) -> bool { true }))\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            // 0.9.0 typed no local taken from a field or a generic call, and the
            // re-registration of lib's own who by a nested module changes nothing.
            new String[]{"aPlainStructLocalTakenFromAField", "struct PW { value: P }\nstruct H { w: PW }\nfn main() {\n    imut h = H { w: PW { value: P { x: 1.0 } } }\n    imut a = h.w\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"aPlainStructLocalFromALocalGenericCall", "struct PW { value: P }\nstruct H { w: PW }\nfn main() {\n    imut a = id(PW { value: P { x: 1.0 } })\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"aPlainStructLocalFromAnImportedGenericCall", "struct PW { value: P }\nstruct H { w: PW }\nfn main() {\n    imut a = lib.id(PW { value: P { x: 1.0 } })\n    import \"dup\"\n    println(a.value.who())\n}\n", "rejected"},
            new String[]{"topLevelDupBesideANestedModuleAddingAnotherMethod", "import \"dup\"\nfn main() {\n    import \"ext\"\n    imut a = W { value: lib.mk()[0] }\n    println(a.value.who())\n}\n", "lib"},
            // A duplicate a top-level import brings in is registered before anything
            // runs, the same for both backends: these run as 0.9.0 runs them. An
            // alias of a generic struct is typed only by 0.10.0 (0.9.0 lost an
            // alias's struct), so through a contested P it is refused.
            new String[]{"topLevelDupAnnotatedArrayStore", "import \"dup\"\nfn main() {\n    mut arr: Array<P> = [P { x: 0.0 }]\n    arr[0] = lib.mk()[0]\n    println(arr[0].who())\n}\n", "lib"},
            new String[]{"topLevelDupAnnotatedMapStore", "import \"dup\"\nfn main() {\n    mut m: Map<string, P> = {}\n    m[\"k\"] = lib.mk()[0]\n    println(m[\"k\"].who())\n}\n", "lib"},
            new String[]{"topLevelDupGenericFieldStore", "import \"dup\"\nfn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    w.value = p\n    println(w.value.who())\n}\n", "lib"},
            new String[]{"aGenericAliasReadAfterAStore", "fn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut alias = w\n    import \"dup\"\n    w.value = p\n    println(alias.value.who())\n}\n", "rejected"},
            new String[]{"aGenericAliasOfAFreshValue", "fn main() {\n    mut w = W { value: P { x: 0.0 } }\n    imut alias = w\n    import \"dup\"\n    w.value = P { x: 3.0 }\n    println(alias.value.who())\n}\n", "rejected"},
            new String[]{"aGenericAliasReadInAClosure", "fn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut alias = w\n    imut f = fn() -> string { alias.value.who() }\n    import \"dup\"\n    w.value = p\n    println(f())\n}\n", "rejected"},
            new String[]{"aStoreByACallInADeclaration", "fn put(t: W<P>, q: P) -> int {\n    t.value = q\n    1\n}\nfn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    import \"dup\"\n    imut n = put(w, p)\n    println(w.value.who())\n}\n", "rejected"},
            new String[]{"aOneArgumentClosureThatCapturedTheValue", "fn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut setter = fn(t: W<P>) { t.value = p }\n    import \"dup\"\n    setter(w)\n    println(w.value.who())\n}\n", "rejected"},
            new String[]{"aCallThatCannotStoreIntoItsArgument", "fn show(q: P, xs: Array<P>) -> int { 1 }\nimport \"dup\"\nfn main() {\n    imut xs = lib.mk()\n    imut fresh = P { x: 2.0 }\n    show(fresh, xs)\n    println(fresh.who())\n}\n", "lib"},
            new String[]{"storeThroughAParameter", "fn put(t: W<P>, q: P) { t.value = q }\nfn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut alias = w\n    import \"dup\"\n    put(w, p)\n    println(alias.value.who())\n}\n", "rejected"},
            new String[]{"anAliasReturnedByAFunction", "fn same(t: W<P>) -> W<P> { t }\nfn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut alias = same(w)\n    import \"dup\"\n    w.value = p\n    println(alias.value.who())\n}\n", "rejected"},
            new String[]{"anAliasReturnedByAClosure", "fn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut get = fn() -> W<P> { w }\n    imut alias = get()\n    import \"dup\"\n    w.value = p\n    println(alias.value.who())\n}\n", "rejected"},
            new String[]{"aStoreThroughALoopItem", "fn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut ws = [w]\n    import \"dup\"\n    for it in ws { it.value = p }\n    println(w.value.who())\n}\n", "rejected"},
            new String[]{"aStoreThroughAHolder", "struct V<T> { inner: T }\nfn main() {\n    imut p = lib.mk()[0]\n    mut w = W { value: P { x: 0.0 } }\n    imut v = V { inner: w }\n    import \"dup\"\n    v.inner.value = p\n    println(w.value.who())\n}\n", "rejected"},
            new String[]{"aFreshValuePushedBesideInferredOnes", "import \"dup\"\nfn main() {\n    mut list = lib.mk()\n    imut fresh = P { x: 2.0 }\n    push(list, fresh)\n    println(fresh.who())\n}\n", "lib"});

    @TestFactory
    java.util.stream.Stream<DynamicTest> aStoreReachesAliasesThroughCallsClosuresLoopsAndHolders(@TempDir Path dir) {
        // A store made through a parameter, a loop item or a holder, or seen
        // through an alias a function or closure handed back, reaches every alias;
        // a fresh value stored beside inferred ones takes nothing from them.
        return ALIAS_ROUTES.stream().map(c -> DynamicTest.dynamicTest(c[0], () -> {
            Path project = Files.createDirectories(dir.resolve(c[0]));
            Files.writeString(project.resolve("lib.siyo"), TRANSPORT_LIB);
            Files.writeString(project.resolve("dup.siyo"), TRANSPORT_DUP);
            Files.writeString(project.resolve("ext.siyo"),
                    "import \"lib\"\nimpl P { fn other(self) -> string { \"other\" } }\n");
            Files.writeString(project.resolve("main.siyo"), TRANSPORT_HEAD + c[1]);
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                String where = c[0] + " " + mode + ": " + result.stdout() + " / " + result.stderr();
                if (c[2].equals("rejected")) {
                    assertEquals(1, result.exitCode(), where);
                    assertEquals("", result.stdout(), where);
                    assertTrue(result.stderr().contains("No matching method"), where);
                } else {
                    assertEquals(0, result.exitCode(), where);
                    assertEquals(c[2], result.stdout(), where);
                }
            }
        }));
    }

    /** {name, program with VALUE where the stored P goes}: a field typed P read after dup's import. */
    private static final List<String[]> FIELD_READ_ROUTES = List.of(
            new String[]{"putFetchesItsOwnValue", "fn put1(t: W<P>) { t.value = VALUE }\nfn main() {\n    mut w = W { value: P { x: 0.0 } }\n    import \"dup\"\n    put1(w)\n    println(w.value.who())\n}\n"},
            new String[]{"closureReadsBeforeTheStore", "fn main() {\n    imut p = VALUE\n    mut w = W { value: P { x: 0.0 } }\n    imut f = fn() -> string { w.value.who() }\n    import \"dup\"\n    w.value = p\n    println(f())\n}\n"},
            new String[]{"loopReadsAfterABackEdgeStore", "fn main() {\n    imut p = VALUE\n    mut w = W { value: P { x: 0.0 } }\n    mut i = 0\n    import \"dup\"\n    while i < 2 {\n        if i == 1 { println(w.value.who()) } else { w.value = p }\n        i = i + 1\n    }\n}\n"});

    @TestFactory
    java.util.stream.Stream<DynamicTest> aFieldTypedPReadsTheSameWhetherItsValueWasInferredOrNot(@TempDir Path dir) {
        // Reading a field typed P after dup redeclared P.who is 0.9.0's own
        // same-name dispatch: with a plain struct W { value: P }, 0.9.0 gives
        // these exact routes the same per-backend output. What 0.10.0 infers
        // must neither add to nor hide from that: the value fetched from
        // lib.mk() behaves as a fresh P literal does, on each backend.
        return FIELD_READ_ROUTES.stream().map(c -> DynamicTest.dynamicTest(c[0], () -> {
            Map<String, CliParityTest.Result> results = new java.util.HashMap<>();
            for (String value : List.of("lib.mk()[0]", "P { x: 3.0 }")) {
                Path project = Files.createDirectories(dir.resolve(c[0] + (value.startsWith("lib") ? "_inferred" : "_fresh")));
                Files.writeString(project.resolve("lib.siyo"), TRANSPORT_LIB);
                Files.writeString(project.resolve("dup.siyo"), TRANSPORT_DUP);
                Files.writeString(project.resolve("main.siyo"), TRANSPORT_HEAD + c[1].replace("VALUE", value));
                for (String mode : List.of("run", "interpret")) {
                    results.put(value + mode, CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString()));
                }
            }
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result inferred = results.get("lib.mk()[0]" + mode);
                CliParityTest.Result fresh = results.get("P { x: 3.0 }" + mode);
                String where = c[0] + " " + mode + ": " + inferred.stdout() + " / " + fresh.stdout() + " / " + inferred.stderr();
                assertEquals(fresh.exitCode(), inferred.exitCode(), where);
                assertEquals(fresh.stdout(), inferred.stdout(), where);
            }
        }));
    }

    /** {name, main, the exact output}: as 0.9.0 and 40f6f7f give it on both backends. */
    private static final List<String[]> INIT_ORDERS = List.of(
            new String[]{"flatUnused", "fn side() {\n    import \"a\"\n    import \"b\"\n}\nfn main() { println(\"MAIN\") }\n", "A\nB\nMAIN"},
            new String[]{"flatCalled", "fn side() {\n    import \"a\"\n    import \"b\"\n}\nfn main() {\n    println(\"MAIN\")\n    side()\n}\n", "A\nB\nMAIN"},
            new String[]{"nestedFirst", "fn side() {\n    if true { import \"a\" }\n    import \"b\"\n}\nfn main() { println(\"MAIN\") }\n", "A\nB\nMAIN"},
            new String[]{"nestedSecond", "fn side() {\n    import \"a\"\n    if true { import \"b\" }\n}\nfn main() {\n    println(\"MAIN\")\n    side()\n}\n", "A\nB\nMAIN"},
            new String[]{"lambdaFirst", "fn side() {\n    imut f = fn() { import \"a\" }\n    import \"b\"\n}\nfn main() { println(\"MAIN\") }\n", "A\nB\nMAIN"},
            new String[]{"ifElse", "fn side() {\n    if true { import \"a\" } else { import \"b\" }\n    import \"c\"\n}\nfn main() {\n    println(\"MAIN\")\n    side()\n}\n", "A\nB\nC\nMAIN"},
            new String[]{"deep", "fn side() {\n    if true {\n        if true { import \"a\" }\n        import \"b\"\n    }\n    import \"c\"\n}\nfn main() { println(\"MAIN\") }\n", "B\nA\nC\nMAIN"},
            new String[]{"diamond", "import \"left\"\nfn side() { import \"right\" }\nfn main() { println(toString(left.ok())) }\n", "A\n1"},
            new String[]{"transitive", "fn side() {\n    import \"c\"\n    import \"m\"\n}\nfn main() {\n    println(\"MAIN\")\n    side()\n}\n", "C\nA\nB\nM\nMAIN"});

    @TestFactory
    java.util.stream.Stream<DynamicTest> modulesInitialiseInTheOrderTheirImportsAreBound(@TempDir Path dir) {
        // Each module prints its letter as it initialises. Compiling a nested
        // import ahead (to know what it brings in) must not change when it runs.
        return INIT_ORDERS.stream().map(c -> DynamicTest.dynamicTest(c[0], () -> {
            Path project = Files.createDirectories(dir.resolve(c[0]));
            for (String letter : List.of("a", "b", "c")) {
                Files.writeString(project.resolve(letter + ".siyo"), "fn seed() -> int {\n    println(\""
                        + letter.toUpperCase() + "\")\n    1\n}\npub imut v = seed()\npub fn ok() -> int { v }\n");
            }
            Files.writeString(project.resolve("left.siyo"), "import \"a\"\npub fn ok() -> int { a.ok() }\n");
            Files.writeString(project.resolve("right.siyo"), "import \"a\"\npub fn ok() -> int { a.ok() }\n");
            Files.writeString(project.resolve("m.siyo"), "fn seed() -> int {\n    println(\"M\")\n    1\n}\n"
                    + "pub imut v = seed()\npub fn g() {\n    if true { import \"a\" }\n    import \"b\"\n}\n");
            Files.writeString(project.resolve("main.siyo"), c[1]);
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                assertEquals(c[2], result.stdout(), c[0] + " " + mode + ": " + result.stderr());
            }
        }));
    }

    @Test
    void anAnnotatedValueKeepsZeroNinesRouteButNotGenericInference(@TempDir Path dir) throws Exception {
        // An annotation is 0.9.0's route: b[0].who() runs as 0.9.0 ran it. A generic
        // call over it is 0.10.0's inference, and fails as 0.9.0 did once P is contested.
        Files.writeString(dir.resolve("lib.siyo"), TRANSPORT_LIB);
        Files.writeString(dir.resolve("dup.siyo"), TRANSPORT_DUP);
        String head = TRANSPORT_HEAD + "fn main() {\n    imut a: Array<P> = lib.mk()\n    imut p = a[0]\n";
        Files.writeString(dir.resolve("plain.siyo"), head + "    imut b = [p]\n    println(b[0].who())\n}\n");
        Files.writeString(dir.resolve("generic.siyo"), head + "    import \"dup\"\n    println(lib.id(p).who())\n}\n");
        Files.writeString(dir.resolve("localGeneric.siyo"), head + "    import \"dup\"\n    println(id(p).who())\n}\n");
        for (String mode : List.of("run", "interpret")) {
            assertEquals("lib", CliParityTest.siyoc(dir, mode, dir.resolve("plain.siyo").toString()).stdout(), mode);
            for (String file : List.of("generic.siyo", "localGeneric.siyo")) {
                CliParityTest.Result result = CliParityTest.siyoc(dir, mode, dir.resolve(file).toString());
                assertEquals(1, result.exitCode(), file + " " + mode + ": " + result.stdout());
                assertTrue(result.stderr().contains("No matching method"), file + " " + mode + ": " + result.stderr());
            }
        }
    }

    @Test
    void aLongChainOfGenericHolderAliasesStaysWithinABoundedHeap(@TempDir Path dir) throws Exception {
        // 4,000 aliases of one W<P>: each union used to leave its retired cell
        // holding every root so far, so the chain held 4,000 * 4,000 / 2 entries
        // and ran out of 256 MB. 79348c0 runs it in 64 MB interpreted.
        StringBuilder main = new StringBuilder(TRANSPORT_HEAD + "fn main() {\n    imut a0 = W { value: P { x: 1.0 } }\n");
        for (int i = 1; i < 4000; i++) main.append("    imut a").append(i).append(" = a").append(i - 1).append("\n");
        main.append("    println(a3999.value.who())\n}\n");
        Files.writeString(dir.resolve("lib.siyo"), TRANSPORT_LIB);
        Files.writeString(dir.resolve("main.siyo"), main.toString());
        CliParityTest.Result interpreted = CliParityTest.siyoc(dir, Map.of("JAVA_TOOL_OPTIONS", "-Xmx64m"),
                "interpret", dir.resolve("main.siyo").toString());
        assertEquals("lib", interpreted.stdout(), interpreted.stderr());
        // Compiled, the 4,000 locals of one method need more, as at 79348c0.
        CliParityTest.Result compiled = CliParityTest.siyoc(dir, Map.of("JAVA_TOOL_OPTIONS", "-Xmx128m"),
                "run", dir.resolve("main.siyo").toString());
        assertEquals("lib", compiled.stdout(), compiled.stderr());
    }

    @Test
    void aLongChainOfPoppedGenericHoldersStaysWithinABoundedHeap(@TempDir Path dir) throws Exception {
        // a_i = pop([a_(i-1)]): each link is typed only by 0.10.0, and checked the
        // same way as the one before, so the chain keeps one root, not one per link.
        StringBuilder main = new StringBuilder(TRANSPORT_HEAD + "fn main() {\n    imut a0 = W { value: P { x: 1.0 } }\n");
        for (int i = 1; i < 4000; i++) main.append("    imut a").append(i).append(" = pop([a").append(i - 1).append("])\n");
        main.append("    println(a3999.value.who())\n}\n");
        Files.writeString(dir.resolve("lib.siyo"), TRANSPORT_LIB);
        Files.writeString(dir.resolve("main.siyo"), main.toString());
        CliParityTest.Result interpreted = CliParityTest.siyoc(dir, Map.of("JAVA_TOOL_OPTIONS", "-Xmx64m"),
                "interpret", dir.resolve("main.siyo").toString());
        assertEquals("lib", interpreted.stdout(), interpreted.stderr());
    }

    @Test
    void aUnionOfStorageKeepsEachRootOnce() throws Exception {
        // A chain of aliases, each also given what the one before held: the cell
        // a union retires is a link only, so the entries kept are the roots there
        // are, not one copy per alias.
        List<VariableSymbol> chain = new java.util.ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            VariableSymbol variable = new VariableSymbol("a" + i, true, SiyoStruct.class);
            if (i > 0) {
                VariableSymbol previous = chain.get(i - 1);
                java.util.Set<Object> roots = new java.util.LinkedHashSet<>(previous.getProvenance());
                roots.add(previous);
                variable.addProvenance(roots);
                variable.shareStorageWith(previous);
            }
            chain.add(variable);
        }
        java.lang.reflect.Field cellField = VariableSymbol.class.getDeclaredField("_cell");
        cellField.setAccessible(true);
        java.lang.reflect.Field rootsField = cellField.getType().getDeclaredField("_roots");
        rootsField.setAccessible(true);
        long kept = 0;
        for (VariableSymbol variable : chain) kept += ((java.util.Set<?>) rootsField.get(cellField.get(variable))).size();
        int roots = chain.get(chain.size() - 1).getProvenance().size();
        assertEquals(1999, roots);
        assertEquals(roots, kept);
        for (VariableSymbol variable : chain) assertEquals(roots, variable.getProvenance().size());
    }

    @Test
    void aStructLocalsFieldStillReadsAsTrackedAfterANestedImport(@TempDir Path dir) throws Exception {
        // Refusing the local's methods must not erase its field reads: p.x + 1
        // on an erased value would be read at int compiled against other's P.
        Files.writeString(dir.resolve("lib.siyo"), LIB_P);
        Files.writeString(dir.resolve("other.siyo"), "pub struct P { x: int }\n");
        Files.writeString(dir.resolve("main.siyo"), "import \"lib\"\nfn main() {\n    imut a = lib.values\n"
                + "    imut p = a[0]\n    import \"other\"\n    println(toString(p.x + 1))\n}\n");
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, dir.resolve("main.siyo").toString());
            assertEquals("2.5", result.stdout(), mode + ": " + result.stderr());
        }
    }

    @Test
    void anImportInsideTheExportersOwnFunctionDoesNotChangeWhatItsValueMeans(@TempDir Path dir) throws Exception {
        // lib's own function imports other, replacing lib's P in lib's tables after
        // `values` was declared. The importer, which never names other, must not
        // read lib's value as other's P.
        Files.writeString(dir.resolve("lib.siyo"), LIB_P + "fn side() { import \"other\" }\n");
        Files.writeString(dir.resolve("other.siyo"), OTHER_P);
        Files.writeString(dir.resolve("main.siyo"),
                "import \"lib\"\nfn main() {\n    imut a = lib.values\n    println(a[0].who())\n}\n");
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, dir.resolve("main.siyo").toString());
            String where = mode + ": " + result.stdout() + " / " + result.stderr();
            assertFalse(result.stdout().contains("other"), where);
            assertTrue(result.exitCode() == 0 ? result.stdout().equals("lib") : result.stdout().isEmpty(), where);
        }
    }

    // --- An exporter's own nested import and a return type 0.10.0 now infers -------

    private static final String RETURN_LIB = "pub struct Box<T> { value: T }\npub struct P { x: float }\nimpl P { fn who(self) -> string { \"lib\" } }\npub fn mk() -> Array<P> { [P { x: 1.5 }] }\npub fn mkp() -> P { P { x: 1.5 } }\npub fn boxed() -> Box<Array<P>> { Box { value: [P { x: 1.5 }] } }\npub fn id<T>(t: T) -> T { t }\npub interface Named { fn name() -> string }\nimpl Named for P { fn name(self) -> string { \"named-lib\" } }\n";

    /**
     * {@code side}: lib's own function imports other; {@code top}: lib imports other
     * before its own P; {@code unrelated}: lib's function imports a module with no P.
     */
    private record ReturnCase(String name, String libSide, String main, String expected) {
    }

    private static final List<ReturnCase> RETURN_CASES = List.of(
            new ReturnCase("unannotatedReturn", "side", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("indexedCall", "side", "import \"lib\"\nfn main() { println(lib.mk()[0].who()) }\n", "rejected"),
            new ReturnCase("genericStructReturn", "side", "import \"lib\"\nfn main() {\n    imut b = lib.boxed()\n    println(b.value[0].who())\n}\n", "rejected"),
            new ReturnCase("genericCallOverAStructReturn", "side", "import \"lib\"\nfn main() { println(lib.id(lib.mkp()).who()) }\n", "rejected"),
            new ReturnCase("localGenericCallOverAStructReturn", "side", "import \"lib\"\nfn id<T>(t: T) -> T { t }\nfn main() { println(id(lib.mkp()).who()) }\n", "rejected"),
            new ReturnCase("importerImportsOtherFirst", "none", "import \"other\"\nimport \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("importerImportsOtherAfter", "none", "import \"lib\"\nimport \"other\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("importersOwnStructOfTheName", "none", "import \"lib\"\nstruct P { z: int }\nimpl P { fn who(self) -> string { \"main\" } }\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("exporterImportsOtherBeforeItsOwnP", "top", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("unannotatedReturnNoSideImport", "none", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "lib"),
            new ReturnCase("genericStructReturnNoSideImport", "none", "import \"lib\"\nfn main() {\n    imut b = lib.boxed()\n    println(b.value[0].who())\n}\n", "lib"),
            new ReturnCase("genericCallOverAStructReturnNoSideImport", "none", "import \"lib\"\nfn main() { println(lib.id(lib.mkp()).who()) }\n", "lib"),
            new ReturnCase("unannotatedReturnUnrelatedSideImport", "unrelated", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who())\n}\n", "lib"),
            new ReturnCase("annotatedReturnNoSideImport", "none", "import \"lib\"\nfn main() {\n    imut a: Array<P> = lib.mk()\n    println(a[0].who())\n}\n", "lib"),
            new ReturnCase("aFieldOnlyStructOfTheNameImportedFirst", "none", "import \"third\"\nimport \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(toString(a[0].x) + \" \" + a[0].who())\n}\n", "1.5 lib"),
            // Another file adding a method to lib's P clashes with nothing; implementing
            // one lib already has does, since the backends may pick different bodies.
            // An annotated local is 0.9.0's route and is held only to what changed.
            new ReturnCase("aDuplicateImplOfLibsPHere", "none", "import \"lib\"\nimpl Named for P { fn name(self) -> string { \"main-named\" } }\nfn main() {\n    imut a = lib.mk()\n    println(a[0].name())\n}\n", "rejected"),
            new ReturnCase("aMethodAddedToLibsPHere", "none", "import \"lib\"\nimpl P { fn mine(self) -> string { \"main-ext\" } }\nfn main() {\n    imut a = lib.mk()\n    println(a[0].who() + \" \" + a[0].mine())\n}\n", "lib main-ext"),
            new ReturnCase("anAnnotatedLocalBesideADuplicateImplAcrossAnImport", "none", "import \"lib\"\nimpl Named for P { fn name(self) -> string { \"main-named\" } }\nfn main() {\n    imut a: Array<P> = lib.mk()\n    import \"fourth\"\n    println(a[0].who())\n}\n", "lib"),
            // A module imported here found lib's P contested, so it is contested here:
            // xx's zed reached lib's P through mid, and dupmod's refused who is found
            // by name in the interpreter.
            new ReturnCase("aMiddleModuleWhereAnotherPsMethodReachesLibsP", "none", "import \"mid\"\nimport \"lib\"\nfn main() {\n    imut a = lib.mk()\n    println(a[0].zed())\n}\n", "rejected"),
            new ReturnCase("aModuleRedeclaringLibsMethodImportedNested", "none", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    import \"dupmod\"\n    println(a[0].who())\n}\n", "rejected"),
            new ReturnCase("aStructLocalOfItBeforeThatImport", "none", "import \"lib\"\nfn main() {\n    imut a = lib.mk()\n    imut p = a[0]\n    import \"dupmod\"\n    println(p.who())\n}\n", "rejected"),
            // 0.9.0 read these through the replaced P too; unchanged.
            new ReturnCase("zeroNineStructReturn", "side", "import \"lib\"\nfn main() { println(lib.mkp().who()) }\n", "other"),
            new ReturnCase("zeroNineAnnotatedReturn", "side", "import \"lib\"\nfn main() {\n    imut a: Array<P> = lib.mk()\n    println(a[0].who())\n}\n", "other"));

    @TestFactory
    java.util.stream.Stream<DynamicTest> anInferredReturnTypeNeverReadsATypeTheExportersOwnImportReplaced(@TempDir Path dir) {
        // 0.9.0 left lib.mk()'s Array<P> erased, so a[0].who() failed. 0.10.0 names
        // it, and after lib's side() replaced lib's P with other's it ran other's
        // method. It is now erased again in that case, and named otherwise.
        return RETURN_CASES.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            Path project = Files.createDirectories(dir.resolve(c.name()));
            String lib = switch (c.libSide()) {
                case "side" -> RETURN_LIB + "fn side() { import \"other\" }\n";
                case "unrelated" -> RETURN_LIB + "fn side() { import \"fourth\" }\n";
                case "top" -> "import \"other\" as o\n" + RETURN_LIB;
                default -> RETURN_LIB;
            };
            Files.writeString(project.resolve("lib.siyo"), lib);
            Files.writeString(project.resolve("other.siyo"), OTHER_P);
            // A P with no methods; a module declaring no P.
            Files.writeString(project.resolve("third.siyo"), "pub struct P { x: float }\n");
            Files.writeString(project.resolve("fourth.siyo"), "pub struct Q { y: int }\n");
            Files.writeString(project.resolve("xx.siyo"),
                    "pub struct P { y: int }\nimpl P { fn zed(self) -> string { \"xx\" } }\n");
            Files.writeString(project.resolve("mid.siyo"), "import \"xx\"\nimport \"lib\"\npub fn one() -> int { 1 }\n");
            Files.writeString(project.resolve("dupmod.siyo"),
                    "import \"lib\"\nimpl P { fn who(self) -> string { \"dup\" } }\n");
            Files.writeString(project.resolve("main.siyo"), c.main());
            for (String mode : List.of("run", "interpret")) {
                CliParityTest.Result result = CliParityTest.siyoc(project, mode, project.resolve("main.siyo").toString());
                String where = c.name() + " " + mode + ": " + result.stdout() + " / " + result.stderr();
                if (c.expected().equals("rejected")) {
                    assertEquals(1, result.exitCode(), where);
                    assertEquals("", result.stdout(), where);
                    assertTrue(result.stderr().contains("No matching method"), where);
                } else {
                    assertEquals(0, result.exitCode(), where);
                    assertEquals(c.expected(), result.stdout(), where);
                }
            }
        }));
    }

    @Test
    void manyNestedImportsKeepTheOriginCheckBounded(@TempDir Path dir) throws Exception {
        // 1,500 functions, each taking a local of a 2,000-struct chain and then
        // importing a module of its own: 1,500 generations of the type tables. A
        // copy of the tables per generation ran out of memory here.
        StringBuilder lib = new StringBuilder(LIB_HEAD);
        int n = 2000;
        for (int i = 0; i < n; i++) {
            lib.append("pub struct S").append(i).append(" { n: int, k: ")
                    .append(i + 1 < n ? "Array<S" + (i + 1) + ">" : "int").append(" }\n");
        }
        lib.append("pub imut values = g(Box { value: [S0 { n: 1, k: [] }] })\n");
        Files.writeString(dir.resolve("lib.siyo"), lib.toString());
        StringBuilder main = new StringBuilder("import \"lib\"\n");
        int functions = 1500;
        for (int i = 0; i < functions; i++) {
            Files.writeString(dir.resolve("o" + i + ".siyo"), "pub struct Q" + i + " { x: int }\n");
            main.append("fn f").append(i).append("() -> int {\n    imut a = lib.values\n    import \"o").append(i)
                    .append("\"\n    a[0].n\n}\n");
        }
        main.append("fn main() {\n    println(toString(f0() + f").append(functions - 1).append("()))\n}\n");
        Files.writeString(dir.resolve("main.siyo"), main.toString());
        long start = System.nanoTime();
        CliParityTest.Result result = CliParityTest.siyoc(dir, "interpret", dir.resolve("main.siyo").toString());
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        assertEquals("2", result.stdout(), result.stderr());
        assertTrue(seconds < 30, "took " + seconds + " s");
    }

    @Test
    void anOriginCheckStaysFastOnALargeCyclicGraphWithManyAliases(@TempDir Path dir) throws Exception {
        StringBuilder lib = new StringBuilder(LIB_HEAD);
        int n = 500;
        for (int i = 0; i < n; i++) {
            lib.append("pub struct S").append(i).append(" { a: Array<S").append((i + 1) % n).append(">, b: Array<S")
                    .append((i * 7) % n).append(">, v: float }\n");
        }
        lib.append("pub imut values = g(Box { value: [S0 { a: [], b: [], v: 1.5 }] })\n");
        StringBuilder main = new StringBuilder("import \"lib\"\nfn main() {\n    imut a0 = lib.values\n");
        for (int i = 1; i <= 200; i++) main.append("    imut a").append(i).append(" = a").append(i - 1).append("\n");
        main.append("    println(toString(a200[0].v * 2))\n}\n");
        Files.writeString(dir.resolve("lib.siyo"), lib.toString());
        Files.writeString(dir.resolve("main.siyo"), main.toString());
        for (String mode : List.of("run", "interpret")) {
            long start = System.nanoTime();
            CliParityTest.Result result = CliParityTest.siyoc(dir, mode, dir.resolve("main.siyo").toString());
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            assertEquals("3.0", result.stdout(), mode + ": " + result.stderr());
            assertTrue(seconds < 20, mode + " took " + seconds + " s");
        }
    }

    @Test
    void anOriginCheckIsSharedAcrossAliasesOfALongChain(@TempDir Path dir) throws Exception {
        // 3,000 chained structs and 3,000 chained aliases, each read: before aliases
        // shared one check, this ran out of memory.
        StringBuilder lib = new StringBuilder(LIB_HEAD);
        int n = 3000;
        for (int i = 0; i < n; i++) {
            lib.append("pub struct S").append(i).append(" { n: int, k: ")
                    .append(i + 1 < n ? "Array<S" + (i + 1) + ">" : "int").append(" }\n");
        }
        lib.append("pub imut values = g(Box { value: [S0 { n: 1, k: [] }] })\n");
        StringBuilder main = new StringBuilder("import \"lib\"\nfn main() {\n    imut a0 = lib.values\n    mut t = 0\n");
        for (int i = 1; i < 3000; i++) {
            main.append("    imut a").append(i).append(" = a").append(i - 1).append("\n    t = t + a").append(i).append("[0].n\n");
        }
        main.append("    println(toString(t))\n}\n");
        Files.writeString(dir.resolve("lib.siyo"), lib.toString());
        Files.writeString(dir.resolve("main.siyo"), main.toString());
        long start = System.nanoTime();
        // Interpreted only: a main this long passes the JVM's 64 KB method limit compiled.
        CliParityTest.Result result = CliParityTest.siyoc(dir, "interpret", dir.resolve("main.siyo").toString());
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        assertEquals("2999", result.stdout(), result.stderr());
        assertTrue(seconds < 20, "took " + seconds + " s");
    }
}
