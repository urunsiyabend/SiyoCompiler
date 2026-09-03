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
 * Declared function types.
 *
 * <p>A signature written in a declaration — {@code fn(int) -> int} — used to be
 * parsed and discarded, so a callback of the wrong shape was accepted and blew
 * up at run time, and a call through such a name had an erased result type.</p>
 */
class FunctionTypeTest {

    // --- a declared signature is honoured ------------------------------------

    @Test
    void aCallThroughADeclaredFunctionTypeHasItsReturnType() throws Exception {
        String source = """
                fn apply(f: fn(int) -> int, n: int) -> int { f(n) }
                fn main() { println(toString(apply(fn(x: int) -> int { x * 2 }, 21))) }
                """;
        assertEquals("42", run(source, "DeclaredCallback"));
        assertEquals("42", interpret(source, "DeclaredCallback"));
    }

    @Test
    void aLocalDeclaredWithAFunctionTypeIsCallable() throws Exception {
        String source = """
                fn main() {
                    imut double: fn(int) -> int = fn(x: int) -> int { x * 2 }
                    imut n: int = double(4)
                    println(toString(n))
                }
                """;
        assertEquals("8", run(source, "DeclaredLocal"));
        assertEquals("8", interpret(source, "DeclaredLocal"));
    }

    @Test
    void anInferredSignatureGivesTheCallItsType() throws Exception {
        assertEquals("15", run("""
                fn main() {
                    imut add = fn(a: int, b: int) -> int { a + b }
                    imut total: int = add(7, 8)
                    println(toString(total))
                }
                """, "InferredSignature"));
    }

    @Test
    void aFunctionTypeWithNoParametersIsAccepted() throws Exception {
        assertEquals("hi", run("""
                fn call(f: fn() -> string) -> string { f() }
                fn main() { println(call(fn() -> string { "hi" })) }
                """, "NoParameterSignature"));
    }

    @Test
    void aBareFnStillAcceptsAnyClosure() throws Exception {
        assertEquals("3", run("""
                fn call(f: fn, n: int) -> object { f(n) }
                fn main() { println(toString(call(fn(x: int) -> int { x + 1 }, 2))) }
                """, "BareFn"));
    }

    @Test
    void aFunctionTypeIsAStructField() throws Exception {
        assertEquals("9", run("""
                struct Route { handler: fn(int) -> int }
                fn main() {
                    imut r = Route { handler: fn(x: int) -> int { x * 3 } }
                    imut h = r.handler
                    println(toString(h(3)))
                }
                """, "FieldSignature"));
    }

    @Test
    void aNestedFunctionTypeIsAccepted() throws Exception {
        assertEquals("12", run("""
                fn makeAdder(n: int) -> fn(int) -> int { fn(x: int) -> int { x + n } }
                fn main() {
                    imut add10 = makeAdder(10)
                    println(toString(add10(2)))
                }
                """, "NestedSignature"));
    }

    @Test
    void anArrayOfFunctionsIsStillAnArray() throws Exception {
        assertEquals("2", run("""
                fn count(fs: fn()[]) -> int { len(fs) }
                fn main() { println(toString(count([fn() { }, fn() { }]))) }
                """, "FunctionArray"));
    }

    // --- rejected programs ---------------------------------------------------

    @Test
    void aCallbackWithTheWrongArityIsReported() {
        String message = firstDiagnostic("""
                fn apply(f: fn(int) -> int, n: int) -> int { f(n) }
                fn main() { println(toString(apply(fn(a: int, b: int) -> int { a + b }, 1))) }
                """);
        assertTrue(message.contains("taking 2 arguments"),
                "expected an arity diagnostic, got: " + message);
    }

    @Test
    void aCallbackWithTheWrongParameterTypeIsReported() {
        String message = firstDiagnostic("""
                fn apply(f: fn(int) -> int, n: int) -> int { f(n) }
                fn main() { println(toString(apply(fn(s: string) -> int { len(s) }, 1))) }
                """);
        assertTrue(message.contains("Cannot convert"),
                "expected a conversion diagnostic, got: " + message);
    }

    @Test
    void aCallbackWithTheWrongReturnTypeIsReported() {
        String message = firstDiagnostic("""
                fn apply(f: fn(int) -> int, n: int) -> int { f(n) }
                fn main() { println(toString(apply(fn(x: int) -> string { "no" }, 1))) }
                """);
        assertTrue(message.contains("Cannot convert"),
                "expected a conversion diagnostic, got: " + message);
    }

    @Test
    void aLocalAnnotationIsCheckedAgainstItsLambda() {
        String message = firstDiagnostic("""
                fn main() {
                    imut f: fn(int) -> int = fn(a: int, b: int) -> int { a + b }
                    println(toString(f(1)))
                }
                """);
        assertTrue(message.contains("taking 2 arguments"),
                "expected an arity diagnostic, got: " + message);
    }

    @Test
    void callingADeclaredClosureWithTheWrongArityIsReported() {
        String message = firstDiagnostic("""
                fn apply(f: fn(int) -> int) -> int { f(1, 2) }
                fn main() { println(toString(apply(fn(x: int) -> int { x }))) }
                """);
        assertTrue(message.contains("requires 1 argument") || message.contains("1 argument"),
                "expected an argument-count diagnostic, got: " + message);
    }

    @Test
    void callingADeclaredClosureWithTheWrongArgumentTypeIsReported() {
        String message = firstDiagnostic("""
                fn apply(f: fn(int) -> int) -> int { f("no") }
                fn main() { println(toString(apply(fn(x: int) -> int { x }))) }
                """);
        assertTrue(message.contains("Cannot convert"),
                "expected a conversion diagnostic, got: " + message);
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
