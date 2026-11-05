package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gaps found by writing TaskFlow (projects/taskflow), each pinned on both
 * backends. See projects/taskflow/PAIN_POINTS.md for the reproducers.
 */
class TaskFlowRegressionTest {
    private final DogfoodRegressionTest harness = new DogfoodRegressionTest();

    private void assertBoth(String expected, String source, String className) throws Exception {
        assertEquals(expected, harness.run(source, className), "compiled");
        assertEquals(expected, harness.interpret(source, className), "interpreted");
    }

    // --- T1: an empty literal takes its element type from the annotation -------

    @Test
    void anEmptyGenericArrayKnowsItsElementType() throws Exception {
        assertBoth("b", """
                fn main() {
                    mut path: Array<int> = []
                    push(path, 1)
                    imut names = ["a", "b"]
                    println(names[path[0]])
                }
                """, "EmptyGenericInt");
    }

    @Test
    void anEmptyGenericArrayKeepsItsElementStruct() throws Exception {
        assertBoth("a\na", """
                struct T { id: string }
                fn main() {
                    mut ts: Array<T> = []
                    push(ts, T { id: "a" })
                    println(ts[0].id)
                    for t in ts { println(t.id) }
                }
                """, "EmptyGenericStruct");
    }

    @Test
    void anEmptyNestedArrayKeepsItsInnerType() throws Exception {
        assertBoth("8", """
                fn main() {
                    mut rows: Array<Array<int>> = []
                    push(rows, [])
                    push(rows[0], 7)
                    for row in rows { println(toString(row[0] + 1)) }
                }
                """, "EmptyGenericNested");
    }

    // --- T2: pop returns what the array holds ----------------------------------

    @Test
    void popKeepsTheElementType() throws Exception {
        assertBoth("q 2\nk!", """
                fn main() {
                    mut ns: Array<int> = [1]
                    imut xs = ["p", "q"]
                    imut i = pop(ns)
                    mut ms = [1, 1]
                    println(xs[i] + " " + toString(pop(ms) + 1))
                    mut ss = ["k"]
                    println(pop(ss) + "!")
                }
                """, "PopInt");
    }

    @Test
    void popKeepsTheElementStruct() throws Exception {
        assertBoth("a a", """
                struct T { id: string }
                fn main() {
                    mut ts: Array<T> = [T { id: "a" }, T { id: "a" }]
                    imut last = pop(ts)
                    println(last.id + " " + pop(ts).id)
                }
                """, "PopStruct");
    }

    @Test
    void popFromAnArrayOfArraysKeepsTheInnerType() throws Exception {
        assertBoth("3 z", """
                struct T { id: string }
                fn main() {
                    mut rows: Array<Array<int>> = [[1, 2]]
                    imut row = pop(rows)
                    println(toString(row[1] + 1) + " " + zed())
                }
                fn zed() -> string {
                    mut rows: Array<Array<T>> = [[T { id: "z" }]]
                    imut row = pop(rows)
                    row[0].id
                }
                """, "PopNested");
    }

    @Test
    void popOfAnUntypedArrayIsStillErased() throws Exception {
        assertBoth("1 a", """
                fn main() {
                    mut xs: object[] = []
                    push(xs, "a")
                    push(xs, 1)
                    imut n = pop(xs)
                    imut s = pop(xs)
                    println(toString(n) + " " + toString(s))
                }
                """, "PopErased");
    }

    // --- T3: a for loop walks a set --------------------------------------------

    @Test
    void aForLoopWalksASetInInsertionOrder() throws Exception {
        assertBoth("q\na\n2", """
                fn main() {
                    mut s: Set<string> = #{}
                    s.add("q")
                    s.add("a")
                    s.add("q")
                    for k in s { println(k) }
                    println(toString(len(s)))
                }
                """, "ForInSet");
    }

