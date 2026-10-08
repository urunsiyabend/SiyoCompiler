package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gaps found by writing LogScope (projects/logscope), each pinned on both
 * backends. See projects/logscope/PAIN_POINTS.md for the reproducers.
 */
class DogfoodRegressionTest {

    // --- P2: a lambda's tail value ---------------------------------------------

    @Test
    void aLambdaReturnsItsIfElseTailValue() throws Exception {
        String source = """
                fn main() {
                    imut f1 = fn(a: int) -> int { if a > 0 { 1 } else { 2 } }
                    imut f2 = fn(a: int) -> int { if a > 0 { 1 } else if a < 0 { 3 } else { 2 } }
                    imut f3 = fn(a: int) -> string { if a > 0 { "pos" } else { "neg" } }
                    println(toString(f1(5)) + " " + toString(f1(-5)))
                    println(toString(f2(5)) + " " + toString(f2(-5)) + " " + toString(f2(0)))
                    println(f3(5) + " " + f3(-5))
                }
                """;
        assertEquals("1 2\n1 3 2\npos neg", run(source, "LambdaIfTail"));
        assertEquals("1 2\n1 3 2\npos neg", interpret(source, "LambdaIfTail"));
    }

    @Test
    void anInlineComparatorWithAnIfElseTailSorts() throws Exception {
        String source = """
                fn main() {
                    mut xs = ["b", "c", "a"]
                    sort(xs, fn(a: string, b: string) -> int { if a < b { -1 } else if a > b { 1 } else { 0 } })
                    println(toString(xs))
                }
                """;
        assertEquals("[a, b, c]", run(source, "LambdaComparator"));
        assertEquals("[a, b, c]", interpret(source, "LambdaComparator"));
    }

    @Test
    void aLambdaTailValueIsWidenedToItsDeclaredType() throws Exception {
        String source = """
                fn main() {
                    imut f = fn() -> float { 3 }
                    println(toString(f()))
                }
                """;
        assertEquals("3.0", run(source, "LambdaWidenTail"));
        assertEquals("3.0", interpret(source, "LambdaWidenTail"));
    }

    // --- helpers -------------------------------------------------------------

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
        Thread.currentThread().setContextClassLoader(loader);
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
