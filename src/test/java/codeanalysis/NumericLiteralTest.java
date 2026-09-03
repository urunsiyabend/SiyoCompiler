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
 * Numeric literals: the long suffix, hexadecimal notation, and the widening
 * rule that decides between {@code int} and {@code long}.
 */
class NumericLiteralTest {

    // --- long literals -------------------------------------------------------

    @Test
    void anLSuffixMakesALongLiteral() throws Exception {
        String source = """
                fn main() {
                    imut big: long = 9000000000L
                    println(toString(big))
                }
                """;
        assertEquals("9000000000", run(source, "LongSuffix"));
        assertEquals("9000000000", interpret(source, "LongSuffix"));
    }

    @Test
    void aLowercaseSuffixIsAlsoALong() throws Exception {
        assertEquals("42", run("""
                fn main() {
                    imut n: long = 42l
                    println(toString(n))
                }
                """, "LongSuffixLower"));
    }

    @Test
    void aDecimalLiteralTooLargeForIntBecomesALong() throws Exception {
        String source = """
                fn main() {
                    imut big: long = 3000000000
                    println(toString(big + 1))
                }
                """;
        assertEquals("3000000001", run(source, "LongWidening"));
        assertEquals("3000000001", interpret(source, "LongWidening"));
    }

    @Test
    void aLongLiteralArithmeticStaysWide() throws Exception {
        String source = """
                fn main() {
                    imut ms: long = 86400000L
                    println(toString(ms * 1000L))
                }
                """;
        assertEquals("86400000000", run(source, "LongProduct"));
        assertEquals("86400000000", interpret(source, "LongProduct"));
    }

    @Test
    void aLongLiteralMixesWithAnInt() throws Exception {
        assertEquals("2147483648", run("""
                fn main() {
                    imut base: long = 2147483647L
                    println(toString(base + 1))
                }
                """, "LongPlusInt"));
    }

    @Test
    void aLongLiteralPassesAsAFunctionArgument() throws Exception {
        assertEquals("12000000000", run("""
                fn triple(n: long) -> long { n * 3L }
                fn main() { println(toString(triple(4000000000L))) }
                """, "LongArgument"));
    }

    // --- hexadecimal literals ------------------------------------------------

    @Test
    void aHexLiteralIsAnInt() throws Exception {
        String source = """
                fn main() {
                    imut mask = 0xFF
                    println(toString(mask))
                }
                """;
        assertEquals("255", run(source, "HexInt"));
        assertEquals("255", interpret(source, "HexInt"));
    }

    @Test
    void hexDigitsAreCaseInsensitive() throws Exception {
        assertEquals("43981\n43981", run("""
                fn main() {
                    println(toString(0xABCD))
                    println(toString(0xabcd))
                }
                """, "HexCase"));
    }

    @Test
    void anUppercaseXPrefixIsAccepted() throws Exception {
        assertEquals("16", run("""
                fn main() { println(toString(0X10)) }
                """, "HexUpperPrefix"));
    }

    @Test
    void aHexLiteralTooLargeForIntBecomesALong() throws Exception {
        String source = """
                fn main() {
                    imut all: long = 0xFFFFFFFF
                    println(toString(all))
                }
                """;
        assertEquals("4294967295", run(source, "HexWidening"));
        assertEquals("4294967295", interpret(source, "HexWidening"));
    }

    @Test
    void aHexLiteralTakesTheLongSuffix() throws Exception {
        assertEquals("255", run("""
                fn main() {
                    imut mask: long = 0xFFL
                    println(toString(mask))
                }
                """, "HexLongSuffix"));
    }

    @Test
    void aHexLiteralWorksInBitwiseStyleArithmetic() throws Exception {
        assertEquals("15", run("""
                fn main() { println(toString(0xF0 / 0x10)) }
                """, "HexArithmetic"));
    }

    // --- rejected literals ---------------------------------------------------

    @Test
    void aValueTooLargeForLongIsReported() {
        String message = firstDiagnostic("""
                fn main() { println(toString(99999999999999999999)) }
                """);
        assertTrue(message.contains("does not fit in a long"),
                "expected an out-of-range diagnostic, got: " + message);
    }

    @Test
    void aFractionalPartCannotCarryALongSuffix() {
        String message = firstDiagnostic("""
                fn main() { println(toString(1.5L)) }
                """);
        assertTrue(message.contains("not a valid long"),
                "expected an invalid-long diagnostic, got: " + message);
    }

    @Test
    void aHexLiteralWithNoDigitsIsReported() {
        String message = firstDiagnostic("""
                fn main() { println(toString(0x)) }
                """);
        assertTrue(message.contains("not a valid int"),
                "expected an invalid-int diagnostic, got: " + message);
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
