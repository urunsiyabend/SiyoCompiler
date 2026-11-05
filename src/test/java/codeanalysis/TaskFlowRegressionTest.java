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
}
