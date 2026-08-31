package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Values crossing between Siyo and the JVM.
 *
 * <p>Each case here used to produce a class that failed JVM verification, so
 * the program died before its first instruction rather than reporting anything
 * a Siyo programmer could act on.
 */
class JavaBoundaryTest {

    // --- arrays -------------------------------------------------------------

    @Test
    void aSiyoArrayIsConvertedWhenAJavaMethodExpectsAByteArray() throws Exception {
        assertEquals("Hi", run("""
                import java "java.io.ByteArrayOutputStream"
                fn main() {
                    mut out = ByteArrayOutputStream.new()
                    out.write("Hi".getBytes())
                    println(out.toString("UTF-8"))
                }
                """, "ByteArrayArg"));
    }

    @Test
    void aByteArrayRoundTripsThroughSiyo() throws Exception {
        assertEquals("5\ncafé", run("""
                import java "java.lang.String"
                import java "java.io.ByteArrayOutputStream"
                fn main() {
                    mut bytes = "café".getBytes("UTF-8")
                    println(toString(len(bytes)))
                    mut out = ByteArrayOutputStream.new()
                    out.write(bytes)
                    println(out.toString("UTF-8"))
                }
                """, "ByteArrayRoundTrip"));
    }

    @Test
    void anArrayArgumentIsConvertedWhenItArrivesThroughAnObjectParameter() throws Exception {
        // The static type is erased, so the conversion has to happen on the
        // value rather than on what the compiler thinks the value is.
        assertEquals("Hi", run("""
                import java "java.io.ByteArrayOutputStream"
                fn write(out: object, data: object) { out.write(data) }
                fn main() {
                    mut out = ByteArrayOutputStream.new()
                    write(out, "Hi".getBytes())
                    println(out.toString("UTF-8"))
                }
                """, "ErasedArrayArg"));
    }

    @Test
    void aStringArrayIsConvertedForAJavaMethod() throws Exception {
        assertEquals("a-b-c", run("""
                import java "java.lang.String"
                fn main() {
                    mut parts: string[] = ["a", "b", "c"]
                    println(String.join("-", parts))
                }
                """, "StringArrayArg"));
    }

    // --- boxing -------------------------------------------------------------

    @Test
    void aPrimitiveIsBoxedWhenItReachesAnObjectParameter() throws Exception {
        assertEquals("5\ntrue\n2.5\nhello", run("""
                fn show(v: object) -> string { toString(v) }
                fn main() {
                    println(show(5))
                    println(show(true))
                    println(show(2.5))
                    println(show("hello"))
                }
                """, "BoxToObject"));
    }

    @Test
    void aPrimitiveIsBoxedWhenItReachesAnObjectStructField() throws Exception {
        assertEquals("7", run("""
                struct Box { v: object }
                fn main() {
                    mut b = Box { v: 7 }
                    println(toString(b.v))
                }
                """, "BoxIntoField"));
    }

    // --- unboxing -----------------------------------------------------------

    @Test
    void anErasedJavaResultCanBeComparedNumerically() throws Exception {
        assertEquals("5", run("""
                import java "java.io.ByteArrayInputStream"
                fn countBytes(inp: object) -> int {
                    mut n = 0
                    mut done = false
                    while !done {
                        imut b = inp.read()
                        if b < 0 { done = true } else { n += 1 }
                    }
                    n
                }
                fn main() {
                    mut stream = ByteArrayInputStream.new("hello".getBytes())
                    println(toString(countBytes(stream)))
                }
                """, "ErasedCompareLoop"));
    }

    @Test
    void everyOrderingOperatorWorksOnAnErasedOperand() throws Exception {
        assertEquals("true false true true false true", run("""
                fn erase(v: object) -> object { v }
                fn main() {
                    imut five = erase(5)
                    println(toString(five < 10) + " " + toString(five > 10)
                        + " " + toString(five <= 5) + " " + toString(five >= 5)
                        + " " + toString(five == 6) + " " + toString(five != 6))
                }
                """, "ErasedOrdering"));
    }

    @Test
    void anErasedOperandComparesAgainstALongWithoutTruncating() throws Exception {
        // Siyo has no long literal, so the wide values come from toLong().
        assertEquals("true false", run("""
                fn erase(v: object) -> object { v }
                fn main() {
                    imut big = erase(toLong(2000000000))
                    println(toString(big > toLong(1000000000))
                        + " " + toString(big < toLong(1000000000)))
                }
                """, "ErasedLongOrdering"));
    }

    // --- helper -------------------------------------------------------------

    private String run(String source, String className) throws Exception {
        SyntaxTree tree = SyntaxTree.parse(source);
        Compilation compilation = new Compilation(tree, new ModuleRegistry(), className + ".siyo");
        byte[] bytes = compilation.compile(className);
        if (bytes == null) {
            String message = compilation.getGlobalScope().getDiagnostics().hasNext()
                    ? compilation.getGlobalScope().getDiagnostics().get(0).getMessage()
                    : tree.diagnostics().hasNext()
                    ? tree.diagnostics().get(0).getMessage()
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
