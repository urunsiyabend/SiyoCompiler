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
 * Language semantics that used to be wrong, silently or loudly.
 *
 * <p>Most of these were found by building a real library on Siyo: a program
 * that compiled and produced a wrong answer, or one the compiler refused for a
 * reason it could not explain.
 */
class LanguageSemanticsTest {

    // --- a block's value is its tail ----------------------------------------

    @Test
    void anIfExpressionMayBeAFunctionsTailValue() throws Exception {
        assertEquals("OK\n?", run("""
                fn reason(code: int) -> string { if code == 200 { "OK" } else { "?" } }
                fn main() {
                    println(reason(200))
                    println(reason(404))
                }
                """, "IfTailValue"));
    }

    @Test
    void aNestedIfChainMayBeAFunctionsTailValue() throws Exception {
        assertEquals("small\nmedium\nlarge", run("""
                fn size(n: int) -> string {
                    if n < 10 { "small" } else { if n < 100 { "medium" } else { "large" } }
                }
                fn main() {
                    println(size(5))
                    println(size(50))
                    println(size(500))
                }
                """, "NestedIfTail"));
    }

    @Test
    void aTryCatchMayBeAFunctionsTailValue() throws Exception {
        assertEquals("ok\nrecovered", run("""
                fn attempt(fail: bool) -> string {
                    try {
                        if fail { error("boom") }
                        "ok"
                    } catch e {
                        "recovered"
                    }
                }
                fn main() {
                    println(attempt(false))
                    println(attempt(true))
                }
                """, "TryTailValue"));
    }

    @Test
    void aTryExpressionMayContainControlFlow() throws Exception {
        assertEquals("ab", run("""
                fn f(flag: bool) -> string {
                    return try {
                        mut out = "a"
                        if flag { out += "b" }
                        out
                    } catch e {
                        "error"
                    }
                }
                fn main() { println(f(true)) }
                """, "TryExpressionControlFlow"));
    }

    // --- enums ---------------------------------------------------------------

    @Test
    void anEnumMemberMayCarryAnExplicitValue() throws Exception {
        assertEquals("200 201 404 405\n0 1 2", run("""
                enum Status { OK = 200, CREATED = 201, NOT_FOUND = 404, GONE }
                enum Plain { A, B, C }
                fn main() {
                    println(toString(Status.OK) + " " + toString(Status.CREATED)
                        + " " + toString(Status.NOT_FOUND) + " " + toString(Status.GONE))
                    println(toString(Plain.A) + " " + toString(Plain.B) + " " + toString(Plain.C))
                }
                """, "EnumValues"));
    }

    @Test
    void anEnumMemberMayCarryANegativeValue() throws Exception {
        assertEquals("-1 0", run("""
                enum Code { MISSING = -1, NONE }
                fn main() { println(toString(Code.MISSING) + " " + toString(Code.NONE)) }
                """, "EnumNegative"));
    }

    // --- closures ------------------------------------------------------------

    @Test
    void aWriteToACapturedVariableIsSharedWithTheEnclosingScope() throws Exception {
        // 0.5.0 rejected this because a capture was by value and the write was
        // discarded. The variable now lives in a cell that the closure shares.
        String source = """
                fn main() {
                    mut counter = 0
                    imut inc = fn() { counter += 1 }
                    inc()
                    inc()
                    println(toString(counter))
                }
                """;
        assertEquals("2", run(source, "CapturedWrite"));
        assertEquals("2", interpret(source, "CapturedWrite"));
    }

    @Test
    void writingToACapturedImmutableIsStillAnError() {
        assertTrue(firstDiagnostic("""
                fn main() {
                    imut counter = 0
                    imut inc = fn() { counter += 1 }
                    inc()
                }
                """).contains("read-only"),
                "an immutable variable cannot be written, captured or not");
    }

    @Test
    void aClosureMayBeCalledThroughAnErasedValue() throws Exception {
        assertEquals("42", run("""
                fn double(x: int) -> int { x * 2 }
                fn main() {
                    mut m = map()
                    m.set("d", double)
                    imut h = m.get("d")
                    println(toString(h(21)))
                }
                """, "ErasedClosureCall"));
    }

    @Test
    void aStructFieldMayBeDeclaredFnAndCalled() throws Exception {
        assertEquals("42\n10", run("""
                struct Route { pattern: string, handler: fn }
                fn main() {
                    mut r = Route { pattern: "/", handler: fn(x: int) -> int { x * 2 } }
                    println(toString(r.handler(21)))
                    mut routes: Route[] = [r]
                    println(toString(routes[0].handler(5)))
                }
                """, "StructFnField"));
    }

    @Test
    void callingANonFunctionFieldIsReportedAtCompileTime() {
        assertEquals("Field 'n' of struct 'Bad' is not callable\n\n"
                        + "  help: declare it 'fn' if it is meant to hold a function",
                firstDiagnostic("""
                        struct Bad { n: int }
                        fn main() {
                            mut b = Bad { n: 1 }
                            println(toString(b.n(2)))
                        }
                        """));
    }

    @Test
    void aLambdaMayBeABlocksTailExpression() throws Exception {
        assertEquals("21", run("""
                fn make(f: int) -> fn { fn(x: int) -> int { x * f } }
                fn main() { println(toString(make(3)(7))) }
                """, "LambdaTail"));
    }

