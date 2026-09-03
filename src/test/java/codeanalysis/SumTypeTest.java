package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sum types: declaring a closed set of variants, constructing one, and moving
 * a value of the type across the boundaries a program actually crosses.
 */
class SumTypeTest {

    // --- declaring and constructing ------------------------------------------

    @Test
    void aVariantWithAPayloadIsConstructedByWritingIt() throws Exception {
        String source = """
                type Result = Ok(int) | Err(string)
                fn main() {
                    imut r = Ok(5)
                    println(toString(r))
                }
                """;
        assertEquals("Ok(5)", run(source, "OkLiteral"));
        assertEquals("Ok(5)", interpret(source, "OkLiteral"));
    }

    @Test
    void aVariantWithNoPayloadIsAValueOnItsOwn() throws Exception {
        String source = """
                type Option = Some(int) | None
                fn main() {
                    imut nothing = None
                    println(toString(nothing))
                }
                """;
        assertEquals("None", run(source, "NoneLiteral"));
        assertEquals("None", interpret(source, "NoneLiteral"));
    }

    @Test
    void aVariantMayCarrySeveralValues() throws Exception {
        assertEquals("Pair(3, hi)", run("""
                type Both = Pair(int, string)
                fn main() { println(toString(Pair(3, "hi"))) }
                """, "PairPayload"));
    }

    @Test
    void aVariantMayBeWrittenQualifiedByItsType() throws Exception {
        String source = """
                type Result = Ok(int) | Err(string)
                fn main() {
                    println(toString(Result.Ok(1)))
                    println(toString(Result.Err("no")))
                }
                """;
        assertEquals("Ok(1)\nErr(no)", run(source, "QualifiedVariant"));
        assertEquals("Ok(1)\nErr(no)", interpret(source, "QualifiedVariant"));
    }

    @Test
    void twoTypesMayDeclareTheSameVariantWhenItIsWrittenQualified() throws Exception {
        assertEquals("Ok(1)\nOk(2)", run("""
                type Read = Ok(int) | Fail(string)
                type Write = Ok(int) | Refused(string)
                fn main() {
                    println(toString(Read.Ok(1)))
                    println(toString(Write.Ok(2)))
                }
                """, "SharedVariantName"));
    }

    // --- moving a value around -----------------------------------------------

    @Test
    void aSumTypeIsAFunctionReturnType() throws Exception {
        String source = """
                type Result = Ok(int) | Err(string)
                fn parse(text: string) -> Result {
                    if len(text) == 0 { Err("empty") } else { Ok(len(text)) }
                }
                fn main() {
                    println(toString(parse("abc")))
                    println(toString(parse("")))
                }
                """;
        assertEquals("Ok(3)\nErr(empty)", run(source, "UnionReturn"));
        assertEquals("Ok(3)\nErr(empty)", interpret(source, "UnionReturn"));
    }

    @Test
    void aSumTypeIsAParameterType() throws Exception {
        assertEquals("got Ok(7)", run("""
                type Result = Ok(int) | Err(string)
                fn describe(r: Result) -> string { "got " + toString(r) }
                fn main() { println(describe(Ok(7))) }
                """, "UnionParameter"));
    }