    @Test
    void aForLoopOverASetKnowsItsElementType() throws Exception {
        assertBoth("5\nab!", """
                struct T { id: string }
                fn main() {
                    mut ns: Set<int> = #{4}
                    for n in ns { println(toString(n + 1)) }
                    mut ss: Set<string> = #{"ab"}
                    for s in ss { println(s + "!") }
                }
                """, "ForInSetTyped");
    }

    @Test
    void aForLoopOverAnUntypedSetIsErased() throws Exception {
        assertBoth("1\nx", """
                fn main() {
                    imut s = set()
                    s.add(1)
                    s.add("x")
                    for v in s { println(toString(v)) }
                }
                """, "ForInSetErased");
    }

    @Test
    void aForLoopOverATypedMapKnowsItsKeyType() throws Exception {
        assertBoth("a!\n2", """
                fn main() {
                    mut m: Map<string, int> = {"a": 1}
                    for k in m { println(k + "!") }
                    mut n: Map<int, string> = {1: "x"}
                    for k in n { println(toString(k + 1)) }
                }
                """, "ForInMapKeys");
    }

    // --- T4: break, continue and return leave a block-bodied arm ---------------

    @Test
    void continueAndBreakLeaveAMatchArm() throws Exception {
        assertBoth("1 3", """
                type R = Good(int) | Skip | Stop
                fn main() {
                    mut seen = ""
                    for r in [Good(1), Skip, Good(3), Stop, Good(5)] {
                        match r {
                            Good(v) => { seen = seen + toString(v) + " " },
                            Skip => { continue },
                            Stop => { break }
                        }
                    }
                    println(trim(seen))
                }
                """, "ArmLoopJump");
    }

    @Test
    void returnFromAnArmSkipsTheArmsTail() throws Exception {
        assertBoth("1\nno effect\n2", """
                mut effects = 0
                fn effect() -> int {
                    effects = effects + 1
                    effects
                }
                fn f(n: int) -> int {
                    match n {
                        1 => {
                            if n == 1 { return 1 }
                            effect()
                        },
                        _ => 2
                    }
                }
                fn main() {
                    println(toString(f(1)))
                    println(if effects == 0 { "no effect" } else { "tail ran" })
                    println(toString(f(2)))
                }
                """, "ArmReturnTail");
    }

    @Test
    void anArmJumpsOutOfAValueExpression() throws Exception {
        assertBoth("1 skip 3", """
                fn f(n: int) -> int {
                    imut label = n + match n { 2 => { return -1 }, _ => 0 }
                    label
                }
                fn main() {
                    mut out = ""
                    for n in [1, 2, 3] {
                        imut v = match n { 2 => { out = out + "skip "
                                                  continue }, _ => n }
                        out = out + toString(v) + " "
                    }
                    println(trim(out))
                }
                """, "ArmJumpInValue");
    }

    @Test
    void continueLeavesATryInsideALoop() throws Exception {
        assertBoth("2", """
                fn main() {
                    mut c = 0
                    for x in [1, 2, 3, 4, 5] {
                        try {
                            if x == 2 { continue }
                            if x == 4 { break }
                            c += 1
                        } catch e { c = 100 }
                    }
                    println(toString(c))
                }
                """, "TryLoopJump");
    }

    // --- T5: null comparison of a typed container's slot -----------------------

    @Test
    void aMissingKeyOfATypedMapComparesToNull() throws Exception {
        assertBoth("true false true false true true", """
                fn main() {
                    mut m: Map<string, int> = {"a": 1}
                    mut f: Map<string, float> = {}
                    mut b: Map<string, bool> = {}
                    mut l: Map<string, long> = {}
                    println(toString(m["no"] == null) + " " + toString(m["a"] == null) + " "
                        + toString(m["a"] != null) + " " + toString(m["no"] != null) + " "
                        + toString(f["no"] == null) + " " + toString(null == b["no"] && l["no"] == null))
                }
                """, "MapSlotNull");
    }

    // --- T6: a map literal may be a block's tail value -------------------------