    @Test
    void aCapturedVariableWorksInsideACompositeLiteral() throws Exception {
        assertEquals("hi\nst\nmp", run("""
                fn take(xs: string[]) -> string { xs[0] }
                fn call(h: fn) -> string { h() }
                fn makeArray(v: string) -> fn { return fn() -> string { take([v]) } }
                struct P { name: string }
                fn makeStruct(v: string) -> fn { return fn() -> string { P { name: v }.name } }
                fn makeMap(v: string) -> fn { return fn() -> string { toString(({"k": v}).get("k")) } }
                fn main() {
                    println(call(makeArray("hi")))
                    println(call(makeStruct("st")))
                    println(call(makeMap("mp")))
                }
                """, "CaptureInLiteral"));
    }

    // --- spawn capture -------------------------------------------------------

    @Test
    void capturingAMutableVariableInSpawnSuggestsAKeywordThatExists() {
        String message = firstDiagnostic("""
                fn main() {
                    mut n = 0
                    scope { spawn { println(toString(n)) } }
                }
                """);
        assertTrue(message.contains("declare it 'imut'"), "must suggest imut: " + message);
        assertTrue(!message.contains("'let'"), "Siyo has no 'let' keyword: " + message);
    }

    @Test
    void writingThroughACapturedStructIsReportedNotCrashed() {
        // This used to pass the capture check and then crash the emitter.
        String message = firstDiagnostic("""
                struct Counter { n: int }
                fn main() {
                    imut c = Counter { n: 0 }
                    imut ch = channel(4)
                    scope { spawn { c.n += 1  ch.send("a") } }
                }
                """);
        assertTrue(message.startsWith("'c' cannot be captured by a spawn block"), message);
        assertTrue(message.contains("a struct's contents are mutable"), message);
        assertTrue(!message.contains("Mutable variable"),
                "an imut binding must not be described as mutable: " + message);
    }

    // --- declared types ------------------------------------------------------

    @Test
    void aLocalTypeAnnotationIsChecked() {
        assertEquals("Cannot convert type <class java.lang.Integer> to <class java.lang.String>",
                firstDiagnostic("""
                        fn main() {
                            imut x: string = 5
                            println(x)
                        }
                        """));
    }

    @Test
    void aLocalTypeAnnotationNarrowsAnErasedValue() throws Exception {
        assertEquals("42\n2", run("""
                fn erase(v: object) -> object { v }
                fn main() {
                    imut n: int = erase(41)
                    println(toString(n + 1))
                    imut m: map = erase(map())
                    m.set("a", 1)
                    m.set("b", 2)
                    println(toString(m.size()))
                }
                """, "AnnotationNarrows"));
    }

    @Test
    void anAnnotationAcceptsTheWideningsSiyoAlreadyPerforms() throws Exception {
        assertEquals("5\n5.0", run("""
                fn main() {
                    imut wide: long = 5
                    println(toString(wide))
                    imut real: float = 5
                    println(toString(real))
                }
                """, "AnnotationWidening"));
    }

    // --- null and numbers ----------------------------------------------------

    @Test
    void aMapMayBeComparedAgainstNull() throws Exception {
        assertEquals("absent\npresent", run("""
                fn find(key: string) -> map {
                    if key == "missing" { return null } else { return map() }
                }
                fn main() {
                    imut missing = find("missing")
                    if missing == null { println("absent") } else { println("present") }
                    imut found = find("here")
                    if found != null { println("present") } else { println("absent") }
                }
                """, "MapNullCompare"));
    }

    @Test
    void aLongNarrowsToAnIntWithoutGoingThroughText() throws Exception {
        assertEquals("2000000000", run("""
                fn main() {
                    imut wide = toLong(2000000000)
                    println(toString(toInt(wide)))
                }
                """, "LongToInt"));
    }

    // --- keywords ------------------------------------------------------------

    @Test
    void sendMayNameAMethod() throws Exception {
        // `send` only means something as a statement prefix, so reserving it
        // everywhere cost a response object its most natural verb.
        assertEquals("hello\nsent", run("""
                struct Response { body: string }
                impl Response {
                    fn new() -> Response { Response { body: "" } }
                    fn send(self, body: string) -> Response {
                        self.body = body
                        self
                    }
                }
                actor Counter { n: int }
                impl Counter {
                    fn new() -> Counter { Counter { n: 0 } }
                    fn bump(self) -> int { self.n += 1  self.n }
                }
                fn main() {
                    mut r = Response.new()
                    r.send("hello")
                    println(r.body)
                    mut c = spawn Counter.new()
                    send c.bump()
                    println("sent")
                }
                """, "SendAsName"));
    }

    // --- backend parity ------------------------------------------------------

    @Test
    void theInterpreterRunsInitAndMainLikeTheCompiler() throws Exception {
        String source = """
                mut ready = false
                fn init() { ready = true }
                fn main() { println("ready=" + toString(ready)) }
                """;
        assertEquals("ready=true", run(source, "EntryPointParity"));
        assertEquals("ready=true", interpret(source, "EntryPointParity"));
    }

    // --- helpers -------------------------------------------------------------

    private String firstDiagnostic(String source) {
        SyntaxTree tree = SyntaxTree.parse(source);
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