    @Test
    void aSumTypeAnnotationIsAcceptedOnALocal() throws Exception {
        assertEquals("Err(nope)", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    imut r: Result = Err("nope")
                    println(toString(r))
                }
                """, "UnionAnnotation"));
    }

    @Test
    void aSumTypeValueLivesInAnArray() throws Exception {
        assertEquals("Ok(1)\nErr(bad)", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    imut all = [Ok(1), Err("bad")]
                    for r in all { println(toString(r)) }
                }
                """, "UnionInArray"));
    }

    @Test
    void aSumTypeValueIsAStructField() throws Exception {
        assertEquals("Ok(2)", run("""
                type Result = Ok(int) | Err(string)
                struct Response { body: Result }
                fn main() {
                    imut res = Response { body: Ok(2) }
                    println(toString(res.body))
                }
                """, "UnionInStruct"));
    }

    @Test
    void aVariantPayloadMayBeAStruct() throws Exception {
        assertEquals("Found({x=1, y=2})", run("""
                struct Point { x: int, y: int }
                type Hit = Found(Point) | Missing
                fn main() { println(toString(Found(Point { x: 1, y: 2 }))) }
                """, "StructPayload"));
    }

    @Test
    void aVariantPayloadMayBeTheTypeBeingDeclared() throws Exception {
        assertEquals("Cons(1, Cons(2, Nil))", run("""
                type List = Cons(int, List) | Nil
                fn main() { println(toString(Cons(1, Cons(2, Nil)))) }
                """, "RecursivePayload"));
    }

    @Test
    void twoValuesOfTheSameVariantAndPayloadAreEqual() throws Exception {
        assertEquals("true\nfalse\nfalse", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    println(toString(Ok(1) == Ok(1)))
                    println(toString(Ok(1) == Ok(2)))
                    println(toString(Ok(1) == Err("x")))
                }
                """, "UnionEquality"));
    }

    // --- matching ------------------------------------------------------------

    @Test
    void aVariantPatternBindsItsPayload() throws Exception {
        String source = """
                type Result = Ok(int) | Err(string)
                fn show(r: Result) -> string {
                    match r {
                        Ok(value) => "ok:" + toString(value),
                        Err(message) => "err:" + message,
                    }
                }
                fn main() {
                    println(show(Ok(42)))
                    println(show(Err("gone")))
                }
                """;
        assertEquals("ok:42\nerr:gone", run(source, "MatchBinding"));
        assertEquals("ok:42\nerr:gone", interpret(source, "MatchBinding"));
    }

    @Test
    void aPayloadLessVariantIsMatchedByName() throws Exception {
        String source = """
                type Option = Some(int) | None
                fn value(o: Option) -> int {
                    match o {
                        Some(n) => n,
                        None => 0,
                    }
                }
                fn main() {
                    println(toString(value(Some(7))))
                    println(toString(value(None)))
                }
                """;
        assertEquals("7\n0", run(source, "MatchNone"));
        assertEquals("7\n0", interpret(source, "MatchNone"));
    }

    @Test
    void aPatternMayBindSeveralSlots() throws Exception {
        assertEquals("3:hi", run("""
                type Both = Pair(int, string) | Empty
                fn main() {
                    imut p = Pair(3, "hi")
                    println(match p {
                        Pair(n, text) => toString(n) + ":" + text,
                        Empty => "none",
                    })
                }
                """, "MatchTwoSlots"));
    }

    @Test
    void aPatternMayDiscardASlot() throws Exception {
        assertEquals("hi", run("""
                type Both = Pair(int, string) | Empty
                fn main() {
                    println(match Pair(3, "hi") {
                        Pair(_, text) => text,
                        Empty => "none",
                    })
                }
                """, "MatchDiscardSlot"));
    }

    @Test
    void aPatternMayBeQualifiedByItsType() throws Exception {
        assertEquals("2", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    println(match Ok(2) {
                        Result.Ok(n) => toString(n),
                        Result.Err(m) => m,
                    })
                }
                """, "MatchQualifiedPattern"));
    }

    @Test
    void aVariantPatternWorksOnAFunctionResult() throws Exception {
        assertEquals("len 3", run("""
                type Result = Ok(int) | Err(string)
                fn parse(text: string) -> Result {
                    if len(text) == 0 { Err("empty") } else { Ok(len(text)) }
                }
                fn main() {
                    println(match parse("abc") {
                        Ok(n) => "len " + toString(n),
                        Err(m) => m,
                    })
                }
                """, "MatchOnCall"));
    }

    @Test
    void aBoundPayloadKeepsItsStructIdentity() throws Exception {
        assertEquals("1", run("""
                struct Point { x: int, y: int }
                type Hit = Found(Point) | Missing
                fn main() {
                    println(match Found(Point { x: 1, y: 2 }) {
                        Found(p) => toString(p.x),
                        Missing => "none",
                    })
                }
                """, "MatchStructPayload"));
    }

    @Test
    void aMatchArmMayHaveABlockBody() throws Exception {
        assertEquals("44", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    println(match Ok(42) {
                        Ok(n) => {
                            imut doubled = n + 2
                            toString(doubled)
                        },
                        Err(m) => m,
                    })
                }
                """, "MatchBlockBody"));
    }

    @Test
    void aValuePatternStillComparesByValue() throws Exception {
        assertEquals("exactly one\nsomething else", run("""
                type Result = Ok(int) | Err(string)
                fn describe(r: Result) -> string {
                    match r {
                        Ok(1) => "exactly one",
                        _ => "something else",
                    }
                }
                fn main() {
                    println(describe(Ok(1)))
                    println(describe(Ok(2)))
                }
                """, "MatchValuePattern"));
    }

    @Test
    void aRecursiveTypeIsWalkedByMatching() throws Exception {
        assertEquals("6", run("""
                type List = Cons(int, List) | Nil
                fn sum(l: List) -> int {
                    match l {
                        Cons(head, tail) => head + sum(tail),
                        Nil => 0,
                    }
                }
                fn main() { println(toString(sum(Cons(1, Cons(2, Cons(3, Nil)))))) }
                """, "MatchRecursive"));
    }

    @Test
    void aMatchStatementNeedNotProduceAValue() throws Exception {
        assertEquals("ok 5", run("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    match Ok(5) {
                        Ok(n) => println("ok " + toString(n)),
                        Err(m) => println("err " + m),
                    }
                }
                """, "MatchStatement"));
    }

    // --- exhaustiveness ------------------------------------------------------

    @Test
    void aMatchMissingAVariantIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    imut r = Ok(1)
                    println(match r {
                        Ok(n) => toString(n),
                    })
                }
                """);
        assertTrue(message.contains("does not cover Err"),
                "expected a non-exhaustive diagnostic, got: " + message);
    }

    @Test
    void aMatchMissingSeveralVariantsNamesThemAll() {
        String message = firstDiagnostic("""
                type Colour = Red | Green | Blue
                fn main() {
                    imut c: Colour = Red
                    println(match c {
                        Red => "red",
                    })
                }
                """);
        assertTrue(message.contains("does not cover Green, Blue"),
                "expected both missing variants, got: " + message);
    }

    @Test
    void aMatchOnAParameterIsCheckedForExhaustiveness() {
        String message = firstDiagnostic("""
                type Config = Loaded(map) | Missing(string)
                fn describe(c: Config) -> string {
                    match c {
                        Loaded(m) => "loaded",
                    }
                }
                fn main() { println(describe(Missing("x"))) }
                """);
        assertTrue(message.contains("does not cover Missing"),
                "expected a non-exhaustive diagnostic, got: " + message);
    }

    @Test
    void aMatchOnALambdaParameterIsCheckedForExhaustiveness() {
        String message = firstDiagnostic("""
                type Config = Loaded(map) | Missing(string)
                fn main() {
                    imut describe = fn(c: Config) -> string {
                        match c {
                            Loaded(m) => "loaded",
                        }
                    }
                    println(describe(Missing("x")))
                }
                """);
        assertTrue(message.contains("does not cover Missing"),
                "expected a non-exhaustive diagnostic, got: " + message);
    }

    @Test
    void aDefaultArmSatisfiesExhaustiveness() throws Exception {
        assertEquals("other", run("""
                type Colour = Red | Green | Blue
                fn main() {
                    imut c: Colour = Green
                    println(match c {
                        Red => "red",
                        _ => "other",
                    })
                }
                """, "MatchDefaultArm"));
    }

    @Test
    void aPatternForAnotherTypesVariantIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                type Other = Yes(int) | No
                fn main() {
                    imut r = Ok(1)
                    println(match r {
                        Other.Yes(n) => toString(n),
                        _ => "no",
                    })
                }
                """);
        assertTrue(message.contains("cannot match a value of type 'Result'"),
                "expected a type-mismatch diagnostic, got: " + message);
    }

    @Test
    void aPatternBindingTheWrongNumberOfSlotsIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                fn main() {
                    imut r = Ok(1)
                    println(match r {
                        Ok(a, b) => "two",
                        Err(m) => m,
                    })
                }
                """);
        assertTrue(message.contains("carries 1 value"),
                "expected a payload-count diagnostic, got: " + message);
    }

    // --- rejected programs ---------------------------------------------------

    @Test
    void aVariantTheTypeDoesNotDeclareIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                fn main() { println(toString(Result.Maybe(1))) }
                """);
        assertTrue(message.contains("has no variant 'Maybe'"),
                "expected an unknown-variant diagnostic, got: " + message);
    }

    @Test
    void aPayloadOfTheWrongShapeIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                fn main() { println(toString(Ok(1, 2))) }
                """);
        assertTrue(message.contains("carries 1 value"),
                "expected a payload-count diagnostic, got: " + message);
    }

    @Test
    void aPayloadOfTheWrongTypeIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Err(string)
                fn main() { println(toString(Ok("text"))) }
                """);
        assertTrue(message.contains("Cannot convert"),
                "expected a conversion diagnostic, got: " + message);
    }

    @Test
    void aPayloadLessVariantCannotBeGivenAPayload() {
        String message = firstDiagnostic("""
                type Option = Some(int) | None
                fn main() { println(toString(None(1))) }
                """);
        assertTrue(message.contains("carries 0 values"),
                "expected a payload-count diagnostic, got: " + message);
    }

    @Test
    void aVariantDeclaredTwiceInOneTypeIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(int) | Ok(string)
                fn main() { println("x") }
                """);
        assertTrue(message.contains("already declares a variant named 'Ok'"),
                "expected a duplicate-variant diagnostic, got: " + message);
    }

    @Test
    void anUnknownPayloadTypeIsReported() {
        String message = firstDiagnostic("""
                type Result = Ok(Widget) | Err(string)
                fn main() { println("x") }
                """);
        assertTrue(message.contains("Widget"),
                "expected an unknown-type diagnostic, got: " + message);
    }

    @Test
    void aBareVariantNameTwoTypesDeclareIsAmbiguous() {
        String message = firstDiagnostic("""
                type Read = Ok(int) | Fail(string)
                type Write = Ok(int) | Refused(string)
                fn main() { println(toString(Ok(1))) }
                """);
        assertTrue(message.contains("is declared by Read and Write"),
                "expected an ambiguity diagnostic, got: " + message);
    }

    // --- type is a contextual keyword ----------------------------------------

    @Test
    void typeIsStillUsableAsAName() throws Exception {
        assertEquals("GET\nPOST", run("""
                fn main() {
                    mut type = "GET"
                    println(type)
                    type = "POST"
                    println(type)
                }
                """, "TypeAsName"));
    }

    @Test
    void typeIsStillUsableAsAStructFieldAndFunctionName() throws Exception {
        assertEquals("json", run("""
                struct Header { type: string }
                fn type(h: Header) -> string { h.type }
                fn main() {
                    imut h = Header { type: "json" }
                    println(type(h))
                }
                """, "TypeAsMember"));
    }

    // --- helpers -------------------------------------------------------------

    private String firstDiagnostic(String source) {
        SyntaxTree tree = SyntaxTree.parse(source);
        if (tree.diagnostics().hasNext()) {
            return tree.diagnostics().get(0).getMessage();
        }
        Compilation compilation = new Compilation(tree, new ModuleRegistry(), "Diag.siyo");
        byte[] bytes = compilation.compile("Diag");
        if (bytes != null) fail("expected the program to be rejected");
        DiagnosticBox diagnostics = compilation.getGlobalScope().getDiagnostics();
        if (!diagnostics.hasNext() && compilation.getEmitDiagnostics() != null) {
            diagnostics = compilation.getEmitDiagnostics();
        }
        if (!diagnostics.hasNext()) fail("expected a diagnostic");
        return diagnostics.get(0).getMessage();
    }

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