    @Test
    void aMapLiteralMayBeATailValue() throws Exception {
        assertBoth("{a=1, b=x}".replace("=", ": "), """
                fn mk(s: string) -> map { {"a": 1, "b": s} }
                fn main() { println(toString(mk("x"))) }
                """, "MapTail");
    }

    @Test
    void aMapLiteralMayEndAMatchArmAndSpanLines() throws Exception {
        assertBoth("{k: 2}\n{1: 2}", """
                fn mk(n: int) -> map {
                    match n {
                        1 => {
                            imut v = n + 1
                            {
                                "k": v
                            }
                        },
                        _ => {1: 2}
                    }
                }
                fn main() {
                    println(toString(mk(1)))
                    println(toString(mk(2)))
                }
                """, "MapTailArm");
    }

    @Test
    void aBareBlockIsStillABlock() throws Exception {
        assertBoth("2", """
                fn main() {
                    mut n = 1
                    { n = n + 1 }
                    { }
                    println(toString(n))
                }
                """, "BareBlock");
    }

    // --- T7: any value may be interpolated -------------------------------------

    @Test
    void interpolationConvertsAnyValue() throws Exception {
        assertBoth("P { x: 1 } | [1, 2] | {a: 1} | [z] | 3 | 1.5 | true", """
                struct P { x: int }
                fn main() {
                    imut p = P { x: 1 }
                    imut a = [1, 2]
                    imut m = {"a": 1}
                    mut s: Set<string> = #{"z"}
                    imut vs = s.values()
                    println("$p | $a | $m | $vs | ${1 + 2} | ${1.5} | ${1 == 1}")
                }
                """, "InterpolateAny");
    }

    @Test
    void interpolatingASetOrSumValueWorks() throws Exception {
        assertBoth("S true", """
                type T = S | V(int)
                fn main() {
                    imut t = S
                    mut s: Set<int> = #{1}
                    println("$t ${s.has(1)}")
                }
                """, "InterpolateSum");
    }

    // --- T8: a deep recursion can be caught, and on both backends --------------

    @Test
    void aStackOverflowIsCaughtTheSameWayOnBothBackends() throws Exception {
        assertBoth("caught string StackOverflowError", """
                fn deep(n: int) -> int { 1 + deep(n + 1) }
                fn main() {
                    try {
                        println(toString(deep(0)))
                    } catch e {
                        println("caught " + typeOf(e) + " " + toString(e))
                    }
                }
                """, "OverflowCatch");
    }

    // --- T9: json.parseValue reads any JSON document ---------------------------

    private static final String JSON_PROBE = """
            import "std/json"
            fn show(s: string) {
                match json.parseValue(s) {
                    Document(v) => println(typeOf(v) + " " + json.stringify(v)),
                    Malformed(why) => println("bad: " + why)
                }
            }
            """;

    @Test
    void parseValueReadsAnyDocument() throws Exception {
        assertBoth("array [1,\"a\",null]\nmap {\"a\":[]}\nint 7\nstring \"s\"\nbool true\nnull null", JSON_PROBE + """
                fn main() {
                    show("[1, \\"a\\", null]")
                    show("{\\"a\\": []}")
                    show(" 7 ")
                    show("\\"s\\"")
                    show("true")
                    show("null")
                }
                """, "JsonValue");
    }

    @Test
    void parseStillWantsAnObject() throws Exception {
        assertBoth("bad", """
                import "std/json"
                fn main() {
                    match json.parse("[1]") {
                        Parsed(m) => println("ok"),
                        Invalid(why) => println("bad")
                    }
                }
                """, "JsonParseObject");
    }

    // --- T10: the JSON parser rejects what RFC 8259 rejects --------------------

    private void assertMalformed(String expectedFragment, String json, String className) throws Exception {
        String source = JSON_PROBE + "fn main() { show(\"" + json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\") }\n";
        String compiled = harness.run(source, className);
        String interpreted = harness.interpret(source, className);
        assertEquals(compiled, interpreted, "backends differ");
        org.junit.jupiter.api.Assertions.assertTrue(compiled.startsWith("bad: ") && compiled.contains(expectedFragment),
                json + " gave: " + compiled);
    }

