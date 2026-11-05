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
