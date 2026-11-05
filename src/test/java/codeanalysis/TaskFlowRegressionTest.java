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

    // --- T19: second review --------------------------------------------------------

    @Test
    void aFloatReadAtADeclaredLongIsAnErrorNotATruncation() throws Exception {
        // Compiled only: the interpreter holds the float, as it did in 0.8.0 (D15).
        String source = """
                fn main() {
                    imut o: object = 1.5
                    imut r = try {
                        imut a: long = o
                        "read " + toString(a)
                    } catch e { "refused" }
                    println(r)
                }
                """;
        assertEquals("refused", harness.run(source, "LongFromFloat"));
    }

    @Test
    void anIfWhoseArmsBothLeaveMayInitialiseAVariable() throws Exception {
        assertBoth("3\n4\ncaught", """
                fn f(k: int) -> int {
                    imut v = if k > 0 { return 3 } else { return 4 }
                    v
                }
                fn g(k: int) -> int {
                    imut v = if k > 0 { throw "up" } else { throw "down" }
                    v
                }
                fn main() {
                    println(toString(f(1)))
                    println(toString(f(0)))
                    imut r = try { toString(g(1)) } catch e { "caught" }
                    println(r)
                }
                """, "BothArmsLeave");
    }

    @Test
    void aLiteralArgumentOrFieldWidensToItsDeclaredElementType() throws Exception {
        assertBoth("0.5\n0.25", """
                struct S { xs: Array<float> }
                fn p(a: float[]) -> float { a[0] / a[1] }
                fn main() {
                    println(toString(p([1, 2])))
                    imut s = S { xs: [1, 4] }
                    println(toString(s.xs[0] / s.xs[1]))
                }
                """, "LiteralArgumentWidening");
    }

    @Test
    void numbersCompareByValue() throws Exception {
        assertBoth("true false true true", """
                import "std/math"
                fn main() {
                    imut nan = 0.0 / 0.0
                    imut zero = 0.0
                    imut neg = -0.0
                    println(toString(5L == 5) + " " + toString(nan == nan) + " "
                        + toString(zero == neg) + " " + toString(5 != 6L))
                }
                """, "NumericEquality");
    }

    @Test
    void aTryTailWithAValueBesideAVoidCatchIsRejected() throws Exception {
        assertRejected("cannot mix void and value arms", """
                fn se() -> int { 1 }
                fn f() -> int { try { se() } catch e { println("x") } }
                fn main() { println(toString(f())) }
                """, "TryMixedTail");
    }

    @Test
    void anArgumentWithNoValueIsReportedOnce() throws Exception {
        assertRejected("no value", """
                fn v() { }
                fn takes(n: int) -> int { n }
                fn main() { println(toString(takes(v()))) }
                """, "VoidArgumentOnce");
    }

    // --- T20: a named function nested in a function captures lexically ------------
    //
    // Interpreted only: compiled, a nested fn reading its parent's locals is still
    // an internal compiler error (PAIN_POINTS.md D10).

    private void assertInterpreted(String expected, String source, String className) throws Exception {
        assertEquals(expected, harness.interpret(source, className));
    }

    @Test
    void anEscapedNamedFunctionKeepsAnImmutableCapture() throws Exception {
        assertInterpreted("41\n42", """
                fn make() -> fn() -> int {
                    imut x = 41
                    fn inner() -> int { x }
                    inner
                }
                fn makeSum() -> fn() -> int {
                    imut x = 41
                    fn inner() -> int { x + 1 }
                    inner
                }
                fn main() {
                    imut f = make()
                    println(toString(f()))
                    imut g = makeSum()
                    println(toString(g()))
                }
                """, "EscapedNamedImmutable");
    }

    @Test
    void anEscapedNamedFunctionSharesAMutableCapture() throws Exception {
        assertInterpreted("1\n2\n1", """
                fn make() -> fn() -> int {
                    mut n = 0
                    fn bump() -> int {
                        n = n + 1
                        n
                    }
                    bump
                }
                fn main() {
                    imut a = make()
                    imut b = make()
                    println(toString(a()))
                    println(toString(a()))
                    println(toString(b()))
                }
                """, "EscapedNamedMutable");
    }

    @Test
    void aNamedFunctionAndItsParentSeeTheSameVariable() throws Exception {
        assertInterpreted("2\n12\n13", """
                fn run() {
                    mut n = 1
                    fn bump() -> int {
                        n = n + 1
                        n
                    }
                    imut escaped = bump
                    println(toString(bump()))
                    n = 11
                    println(toString(escaped()))
                    println(toString(bump()))
                }
                fn main() { run() }
                """, "NamedSharesWithParent");
    }

    @Test
    void anEscapedNamedFunctionKeepsTheFrameItWasMadeIn() throws Exception {
        // Called while another activation of the parent is on the stack, the
        // function reads its own x, not the nearest one.
        assertInterpreted("1 2", """
                fn make(x: int, other: fn() -> int) -> string {
                    fn read() -> int { x }
                    if x == 2 { return toString(other()) + " " + toString(read()) }
                    return make(2, read)
                }
                fn nothing() -> int { 0 }
                fn main() { println(make(1, nothing)) }
                """, "NamedLexicalNotDynamic");
    }

    @Test
    void aParameterIsCapturedLikeALocal() throws Exception {
        assertInterpreted("8\n9\n10", """
                fn adder(mut base: int) -> fn(int) -> int {
                    fn add(n: int) -> int {
                        base = base + 1
                        base + n
                    }
                    add
                }
                fn main() {
                    imut f = adder(5)
                    println(toString(f(2)))
                    println(toString(f(2)))
                    println(toString(f(2)))
                }
                """, "NamedCapturesParameter");
    }

    @Test
    void aNamedFunctionCarriesWhatItsSiblingsAndChildrenNeed() throws Exception {
        assertInterpreted("7\n7\n7", """
                fn viaSibling() -> fn() -> int {
                    imut x = 7
                    fn read() -> int { x }
                    fn viaRead() -> int { read() }
                    viaRead
                }
                fn viaChild() -> fn() -> int {
                    imut x = 7
                    fn outer() -> int {
                        fn inner() -> int { x }
                        inner()
                    }
                    outer
                }
                fn viaLambda() -> fn() -> int {
                    imut x = 7
                    fn read() -> int { x }
                    fn() -> int { read() }
                }
                fn main() {
                    imut a = viaSibling()
                    println(toString(a()))
                    imut b = viaChild()
                    println(toString(b()))
                    imut c = viaLambda()
                    println(toString(c()))
                }
                """, "NamedTransitive");
    }

    @Test
    void aNamedFunctionMayRecurseAfterEscaping() throws Exception {
        assertInterpreted("6", """
                fn make() -> fn(int) -> int {
                    imut step = 1
                    fn sum(n: int) -> int {
                        if n == 0 { return 0 }
                        n + sum(n - step)
                    }
                    sum
                }
                fn main() {
                    imut f = make()
                    println(toString(f(3)))
                }
                """, "NamedRecursion");
    }

    @Test
    void aNamedFunctionsOwnLocalIsNotACapture() throws Exception {
        // Its x shadows the parent's, in blocks, match arms and catch clauses too.
        assertInterpreted("5 9 3", """
                type K = A(int)
                fn make() -> fn() -> string {
                    imut x = 1
                    imut e = 2
                    imut v = 3
                    fn inner() -> string {
                        imut x = 5
                        imut caught = try { throw 9 } catch e { e }
                        imut bound = match A(3) { A(v) => v }
                        toString(x) + " " + toString(caught) + " " + toString(bound)
                    }
                    inner
                }
                fn main() {
                    imut f = make()
                    println(f())
                }
                """, "NamedOwnLocals");
    }

    @Test
    void aSpawnMayCallANamedFunctionThatCaptures() throws Exception {
        assertInterpreted("4", """
                fn run() {
                    imut x = 4
                    fn read() -> int { x }
                    scope { spawn { println(toString(read())) } }
                }
                fn main() { run() }
                """, "NamedInSpawn");
    }

    @Test
    void aNamedFunctionThatCapturesNothingStillRunsOnBothBackends() throws Exception {
        assertBoth("3\n8", """
                imut G = 5
                fn f() -> int {
                    fn inner(n: int) -> int { n + 1 }
                    inner(2)
                }
                fn pick() -> fn() -> int {
                    fn read() -> int { G + 3 }
                    read
                }
                fn main() {
                    println(toString(f()))
                    imut p = pick()
                    println(toString(p()))
                }
                """, "NamedNoCaptures");
    }

    @Test
    void nestedFunctionsOfOneNameAreSeparateFunctions() throws Exception {
        assertBoth("1 2 10 20 1", """
                fn helper() -> int { 1 }
                fn a() -> int {
                    fn helper() -> int { 2 }
                    helper()
                }
                fn b() -> int {
                    fn k() -> int { 10 }
                    k()
                }
                fn c() -> int {
                    fn k() -> int { 20 }
                    k()
                }
                fn main() {
                    println(toString(helper()) + " " + toString(a()) + " " + toString(b()) + " "
                        + toString(c()) + " " + toString(helper()))
                }
                """, "NestedSameNames");
    }

    @Test
    void anOverloadedNestedFunctionPicksByArgumentCount() throws Exception {
        assertBoth("1 3", """
                fn f() -> string {
                    fn g(a: int) -> int { a }
                    fn g(a: int, b: int) -> int { a + b }
                    toString(g(1)) + " " + toString(g(1, 2))
                }
                fn main() { println(f()) }
                """, "NestedOverload");
    }

    @Test
    void aNestedFunctionDeclaredTwiceInOneBlockIsRejected() throws Exception {
        assertRejected("already", """
                fn f() -> int {
                    fn g() -> int { 1 }
                    fn g() -> int { 2 }
                    g()
                }
                fn main() { println(toString(f())) }
                """, "NestedDuplicate");
    }

    @Test
    void aNestedFunctionMayBeDeclaredInAnyBlock() throws Exception {
        assertBoth("3\n4\n5\n6\n7\n8", """
                fn main() {
                    if true {
                        fn a() -> int { 3 }
                        println(toString(a()))
                    }
                    for i in [1] {
                        fn a() -> int { 4 }
                        println(toString(a()))
                    }
                    mut n = 0
                    while n < 1 {
                        fn a() -> int { 5 }
                        println(toString(a()))
                        n += 1
                    }
                    {
                        fn a() -> int { 6 }
                        println(toString(a()))
                    }
                    try {
                        fn a() -> int { 7 }
                        println(toString(a()))
                    } catch e { println("no") }
                    imut viaLambda = fn() -> int {
                        fn a() -> int { 8 }
                        a()
                    }
                    println(toString(viaLambda()))
                }
                """, "NestedInAnyBlock");
    }

    @Test
    void functionsInABlockMayCallEachOtherAheadOfDeclaration() throws Exception {
        assertBoth("9", """
                fn main() {
                    if true {
                        fn a() -> int { b() }
                        fn b() -> int { 9 }
                        println(toString(a()))
                    }
                }
                """, "NestedForwardCall");
    }

    @Test
    void aNestedFunctionNamedLambdaDoesNotCollideWithAClosure() throws Exception {
        assertBoth("8 7", """
                fn main() {
                    fn lambda() -> int { 8 }
                    imut f = fn() -> int { 7 }
                    println(toString(lambda()) + " " + toString(f()))
                }
                """, "NestedNamedLambda");
    }

    @Test
    void aCaptureMayBeReadOnlyInsideAnyForm() throws Exception {
        assertInterpreted("8\n8\n8\n8\n8\n8", """
                type K = A(int)
                fn inTry() -> fn() -> int {
                    imut x = 8
                    fn k() -> int { try { return x } catch e { return 0 } }
                    k
                }
                fn inCatch() -> fn() -> int {
                    imut x = 8
                    fn k() -> int { try { throw 1 } catch e { return x } }
                    k
                }
                fn inFor() -> fn() -> int {
                    imut x = 8
                    fn k() -> int {
                        mut t = 0
                        for mut i = 0 i < x i = i + 1 { t = t + 1 }
                        t
                    }
                    k
                }
                fn inMatch() -> fn() -> int {
                    imut x = 8
                    fn k() -> int { match A(1) { A(v) => { x } } }
                    k
                }
                fn inIf() -> fn() -> int {
                    imut x = 8
                    fn k() -> int { if x > 0 { return x } else { return 0 } }
                    k
                }
                fn inScope() -> fn() -> int {
                    imut x = 8
                    fn k() -> int {
                        mut seen = 0
                        scope { seen = x }
                        seen
                    }
                    k
                }
                fn main() {
                    for f in [inTry(), inCatch(), inFor(), inMatch(), inIf(), inScope()] { println(toString(f())) }
                }
                """, "CaptureInAnyForm");
    }

    @Test
    void aLambdaInsideAFunctionThatItCallsSeesTheFunctionsCaptures() throws Exception {
        assertInterpreted("6", """
                fn make() -> fn(int) -> int {
                    imut step = 1
                    fn sum(n: int) -> int {
                        if n == 0 { return 0 }
                        imut again = fn() -> int { sum(n - step) }
                        n + again()
                    }
                    sum
                }
                fn main() {
                    imut f = make()
                    println(toString(f(3)))
                }
                """, "LambdaCallsEnclosingFunction");
    }

    @Test
    void aSameNamedLocalDoesNotHideACaptureOfACalledFunction() throws Exception {
        assertInterpreted("104\n51", """
                fn viaLambda() -> fn() -> int {
                    imut x = 5
                    fn k() -> int { x }
                    fn() -> int {
                        imut x = 99
                        k() + x
                    }
                }
                fn viaFunction() -> fn() -> int {
                    imut x = 1
                    fn k() -> int { x }
                    fn m() -> fn() -> int {
                        imut x = 50
                        fn n() -> int { x + k() }
                        n
                    }
                    m()
                }
                fn main() {
                    imut a = viaLambda()
                    println(toString(a()))
                    imut b = viaFunction()
                    println(toString(b()))
                }
                """, "SameNamedLocalHides");
    }

    @Test
    void compiledANestedFunctionUsingItsParentsLocalsIsReportedNotMiscompiled() throws Exception {
        // It used to read a global of the same name silently when one existed.
        String source = """
                imut x = 1
                fn make() -> fn() -> int {
                    imut x = 2
                    fn k() -> int { x }
                    k
                }
                fn main() {
                    imut f = make()
                    println(toString(f()))
                }
                """;
        assertEquals("2", harness.interpret(source, "NestedGlobalShadow"));
        String compiled = compileFailure(source, "NestedGlobalShadow");
        org.junit.jupiter.api.Assertions.assertTrue(compiled.contains("from the function around it"), compiled);
    }

    @Test
    void aGlobalIsNotCapturedByANestedFunction() throws Exception {
        // Compiled too: it reads no parent local. A write must reach the global.
        assertInterpreted("1\n2\n3", """
                mut g = 0
                fn make() -> fn() -> int {
                    fn bump() -> int {
                        g = g + 1
                        g
                    }
                    bump
                }
                fn main() {
                    imut f = make()
                    println(toString(f()))
                    println(toString(f()))
                    g = g + 1
                    println(toString(g))
                }
                """, "GlobalNotCaptured");
    }

    @Test
    void aLambdaMayDeclareLocalsInsideTryAndScope() throws Exception {
        assertBoth("1\n7\n2\n9", """
                type K = A(int)
                fn main() {
                    imut l = fn() -> int {
                        try {
                            imut y = 1
                            return y
                        } catch e { return 0 }
                    }
                    println(toString(l()))
                    imut c = fn() -> int {
                        try { throw 7 } catch e { return e }
                    }
                    println(toString(c()))
                    imut s = fn() -> int {
                        mut seen = 0
                        scope {
                            imut z = 2
                            seen = z
                        }
                        seen
                    }
                    println(toString(s()))
                    imut m = fn() -> int { match A(9) { A(v) => { imut w = v
                        w } } }
                    println(toString(m()))
                }
                """, "LambdaLocalsInForms");
    }

    @Test
    void aLambdaTellsAnInnerLocalFromAnOuterVariableOfTheSameName() throws Exception {
        assertBoth("9\n3\n16\n5 3\n2 1", """
                imut g = 1
                fn main() {
                    imut e = 3
                    imut caught = fn() -> int {
                        try { throw 9 } catch e { println(toString(e)) }
                        e
                    }
                    println(toString(caught()))
                    imut x = 1
                    imut scoped = fn() -> int {
                        mut t = 0
                        scope {
                            imut x = 7
                            imut y = 8
                            t = x + y
                        }
                        t + x
                    }
                    println(toString(scoped()))
                    imut y = 3
                    imut blocked = fn() -> string {
                        mut inner = 0
                        if true {
                            imut y = 5
                            inner = y
                        }
                        toString(inner) + " " + toString(y)
                    }
                    println(blocked())
                    imut overGlobal = fn() -> string {
                        mut inner = 0
                        if true {
                            imut g = 2
                            inner = g
                        }
                        toString(inner) + " " + toString(g)
                    }
                    println(overGlobal())
                }
                """, "LambdaSameNames");
    }

    @Test
    void aSpawnTellsAnInnerLocalFromAnOuterVariableOfTheSameName() throws Exception {
        String source = """
                fn main() {
                    imut x = 3
                    scope {
                        spawn {
                            if x == 3 {
                                imut x = 9
                                println(toString(x))
                            }
                            println(toString(x))
                        }
                    }
                }
                """;
        assertEquals("9\n3", harness.interpret(source, "SpawnSameNames"));
    }

    @Test
    void aSpawnInsideAFunctionItCallsSeesTheFunctionsCaptures() throws Exception {
        assertInterpreted("1\n1\n5", """
                fn run() {
                    imut x = 1
                    fn k(n: int) -> int {
                        if n > 0 {
                            scope { spawn { println(toString(k(n - 1))) } }
                        }
                        x
                    }
                    println(toString(k(1)))
                }
                fn sibling() {
                    imut y = 5
                    if true {
                        fn a() { scope { spawn { println(toString(b())) } } }
                        fn b() -> int { y }
                        a()
                    }
                }
                fn main() {
                    run()
                    sibling()
                }
                """, "SpawnSettles");
    }

    @Test
    void aForLoopInALambdaKeepsItsOwnCounters() throws Exception {
        assertBoth("3\n3\n7", """
                fn main() {
                    imut forIn = fn() -> int {
                        mut t = 0
                        for k in [1, 2] { t = t + k }
                        t
                    }
                    println(toString(forIn()))
                    imut cFor = fn() -> int {
                        mut t = 0
                        for mut i = 0 i < 3 i = i + 1 { t = t + 1 }
                        t
                    }
                    println(toString(cFor()))
                    imut base = 4
                    imut captures = fn() -> int {
                        mut t = base
                        for k in [1, 2] { t = t + k }
                        t
                    }
                    println(toString(captures()))
                }
                """, "ForInLambda");
    }

    @Test
    void aClosureInAClosureMayReadAGlobalAndALocal() throws Exception {
        assertBoth("11\n11\n4\n2", """
                imut G = 11
                fn viaFunction() -> int {
                    imut l = fn() -> int {
                        imut g = fn() -> int { G }
                        g()
                    }
                    l()
                }
                fn main() {
                    println(toString(viaFunction()))
                    imut l = fn() -> int {
                        imut g = fn() -> int { G }
                        g()
                    }
                    println(toString(l()))
                    imut a = 4
                    imut m = fn() -> int {
                        imut g = fn() -> int { a }
                        g()
                    }
                    println(toString(m()))
                    mut n = 1
                    imut w = fn() {
                        imut i = fn() { n = n + 1 }
                        i()
                    }
                    w()
                    println(toString(n))
                }
                """, "ClosureInClosure");
    }

    @Test
    void aLambdaInANestedFunctionMayReadAGlobal() throws Exception {
        assertBoth("5\n6", """
                imut G = 5
                mut M = 6
                fn f() -> int {
                    fn k() -> int {
                        imut l = fn() -> int { G }
                        l()
                    }
                    k()
                }
                fn g() -> int {
                    fn k() -> int {
                        imut l = fn() -> int { 6 }
                        l()
                    }
                    k()
                }
                fn main() {
                    println(toString(f()))
                    println(toString(g()))
                }
                """, "LambdaInNestedReadsGlobal");
    }

    @Test
    void aNestedFunctionInAMethodMayReadAGlobalWhateverComesFirst() throws Exception {
        assertBoth("2", """
                mut M = 2
                struct S { v: int }
                impl S {
                    fn m(self) -> int {
                        fn h() -> int { M }
                        h()
                    }
                }
                fn main() {
                    imut s = S { v: 1 }
                    println(toString(s.m()))
                }
                """, "NestedInMethodReadsGlobal");
    }

    @Test
    void aLocalDoesNotLeakIntoTheGlobals() throws Exception {
        // The bug this replaces: a local reachable with no frame holding it.
        assertInterpreted("1\n2", """
                fn first() -> fn() -> int {
                    imut x = 1
                    fn read() -> int { x }
                    read
                }
                fn second() -> fn() -> int {
                    imut x = 2
                    fn read2() -> int { x }
                    read2
                }
                fn main() {
                    imut a = first()
                    imut b = second()
                    println(toString(a()))
                    println(toString(b()))
                }
                """, "NoLeakToGlobals");
    }
}