    @Test
    void aLeadingZeroIsRejected() throws Exception {
        assertMalformed("leading zero", "{\"a\": 01}", "JsonLeadingZero");
        assertMalformed("leading zero", "[-012]", "JsonLeadingZeroNeg");
    }

    @Test
    void aDigitMustBeAnAsciiDigit() throws Exception {
        assertMalformed("expected a value", "[\u0663]", "JsonArabicDigit");
    }

    @Test
    void aRawControlCharacterInAStringIsRejected() throws Exception {
        assertMalformed("control character", "[\"a\\tb\"]".replace("\\t", "\t"), "JsonRawTab");
    }

    @Test
    void aDuplicateKeyIsRejected() throws Exception {
        assertMalformed("duplicate key \"a\"", "{\"a\": 1, \"a\": 2}", "JsonDuplicateKey");
    }

    @Test
    void aBadUnicodeEscapeIsRejected() throws Exception {
        assertMalformed("bad unicode escape", "[\"\\u+123\"]", "JsonBadEscape");
    }

    @Test
    void aMalformedDocumentNamesItsLineAndColumn() throws Exception {
        assertMalformed("line 2, column 3", "[1,\n  x]", "JsonLineColumn");
    }

    @Test
    void anIntegerTooLongForALongIsAFloat() throws Exception {
        assertBoth("float true\nlong 99999999999", JSON_PROBE + """
                fn main() {
                    match json.parseValue("123456789012345678901234567890") {
                        Document(v) => println(typeOf(v) + " " + toString(v == v)),
                        Malformed(why) => println(why)
                    }
                    show("99999999999")
                }
                """, "JsonHugeInt");
    }

    @Test
    void aByteOrderMarkIsIgnored() throws Exception {
        assertBoth("map {\"a\":1}\nok", JSON_PROBE + """
                fn main() {
                    show(chr(65279) + "{\\"a\\": 1}")
                    match json.parse(chr(65279) + "{}") {
                        Parsed(m) => println("ok"),
                        Invalid(why) => println(why)
                    }
                }
                """, "JsonBom");
    }

    @Test
    void aDocumentNestedTooDeeplyIsMalformedNotAnOverflow() throws Exception {
        assertBoth("bad: json: nested too deeply\nbad: json: nested too deeply", JSON_PROBE + """
                import "std/strings"
                fn trimmed(s: string) -> string { s }
                fn check(s: string) {
                    match json.parseValue(s) {
                        Document(v) => println("ok"),
                        Malformed(why) => println("bad: " + substring(why, 0, indexOf(why, " at position")))
                    }
                }
                fn main() {
                    check(strings.repeat("[", 200000))
                    check(strings.repeat("{\\"a\\":", 200000))
                }
                """, "JsonDeep");
    }

    @Test
    void aReasonablyDeepDocumentStillParses() throws Exception {
        assertBoth("ok", """
                import "std/json"
                import "std/strings"
                fn main() {
                    match json.parseValue(strings.repeat("[", 300) + strings.repeat("]", 300)) {
                        Document(v) => println("ok"),
                        Malformed(why) => println(why)
                    }
                }
                """, "JsonDeepEnough");
    }

    // --- T11: json.stringify writes structs and sets as data -------------------

    @Test
    void stringifyWritesAStructByItsFields() throws Exception {
        assertBoth("{\"id\":\"a\",\"deps\":[\"b\"],\"inner\":{\"n\":1}}\n[{\"n\":2}]\n[3,4]", """
                import "std/json"
                struct I { n: int }
                struct T { id: string, deps: Array<string>, inner: I }
                fn main() {
                    println(json.stringify(T { id: "a", deps: ["b"], inner: I { n: 1 } }))
                    println(json.stringify([I { n: 2 }]))
                    mut s: Set<int> = #{3}
                    s.add(4)
                    println(json.stringify(s))
                }
                """, "JsonStruct");
    }

    // --- T12: strings.isInt, and joining without a quadratic cost --------------

