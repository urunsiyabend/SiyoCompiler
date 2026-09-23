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
 * Raising an error, and what a handler learns about it. An error used to be
 * text and nothing else: {@code error(msg)} raised a message, {@code catch e}
 * bound that message, and a Java exception with no message bound null.
 */
class ErrorHandlingTest {

    // --- throw ----------------------------------------------------------------

    @Test
    void throwRaisesAValueThatTheHandlerBinds() throws Exception {
        String source = """
                fn main() {
                    try {
                        throw "boom"
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("boom", run(source, "ThrowText"));
        assertEquals("boom", interpret(source, "ThrowText"));
    }

    @Test
    void aThrownErrorLeavesTheFunctionThatRaisedIt() throws Exception {
        String source = """
                fn risky(n: int) -> int {
                    if n < 0 {
                        throw "negative"
                    }
                    n * 2
                }
                fn main() {
                    try {
                        println(toString(risky(-1)))
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("negative", run(source, "ThrowAcrossCall"));
        assertEquals("negative", interpret(source, "ThrowAcrossCall"));
    }

    @Test
    void anErrorCarriesANumber() throws Exception {
        String source = """
                fn main() {
                    try {
                        throw 404
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("404", run(source, "ThrowInt"));
        assertEquals("404", interpret(source, "ThrowInt"));
    }

    @Test
    void anErrorCarriesAStruct() throws Exception {
        String source = """
                struct Failure { code: int, why: string }
                fn main() {
                    try {
                        throw Failure { code: 503, why: "busy" }
                    } catch e: Failure {
                        println(toString(e.code))
                        println(e.why)
                    }
                }
                """;
        assertEquals("503\nbusy", run(source, "ThrowStruct"));
        assertEquals("503\nbusy", interpret(source, "ThrowStruct"));
    }

    // --- an error matched on by type ------------------------------------------

    @Test
    void aThrownVariantIsMatchedOnByVariant() throws Exception {
        String source = """
                type Failure = NotFound(int) | Refused(string)
                fn main() {
                    try {
                        throw NotFound(404)
                    } catch e: Failure {
                        imut described = match e {
                            NotFound(code) => "missing: " + toString(code),
                            Refused(why) => "refused: " + why
                        }
                        println(described)
                    }
                }
                """;
        assertEquals("missing: 404", run(source, "ThrowVariant"));
        assertEquals("missing: 404", interpret(source, "ThrowVariant"));
    }

    @Test
    void anUnannotatedHandlerStillBindsTheThrownVariant() throws Exception {
        String source = """
                type Failure = NotFound(int) | Refused(string)
                fn main() {
                    try {
                        throw Refused("nope")
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("Refused(nope)", run(source, "ThrowVariantErased"));
        assertEquals("Refused(nope)", interpret(source, "ThrowVariantErased"));
    }

    @Test
    void aMatchOverACaughtSumTypeMustCoverEveryVariant() {
        assertTrue(firstDiagnostic("""
                type Failure = NotFound(int) | Refused(string)
                fn main() {
                    try {
                        throw NotFound(1)
                    } catch e: Failure {
                        imut described = match e {
                            NotFound(code) => toString(code)
                        }
                        println(described)
                    }
                }
                """).startsWith("This match on 'Failure' does not cover Refused"));
    }

    @Test
    void aCatchTypeThatNoDeclarationNamesIsReported() {
        assertEquals("Type 'Nonexistent' does not exist", firstDiagnostic("""
                fn main() {
                    try {
                        throw 1
                    } catch e: Nonexistent {
                        println("caught")
                    }
                }
                """));
    }

    // --- error() keeps carrying its message ------------------------------------

    @Test
    void errorStillRaisesTextThatTheHandlerBinds() throws Exception {
        String source = """
                fn main() {
                    try {
                        error("went wrong")
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("went wrong", run(source, "ErrorText"));
        assertEquals("went wrong", interpret(source, "ErrorText"));
    }

    @Test
    void aBoundErrorMessageIsStillUsableAsText() throws Exception {
        String source = """
                fn main() {
                    try {
                        error("no such file")
                    } catch e {
                        println("failed: " + toString(e))
                    }
                }
                """;
        assertEquals("failed: no such file", run(source, "ErrorConcat"));
        assertEquals("failed: no such file", interpret(source, "ErrorConcat"));
    }

    @Test
    void anAnnotatedHandlerNarrowsTheMessageToText() throws Exception {
        String source = """
                fn main() {
                    try {
                        error("short")
                    } catch e: string {
                        println(toString(len(e)))
                    }
                }
                """;
        assertEquals("5", run(source, "ErrorTyped"));
        assertEquals("5", interpret(source, "ErrorTyped"));
    }

    // --- an exception's type is observable -------------------------------------

    @Test
    void aMessagelessJavaExceptionBindsItsTypeRatherThanNull() throws Exception {
        String source = """
                import java "java.lang.IllegalStateException"
                fn main() {
                    try {
                        throw IllegalStateException.new()
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("IllegalStateException", run(source, "MessagelessJava"));
        assertEquals("IllegalStateException", interpret(source, "MessagelessJava"));
    }

    @Test
    void aJavaExceptionWithAMessageStillBindsTheMessage() throws Exception {
        String source = """
                import java "java.lang.IllegalStateException"
                fn main() {
                    try {
                        throw IllegalStateException.new("bad state")
                    } catch e {
                        println(toString(e))
                    }
                }
                """;
        assertEquals("bad state", run(source, "MessagefulJava"));
        assertEquals("bad state", interpret(source, "MessagefulJava"));
    }

    @Test
    void aFailureRaisedByTheRuntimeIsStillDescribed() throws Exception {
        String source = """
                fn main() {
                    try {
                        imut arr = [1, 2, 3]
                        println(toString(arr[9]))
                    } catch e {
                        println("caught: " + toString(e))
                    }
                }
                """;
        String compiled = run(source, "RuntimeFailure");
        if (compiled.equals("caught: null")) {
            fail("a runtime failure still bound null");
        }
        if (!compiled.startsWith("caught: ")) {
            fail("expected the failure to be caught, got: " + compiled);
        }
    }

    // --- a try expression sees the same payload -------------------------------

    @Test
    void aTryExpressionBindsTheThrownPayload() throws Exception {
        String source = """
                fn main() {
                    imut code = try {
                        throw 42
                    } catch e: int {
                        e
                    }
                    println(toString(code))
                }
                """;
        assertEquals("42", run(source, "TryExprPayload"));
        assertEquals("42", interpret(source, "TryExprPayload"));
    }

    @Test
    void aTryExpressionOverAVariantSelectsAFallback() throws Exception {
        String source = """
                type Failure = Timeout(int) | Closed
                fn fetch() -> string {
                    throw Timeout(30)
                }
                fn main() {
                    imut body = try {
                        fetch()
                    } catch e: Failure {
                        match e {
                            Timeout(secs) => "timed out after " + toString(secs),
                            Closed => "closed"
                        }
                    }
                    println(body)
                }
                """;
        assertEquals("timed out after 30", run(source, "TryExprVariant"));
        assertEquals("timed out after 30", interpret(source, "TryExprVariant"));
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
