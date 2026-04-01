package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that compiled bytecode produces the same output as the interpreter.
 */
class CompilationTest {

    @ParameterizedTest
    @MethodSource("compileAndRunTestData")
    void compiledOutputMatchesInterpreter(String name, String source) throws Exception {
        // Run with interpreter
        String interpreterOutput = runWithInterpreter(source);

        // Compile to bytecode and run
        String compilerOutput = runCompiled(source, name);

        assertEquals(interpreterOutput, compilerOutput,
                "Output mismatch for: " + name);
    }

    private String runWithInterpreter(String source) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            SyntaxTree tree = SyntaxTree.parse(source);
            Compilation compilation = new Compilation(tree);
            HashMap<VariableSymbol, Object> variables = new HashMap<>();
            EvaluationResult result = compilation.evaluate(variables);

            if (result._diagnostics.size() > 0) {
                fail("Interpreter diagnostics: " + result._diagnostics.get(0).getMessage());
            }

            // Only print the last value if it's not from a statement
            // (compiler prints last expression value in main, so match that behavior)
        } finally {
            System.setOut(oldOut);
        }

        return baos.toString().trim();
    }

    private String runCompiled(String source, String className) throws Exception {
        SyntaxTree tree = SyntaxTree.parse(source);
        Compilation compilation = new Compilation(tree);
        byte[] bytecode = compilation.compile(className);

        if (bytecode == null) {
            fail("Compilation failed for: " + className);
        }

        // Load and run the class
        ByteClassLoader loader = new ByteClassLoader(className, bytecode);
        Class<?> cls = loader.loadClass(className);

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } finally {
            System.setOut(oldOut);
        }

        return baos.toString().trim();
    }

    private static class ByteClassLoader extends ClassLoader {
        private final String name;
        private final byte[] bytes;

        ByteClassLoader(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(this.name)) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    static Object[][] compileAndRunTestData() {
        return new Object[][] {
            {"Arithmetic", "println(toString(3 + 4 * 2))"},
            {"Variables", "mut x = 10\nmut y = 20\nprintln(toString(x + y))"},
            {"IfElse", "mut x = 5\nif x > 3 { println(\"big\") } else { println(\"small\") }"},
            {"WhileLoop", "mut sum = 0\nmut i = 1\nwhile i <= 10 { sum = sum + i\ni = i + 1 }\nprintln(toString(sum))"},
            {"ForLoop", "mut result = 1\nfor mut i = 1 i <= 5 i = i + 1 { result = result * i }\nprintln(toString(result))"},
            {"Functions", "fn double(x: int) -> int { x * 2 }\nprintln(toString(double(21)))"},
            {"Recursion", "fn fib(n: int) -> int {\nif n <= 1 return n\nreturn fib(n - 1) + fib(n - 2)\n}\nprintln(toString(fib(10)))"},
            {"Strings", "mut s = \"Hello\" + \", \" + \"World!\"\nprintln(s)"},
            {"Arrays", "mut arr = [10, 20, 30]\narr[1] = 99\nprintln(toString(arr[0] + arr[1] + arr[2]))"},
            {"Structs", "struct Point { x: int, y: int }\nmut p = Point { x: 3, y: 4 }\nprintln(toString(p.x + p.y))"},
            {"StructMutation", "struct Counter { value: int }\nmut c = Counter { value: 0 }\nc.value = 42\nprintln(toString(c.value))"},
            {"Enums", "enum Dir { N, E, S, W }\nprintln(toString(Dir.S))"},
            {"BreakContinue", "mut sum = 0\nfor mut i = 0 i < 20 i = i + 1 {\nif i == 10 break\nif i % 2 == 0 continue\nsum = sum + i\n}\nprintln(toString(sum))"},
            {"Float", "mut x = 3.14\nmut y = 2.0\nprintln(toString(x * y))"},
            {"Null", "mut x = null\nif x == null { println(\"null!\") }"},
            {"NestedCalls", "fn add(a: int, b: int) -> int { a + b }\nfn mul(a: int, b: int) -> int { a * b }\nprintln(toString(add(mul(3, 4), 5)))"},
            {"StringLen", "println(toString(len(\"hello\")))"},
            {"BoolOps", "mut a = true\nmut b = false\nprintln(toString(a && b))\nprintln(toString(a || b))"},
            {"CompoundAssign", "mut x = 10\nx += 5\nx -= 2\nx *= 3\nprintln(toString(x))"},
            {"ForIn", "mut sum = 0\nfor x in [1, 2, 3, 4, 5] { sum += x }\nprintln(toString(sum))"},
            {"TryCatch", "try { error(\"boom\") } catch e { println(e) }"},
            {"TryCatchNoError", "mut x = 0\ntry { x = 42 } catch e { x = -1 }\nprintln(toString(x))"},
            {"Range", "mut sum = 0\nfor i in range(0, 5) { sum += i }\nprintln(toString(sum))"},
            {"StructAccess", "struct P { x: int, y: int }\nmut p = P { x: 3, y: 4 }\np.x = 10\nprintln(toString(p.x + p.y))"},
            {"ArrayMutation", "mut arr = [1, 2, 3]\narr[0] = 99\nprintln(toString(arr[0]))"},
            {"EnumValues", "enum Dir { N, E, S, W }\nprintln(toString(Dir.N))\nprintln(toString(Dir.W))"},
            {"GlobalVarFunc", "mut x = 10\nfn getX() -> int { x }\nprintln(toString(getX()))"},
            {"ImplicitReturn", "fn double(n: int) -> int { n * 2 }\nprintln(toString(double(21)))"},
            {"MultiForIn", "for a in [1, 2] { println(toString(a)) }\nfor b in [3, 4] { println(toString(b)) }"},
            // Variable shadowing
            {"VarShadowBlock", "mut x = 1\n{ mut x = 2\nprintln(toString(x)) }\nprintln(toString(x))"},
            {"VarShadowDeep", "mut x = 1\nprintln(toString(x))\n{ mut x = 2\nprintln(toString(x))\n{ mut x = 3\nprintln(toString(x)) }\nprintln(toString(x)) }\nprintln(toString(x))"},
            {"VarShadowIf", "mut x = 10\nif true { mut x = 20\nprintln(toString(x)) }\nprintln(toString(x))"},
            // Nested try-catch
            {"NestedTryCatch", "mut x = 0\ntry { try { error(\"inner\") } catch e { x = 1 }\nx = x + 10 } catch e { x = -1 }\nprintln(toString(x))"},
            {"NestedTryNoRethrow", "mut r = \"\"\ntry { r = r + \"a\"\ntry { r = r + \"b\"\nerror(\"x\") } catch e { r = r + \"c\" }\nr = r + \"d\" } catch e { r = r + \"e\" }\nprintln(r)"},
            // Continue in for-in
            {"ForInContinue", "mut sum = 0\nfor x in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] {\nif x % 2 == 0 continue\nsum += x }\nprintln(toString(sum))"},
            // Lambda with capture
            {"LambdaCapture", "imut base = 10\nimut add = fn(x: int) -> int { x + base }\nprintln(toString(add(5)))\nprintln(toString(add(20)))"},
            // removeAt and pop
            {"RemoveAt", "mut arr = [10, 20, 30]\nremoveAt(arr, 1)\nprintln(toString(len(arr)))\nprintln(toString(arr[0]))\nprintln(toString(arr[1]))"},
            {"Pop", "mut arr = [10, 20, 30]\nimut last = pop(arr)\nprintln(toString(last))\nprintln(toString(len(arr)))"},
            // parseInt with invalid input
            {"ParseIntSafe", "println(toString(parseInt(\"42\")))\nprintln(toString(parseInt(\"hello\")))"},
            // toUpper/toLower
            {"ToUpperLower", "println(toUpper(\"hello\"))\nprintln(toLower(\"WORLD\"))"},
            // toInt(string) overload
            {"ToIntStr", "println(toString(toInt(\"42\")))\nprintln(toString(toInt(\"bad\")))"},
            // toDouble
            {"ToDouble", "println(toString(toDouble(42)))"},
            // fn returning fn (closure factory)
            {"ClosureFactory", "fn makeMultiplier(factor: int) -> fn(int) -> int {\nreturn fn(x: int) -> int { x * factor }\n}\nimut triple = makeMultiplier(3)\nprintln(toString(triple(5)))"},
        };
    }
}