    @Test
    void isIntTellsWhatParseIntCanRead() throws Exception {
        assertBoth("true true true false false false false false false", """
                import "std/strings"
                fn main() {
                    mut out: string[] = []
                    for s in ["12", "-7", "2147483647", "2147483648", "12x", "", " 1", "1.5", "-"] {
                        push(out, toString(strings.isInt(s)))
                    }
                    println(strings.join(out, " "))
                }
                """, "IsInt");
    }

    @Test
    void readFileKeepsLeadingBlankLines() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("tf", ".txt");
        try {
            java.nio.file.Files.writeString(file, "\n\nab\r\nc\n");
            String path = file.toString().replace("\\", "\\\\");
            assertBoth("[, , ab, c]", """
                    import "std/io"
                    fn main() { println(toString(split(io.readFile("%s"), "\\n"))) }
                    """.formatted(path), "ReadFileBlank");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void readingAndJoiningALargeTextIsNotQuadratic() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("tf", ".txt");
        try {
            String path = file.toString().replace("\\", "\\\\");
            String source = """
                    import "std/io"
                    import "std/strings"
                    fn main() {
                        mut lines: string[] = []
                        for mut i = 0 i < 300000 i = i + 1 { push(lines, "line number " + toString(i)) }
                        io.writeFile("%s", strings.join(lines, "\\n"))
                        println(toString(len(split(io.readFile("%s"), "\\n"))))
                        println(toString(len(strings.repeat("xy", 1000000))))
                    }
                    """.formatted(path, path);
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(60),
                    () -> assertBoth("300000\n2000000", source, "LargeText"));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    // --- T13: else if is a value, and void arms stay void ----------------------

    @Test
    void anElseIfChainIsAValue() throws Exception {
        assertBoth("onetwomany\n50", """
                fn pick(d: int) -> string {
                    imut x = if d == 1 { "one" } else if d == 2 { "two" } else { "many" }
                    x
                }
                fn main() {
                    println(pick(1) + pick(2) + pick(3))
                    imut y = 5
                    println(toString(if y == 1 { 10 } else if y == 5 { 50 } else { 60 }))
                }
                """, "ElseIfValue");
    }

    @Test
    void aVoidElseIfChainMayEndAMatchArm() throws Exception {
        assertBoth("1\n2\n3\n0", """
                type K = A(int) | B
                fn f(n: int) { println(toString(n)) }
                fn g(k: K) {
                    match k {
                        B => f(0),
                        A(d) => {
                            if d == 1 { f(1) } else if d == 2 { f(2) } else { f(3) }
                        }
                    }
                }
                fn main() {
                    g(A(1))
                    g(A(2))
                    g(A(3))
                    g(B)
                }
                """, "VoidElseIfArm");
    }

    @Test
    void anIfWhoseArmsAreVoidAndValueRunsForEffects() throws Exception {
        assertBoth("1\n1\n7\n5", """
                fn f(n: int) { println(toString(n)) }
                fn g(n: int) -> int { n }
                fn h(d: int) {
                    match d { 1 => { if d == 1 { f(1) } else { g(1) } }, _ => { if d == 2 { g(2) } else { f(3) } } }
                    match d { 1 => { if d == 1 { f(1) } else if d == 3 { f(7) } }, _ => { } }
                }
                fn main() {
                    h(1)
                    f(7)
                    imut z = match 1 { 1 => { if true { 5 } else if false { 6 } else { throw "x" } }, _ => 9 }
                    println(toString(z))
                }
                """, "VoidValueArms");
    }

    // --- T14: a declared number widens wherever it is stored -------------------

    @Test
    void aStoredNumberWidensToTheDeclaredType() throws Exception {
        assertBoth("1\n2.0\n6\nfloat\n2\n2\n3\n3000000000", """
                struct P { r: long, f: float, n: int }
                fn main() {
                    imut p = P { r: 0, f: 1, n: 2 }
                    println(toString(p.r + 1))
                    println(toString(p.f + 1))
                    p.r = 5
                    p.f = 2
                    println(toString(p.r + 1))
                    println(typeOf(p.f))
                    mut m: Map<string, long> = {}
                    m["a"] = 1
                    println(toString(m["a"] + 1))
                    mut xs: Array<long> = []
                    push(xs, 1)
                    println(toString(xs[0] + 1))
                    mut b: Map<int, long> = {}
                    mut h: long = 0
                    b[0] = h + 3
                    h = b[0]
                    println(toString(h))
                    p.r = 1000000000
                    p.r += 2000000000
                    println(toString(p.r))
                }
                """, "StoredWidening");
    }

    // --- T15: a call's declared return type reaches what is done with it -------

    @Test
    void aVariableKeepsTheGenericTypeOfTheCallItCameFrom() throws Exception {
        assertBoth("a!\n3\nz!", """
                struct T { id: string }
                fn sets() -> Map<int, Set<string>> {
                    mut m: Map<int, Set<string>> = {}
                    m[1] = #{"a"}
                    m
                }
                fn rows() -> Map<string, Array<int>> { {"r": [2]} }
                fn nested() -> Array<Array<T>> { [[T { id: "z" }]] }
                fn main() {
                    imut s = sets()
                    for name in s[1] { println(name + "!") }
                    imut r = rows()
                    println(toString(r["r"][0] + 1))
                    imut n = nested()
                    println(n[0][0].id + "!")
                }
                """, "CallDeclaredType");
    }

    @Test
    void aLoopVariableKeepsTheGenericTypeItWasWalkedFrom() throws Exception {
        assertBoth("a!\n2\nb\n1!", """
                struct T { id: string }
                struct Plan { batches: Array<Array<T>> }
                fn main() {
                    imut p = Plan { batches: [[T { id: "a" }]] }
                    for batch in p.batches { for t in batch { println(t.id + "!") } }
                    mut rows: Array<Array<int>> = [[1]]
                    for row in rows { println(toString(row[0] + 1)) }
                    mut byName: Map<string, Array<T>> = {"k": [T { id: "b" }]}
                    for k in byName { for t in byName[k] { println(t.id) } }
                    mut sets: Array<Set<string>> = [#{"1"}]
                    for s in sets { for v in s { println(v + "!") } }
                }
                """, "LoopVariableGeneric");
    }

    @Test
    void aForLoopWalksAnErasedValueByWhatItIs() throws Exception {
        assertBoth("7\nk\n1\ndone", """
                fn walk(v: object) {
                    for x in v { println(toString(x)) }
                }
                fn main() {
                    walk(#{7})
                    walk({"k": 1})
                    walk([1])
                    println("done")
                }
                """, "ForInErased");
    }

    // --- T16: a recursive call has locals of its own, interpreted --------------

    @Test
    void aRecursiveCallDoesNotDisturbItsCallersLoop() throws Exception {
        assertBoth("0 sees 1\n0 sees 2", """
                fn walk(n: int, kids: Array<Array<int>>) {
                    for w in kids[n] {
                        println(toString(n) + " sees " + toString(w))
                        if w > 0 { walk(w, kids) }
                    }
                }
                fn main() { walk(0, [[1, 2], [], []]) }
                """, "RecursionLoop");
    }

    @Test
    void aRecursiveCallDoesNotDisturbItsCallersLocals() throws Exception {
        assertBoth("120", """
                fn fact(n: int) -> int {
                    imut before = n
                    if n == 0 { return 1 }
                    imut below = fact(n - 1)
                    before * below
                }
                fn main() { println(toString(fact(5))) }
                """, "RecursionLocals");
    }

    @Test
    void aRecursiveCallDoesNotDisturbBindingsOrCatchVariables() throws Exception {
        assertBoth("3 2 1\nouter", """
                type L = Cons(int, L) | End
                fn sum(l: L) -> string {
                    match l {
                        End => "",
                        Cons(head, tail) => {
                            imut rest = sum(tail)
                            trim(toString(head) + " " + rest)
                        }
                    }
                }
                fn nested(n: int) -> string {
                    try {
                        throw "outer"
                    } catch e {
                        if n > 0 { nested(n - 1) }
                        toString(e)
                    }
                }
                fn main() {
                    println(sum(Cons(3, Cons(2, Cons(1, End)))))
                    println(nested(2))
                }
                """, "RecursionBindings");
    }

    @Test
    void aGlobalIsStillSharedAndAClosureStillCaptures() throws Exception {
        assertBoth("2\n7\n9", """
                mut count = 0
                fn bump() { count = count + 1 }
                fn counter() -> fn() -> int {
                    mut n = 6
                    fn() -> int {
                        n = n + 1
                        n
                    }
                }
                fn main() {
                    bump()
                    bump()
                    println(toString(count))
                    imut c = counter()
                    println(toString(c()))
                    imut k = 9
                    imut f = fn() -> int { k }
                    println(toString(f()))
                }
                """, "GlobalsAndCaptures");
    }

    // --- T17: a declared long is a long from its initializer on ----------------

    @Test
    void aDeclaredLongVariableDoesNotWrapAtAnInt() throws Exception {
        assertBoth("3000000000\nlong\nfloat", """
                fn main() {
                    mut h: long = 0
                    for i in [1000000000, 1000000000, 1000000000] { h += i }
                    println(toString(h))
                    imut l: long = 1
                    println(typeOf(l))
                    imut f: float = 1
                    println(typeOf(f))
                }
                """, "DeclaredLong");
    }

    // --- T18: review regressions: closures, values, void --------------------------

    private void assertRejected(String fragment, String source, String className) throws Exception {
        String compiled = compileFailure(source, className);
        org.junit.jupiter.api.Assertions.assertTrue(compiled.contains(fragment), "compiled: " + compiled);
        String interpreted = interpretFailure(source, className);
        org.junit.jupiter.api.Assertions.assertTrue(interpreted.contains(fragment), "interpreted: " + interpreted);
    }

    private String compileFailure(String source, String className) throws Exception {
        try {
            return "ran: " + harness.run(source, className);
        } catch (AssertionError expected) {
            return expected.getMessage();
        }
    }

    private String interpretFailure(String source, String className) throws Exception {
        try {
            return "ran: " + harness.interpret(source, className);
        } catch (AssertionError expected) {
            return expected.getMessage();
        }
    }

    @Test
    void aNestedClosureReadsAVariableItsParentNeverNamed() throws Exception {
        String source = """
                fn f() -> int {
                    imut a = 1
                    imut o = fn() -> int {
                        imut i = fn() -> int { return a }
                        return i()
                    }
                    return o()
                }
                fn main() { println(toString(f())) }
                """;
        assertEquals("1", harness.interpret(source, "NestedClosureRead"));
    }

    @Test
    void aNestedClosureThatOutlivesItsFunctionKeepsItsCapture() throws Exception {
        String source = """
                fn make() -> fn() -> int {
                    imut a = 41
                    fn() -> int {
                        imut i = fn() -> int { return a + 1 }
                        return i()
                    }
                }
                fn main() {
                    imut f = make()
                    println(toString(f()))
                }
                """;
        assertEquals("42", harness.interpret(source, "NestedClosureEscape"));
    }

    @Test
    void aNestedClosureWritesThroughToTheOuterVariable() throws Exception {
        String source = """
                fn f() -> int {
                    mut n = 1
                    imut o = fn() {
                        imut i = fn() { n = n + 1 }
                        i()
                    }
                    o()
                    n
                }
                fn main() { println(toString(f())) }
                """;
        assertEquals("2", harness.interpret(source, "NestedClosureWrite"));
    }

    @Test
    void aFunctionDeclaredInsideAFunctionReadsItsLocals() throws Exception {
        String source = """
                fn f() -> int {
                    imut a = 1
                    fn inner() -> int { a + 1 }
                    inner()
                }
                fn main() { println(toString(f())) }
                """;
        assertEquals("2", harness.interpret(source, "NestedFunctionRead"));
    }

    @Test
    void aTailArmThatOnlyLoopsFallsThroughLikeAnyOtherPath() throws Exception {
        assertBoth("1\n2", """
                fn c(x: int) -> int { match x { 1 => { while true { return 1 } } _ => { return 2 } } }
                fn main() {
                    println(toString(c(1)))
                    println(toString(c(2)))
                }
                """, "TailArmLoop");
    }

    @Test
    void anIfArmMayReturnBesideAValue() throws Exception {
        assertBoth("1\n2", """
                fn f(a: bool) -> int { if a { 1 } else { return 2 } }
                fn main() {
                    println(toString(f(true)))
                    println(toString(f(false)))
                }
                """, "IfArmReturn");
    }

    @Test
    void aLiteralWidensToItsDeclaredElementType() throws Exception {
        assertBoth("2.0\n2\n3.0\nfloat\nlong", """
                fn g() -> float[] { return [1, 2] }
                fn h() -> Map<string, long> { {"a": 1} }
                fn main() {
                    imut a = g()
                    println(toString(pop(a)))
                    imut m = h()
                    println(toString(m["a"] + 1))
                    imut d: Array<Array<float>> = [[1, 3]]
                    println(toString(d[0][1]))
                    println(typeOf(d[0][0]))
                    imut l: Map<string, long> = {"x": 1}
                    println(typeOf(l["x"]))
                }
                """, "LiteralWidening");
    }

    @Test
    void aSetOfFloatsAcceptsAnIntAndReadsAFloat() throws Exception {
        assertBoth("2.0", """
                fn main() {
                    imut s: Set<float> = set()
                    s.add(1)
                    for v in s { println(toString(v + 1.0)) }
                }
                """, "SetFloatRead");
    }

    @Test
    void stringifyLeavesAnActorAsItsName() throws Exception {
        assertBoth("\"actor<A>\"", """
                import "std/json"
                actor struct A { n: int }
                impl A { fn new() -> A { A { n: 3 } } }
                fn main() {
                    imut a = spawn A.new()
                    println(json.stringify(a))
                }
                """, "StringifyActor");
    }

    @Test
    void anExpressionWithNoValueIsRejectedWhereOneIsNeeded() throws Exception {
        assertRejected("no value", "fn main() { imut r = if true { imut a = 1 } else { imut b = 2 } }", "VoidInit");
        assertRejected("no value", "fn main() { println(if true { imut a = 1 } else { imut b = 2 }) }", "VoidArgument");
        assertRejected("no value", "fn main() { println(toString(match 1 { 1 => { imut q = 1 } _ => { imut r = 1 } })) }", "VoidMatch");
        assertRejected("no value", "fn v() { }\nfn main() { println(\"v=${v()}\") }", "VoidInterpolated");
    }

    @Test
    void aValueBesideAnArmThatFallsOffTheEndIsRejected() throws Exception {
        assertRejected("cannot mix void and value arms",
                "fn a(x: int) -> map { if x == 1 { {\"k\": 1} } else { {} } }\nfn main() { println(toString(a(1))) }", "MixedMapTail");
    }

    @Test
    void anIfThatReturnsInOneArmMayFallThroughInTheOther() throws Exception {
        assertBoth("1\n0", """
                fn f(a: bool) -> int {
                    if a { return 1 } else { println("0") }
                    return 0
                }
                fn main() {
                    println(toString(f(true)))
                    f(false)
                }
                """, "ReturnOrFallThrough");
    }

    @Test
    void breakCannotLeaveALambdaOrASpawn() throws Exception {
        assertRejected("not allowed outside", """
                fn main() {
                    for x in [1] {
                        imut f = fn() { break }
                        f()
                    }
                }
                """, "BreakInLambda");
        assertRejected("not allowed outside", """
                fn main() {
                    for x in [1] {
                        scope { spawn { continue } }
                    }
                }
                """, "ContinueInSpawn");
    }
}
