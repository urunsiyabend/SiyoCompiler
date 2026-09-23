package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shapes a program can be written in: mixed numeric arithmetic, a loop that
 * runs before it checks, a set written as a literal, and a call chain that does
 * not have to be unwound into nested calls.
 */
class ErgonomicsTest {

    // --- int promotes to float ------------------------------------------------

    @Test
    void anIntAndAFloatAddWithoutAHandWrittenConversion() throws Exception {
        String source = """
                fn main() { println(toString(1 + 2.5)) }
                """;
        assertEquals("3.5", run(source, "IntPlusFloat"));
        assertEquals("3.5", interpret(source, "IntPlusFloat"));
    }

    @Test
    void everyArithmeticOperatorMeetsAtTheWiderType() throws Exception {
        String source = """
                fn main() {
                    imut a = 3
                    imut b = 1.5
                    println(toString(a + b))
                    println(toString(a - b))
                    println(toString(a * b))
                    println(toString(a / b))
                }
                """;
        assertEquals("4.5\n1.5\n4.5\n2.0", run(source, "MixedArithmetic"));
        assertEquals("4.5\n1.5\n4.5\n2.0", interpret(source, "MixedArithmetic"));
    }

    @Test
    void aMixedComparisonIsAnswered() throws Exception {
        String source = """
                fn main() {
                    println(toString(1 < 2.5))
                    println(toString(3.0 == 3))
                    println(toString(4 > 4.5))
                }
                """;
        assertEquals("true\ntrue\nfalse", run(source, "MixedCompare"));
        assertEquals("true\ntrue\nfalse", interpret(source, "MixedCompare"));
    }

    @Test
    void anIntReachesAFloatParameter() throws Exception {
        String source = """
                fn half(x: float) -> float { x / 2.0 }
                fn main() { println(toString(half(5))) }
                """;
        assertEquals("2.5", run(source, "IntToFloatParam"));
        assertEquals("2.5", interpret(source, "IntToFloatParam"));
    }

    @Test
    void anIntIsReturnedFromAFloatFunction() throws Exception {
        String source = """
                fn three() -> float { 3 }
                fn threeToo() -> float { return 3 }
                fn main() {
                    println(toString(three()))
                    println(toString(threeToo()))
                }
                """;
        assertEquals("3.0\n3.0", run(source, "IntToFloatReturn"));
        assertEquals("3.0\n3.0", interpret(source, "IntToFloatReturn"));
    }

    @Test
    void anIntAndALongAlsoMeetAtTheWiderType() throws Exception {
        String source = """
                fn main() {
                    imut big = 9000000000L
                    imut small = 2
                    println(toString(big * small))
                    println(toString(small * big))
                }
                """;
        assertEquals("18000000000\n18000000000", run(source, "IntTimesLong"));
        assertEquals("18000000000\n18000000000", interpret(source, "IntTimesLong"));
    }

    @Test
    void aFloatIsNotNarrowedToAnInt() {
        assertEquals("Parameter 'n' expects type <class java.lang.Integer> but was given <class java.lang.Double>",
                firstDiagnostic("""
                fn twice(n: int) -> int { n * 2 }
                fn main() { println(toString(twice(1.5))) }
                """));
    }

    // --- do-while -------------------------------------------------------------

    @Test
    void aDoWhileBodyRunsBeforeTheConditionIsChecked() throws Exception {
        String source = """
                fn main() {
                    mut i = 0
                    do { i = i + 1 } while false
                    println(toString(i))
                }
                """;
        assertEquals("1", run(source, "DoWhileOnce"));
        assertEquals("1", interpret(source, "DoWhileOnce"));
    }

    @Test
    void aDoWhileKeepsLoopingWhileItsConditionHolds() throws Exception {
        String source = """
                fn main() {
                    mut i = 0
                    do { i = i + 1 } while i < 5
                    println(toString(i))
                }
                """;
        assertEquals("5", run(source, "DoWhileLoop"));
        assertEquals("5", interpret(source, "DoWhileLoop"));
    }

    @Test
    void breakAndContinueWorkInsideADoWhile() throws Exception {
        String source = """
                fn main() {
                    mut i = 0
                    mut kept = 0
                    do {
                        i = i + 1
                        if i == 2 { continue }
                        if i == 5 { break }
                        kept = kept + 1
                    } while i < 100
                    println(toString(i) + " " + toString(kept))
                }
                """;
        assertEquals("5 3", run(source, "DoWhileJumps"));
        assertEquals("5 3", interpret(source, "DoWhileJumps"));
    }

    @Test
    void aNestedDoWhileKeepsItsOwnLoop() throws Exception {
        String source = """
                fn main() {
                    mut total = 0
                    mut i = 0
                    do {
                        mut j = 0
                        do {
                            total = total + 1
                            j = j + 1
                        } while j < 2
                        i = i + 1
                    } while i < 3
                    println(toString(total))
                }
                """;
        assertEquals("6", run(source, "NestedDoWhile"));
        assertEquals("6", interpret(source, "NestedDoWhile"));
    }

