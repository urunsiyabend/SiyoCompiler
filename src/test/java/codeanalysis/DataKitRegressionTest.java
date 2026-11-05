package codeanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
