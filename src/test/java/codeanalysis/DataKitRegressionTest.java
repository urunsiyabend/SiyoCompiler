package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void aGenericCallOverPlainValuesStillWorks() throws Exception {
        assertBoth("3\nab\n2", """
                fn twice<T>(x: T) -> Array<T> { [x, x] }
                fn main() {
                    imut xs = twice(1)
                    println(toString(xs[0] + xs[1] + 1))
                    imut ss = twice("a")
                    println(ss[0] + "b")
                    imut anything = twice(twice(2))
                    println(toString(len(anything[0])))
                }
                """, "GenericPlainValues");
    }
}