    // --- set literals ---------------------------------------------------------

    @Test
    void aSetIsWrittenAsALiteral() throws Exception {
        String source = """
                fn main() {
                    imut s = #{1, 2, 3}
                    println(toString(len(s)))
                }
                """;
        assertEquals("3", run(source, "SetLiteral"));
        assertEquals("3", interpret(source, "SetLiteral"));
    }

    @Test
    void aSetLiteralKeepsOneOfEachValue() throws Exception {
        String source = """
                fn main() {
                    imut s = #{1, 2, 2, 3, 1}
                    println(toString(len(s)))
                }
                """;
        assertEquals("3", run(source, "SetLiteralDedup"));
        assertEquals("3", interpret(source, "SetLiteralDedup"));
    }

    @Test
    void anEmptySetLiteralIsWritten() throws Exception {
        String source = """
                fn main() {
                    imut s = #{}
                    println(toString(len(s)))
                }
                """;
        assertEquals("0", run(source, "EmptySetLiteral"));
        assertEquals("0", interpret(source, "EmptySetLiteral"));
    }

    @Test
    void aSetLiteralIsAskedWhetherItHoldsAValue() throws Exception {
        String source = """
                fn main() {
                    imut s = #{"red", "green"}
                    println(toString(s.has("red")) + " " + toString(s.has("blue")))
                }
                """;
        assertEquals("true false", run(source, "SetLiteralHas"));
        assertEquals("true false", interpret(source, "SetLiteralHas"));
    }

    @Test
    void aSetLiteralTakesComputedElements() throws Exception {
        String source = """
                fn main() {
                    imut n = 2
                    imut s = #{n, n * 2, n + 1}
                    println(toString(len(s)))
                }
                """;
        assertEquals("3", run(source, "SetLiteralComputed"));
        assertEquals("3", interpret(source, "SetLiteralComputed"));
    }

    @Test
    void lengthIsDefinedForAMapAsWellAsASet() throws Exception {
        String source = """
                fn main() {
                    imut m = {"a": 1, "b": 2}
                    println(toString(len(m)))
                }
                """;
        assertEquals("2", run(source, "MapLength"));
        assertEquals("2", interpret(source, "MapLength"));
    }

    // --- method chaining ------------------------------------------------------

    @Test
    void aBuiltinIsWrittenAsAMethodOnItsFirstArgument() throws Exception {
        String source = """
                fn main() { println("  hi  ".trim().toUpper()) }
                """;
        assertEquals("HI", run(source, "BuiltinAsMethod"));
        assertEquals("HI", interpret(source, "BuiltinAsMethod"));
    }

    @Test
    void aChainRunsOverTheResultOfACall() throws Exception {
        String source = """
                fn greeting() -> string { "  hello world  " }
                fn main() { println(greeting().trim().toUpper()) }
                """;
        assertEquals("HELLO WORLD", run(source, "ChainOnCallResult"));
        assertEquals("HELLO WORLD", interpret(source, "ChainOnCallResult"));
    }

    @Test
    void aListIsTransformedAndMeasuredInOneChain() throws Exception {
        String source = """
                fn main() {
                    imut a = [3, 1, 2]
                    println(toString(a.map(fn(x: int) -> int { x * 2 })))
                    println(toString(a.filter(fn(x: int) -> bool { x > 1 }).len()))
                }
                """;
        assertEquals("[6, 2, 4]\n2", run(source, "ListChain"));
        assertEquals("[6, 2, 4]\n2", interpret(source, "ListChain"));
    }

    @Test
    void aUserFunctionIsAlsoWritableAsAMethod() throws Exception {
        String source = """
                fn shout(text: string) -> string { text + "!" }
                fn main() { println("hey".shout().shout()) }
                """;
        assertEquals("hey!!", run(source, "UserFunctionAsMethod"));
        assertEquals("hey!!", interpret(source, "UserFunctionAsMethod"));
    }

    @Test
    void aMethodCallWithArgumentsPassesThemAfterTheReceiver() throws Exception {
        String source = """
                fn main() {
                    println("a,b,c".split(",").len().toString())
                    println("hello".replace("l", "L"))
                }
                """;
        assertEquals("3\nheLLo", run(source, "MethodWithArgs"));
        assertEquals("3\nheLLo", interpret(source, "MethodWithArgs"));
    }

    @Test
    void aJavaObjectsOwnMethodIsNotShadowedByASiyoFunction() throws Exception {
        String source = """
                import java "java.io.File"
                fn main() {
                    imut f = File.new("pom.xml")
                    println(toString(f.exists()))
                }
                """;
        assertEquals("true", run(source, "JavaMethodWins"));
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
