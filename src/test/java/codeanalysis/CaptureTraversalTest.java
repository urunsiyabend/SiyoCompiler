package codeanalysis;

import codeanalysis.binding.BoundNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A variable may be used from anywhere in a closure's body. Each form of
 * expression and statement is tried here with a captured variable as its only
 * use, because a form the capture walk does not enter silently reads null.
 */
class CaptureTraversalTest {
    private final DogfoodRegressionTest harness = new DogfoodRegressionTest();

    private static final String PRELUDE = """
            import java "java.lang.Integer"
            import "std/strings"
            type R = A(int) | B
            struct S { v: int }
            interface Named { fn name() -> int }
            impl Named for S { fn name(self) -> int { self.v } }
            impl S { fn plus(self, n: int) -> int { self.v + n } }
            actor struct Box { n: int }
            actor struct Held { n: int }
            impl Held {
                fn new(v: int) -> Held { Held { n: v } }
                fn get(self) -> int { self.n }
            }
            impl Box {
                fn new() -> Box { Box { n: 0 } }
                fn set(self, v: int) { self.n = v }
                fn get(self) -> int { self.n }
            }
            fn id(n: int) -> int { n }
            fn sendTo(c: channel, n: int) { c.send(n) }
            fn asNamed(n: Named) -> int { n.name() }
            fn generic<T>(v: T) -> T { v }
            """;

    /**
     * @param form     What it exercises.
     * @param parent   What the enclosing function declares, always including x.
     * @param body     Statements of the closure, ending in the expression shown.
     * @param expected What toString of that expression prints.
     */
    private record Form(String form, String parent, String body, String expected) {
    }

    private static final String X = "imut x = 41";
    private static final String XS = "imut x = [41]";
    private static final String XP = "imut x = S { v: 41 }";
    private static final String XF = "imut x = fn(n: int) -> int { n + 40 }";
    private static final String XT = "imut x = \"41\"";

    private static final List<Form> FORMS = List.of(
            new Form("plain read", X, "x", "41"),
            new Form("union argument", X, "match A(x) { A(v) => v, B => 0 }", "41"),
            new Form("union argument through a call", X, "match A(id(x)) { A(v) => v, B => 0 }", "41"),
            new Form("cast operand", XT, "x as String", "41"),
            new Form("array element", X, "[x][0]", "41"),
            new Form("map value", X, "{\"k\": x}", "{k: 41}"),
            new Form("map key", X, "{x: 1}", "{41: 1}"),
            new Form("set element", X, "#{x}.has(41)", "true"),
            new Form("struct field", X, "S { v: x }.v", "41"),
            new Form("index target", XS, "x[0]", "41"),
            new Form("index", "imut x = 1", "[0, 41][x]", "41"),
            new Form("index assignment target", XS, "x[0] = 41\nx[0]", "41"),
            new Form("index assignment index", "imut x = 0", "mut a = [1]\na[x] = 41\na[0]", "41"),
            new Form("index assignment value", X, "mut a = [1]\na[0] = x\na[0]", "41"),
            new Form("member access", XP, "x.v", "41"),
            new Form("member assignment target", XP, "x.v = 41\nx.v", "41"),
            new Form("member assignment value", X, "imut q = S { v: 0 }\nq.v = x\nq.v", "41"),
            new Form("binary left", X, "x + 0", "41"),
            new Form("binary right", X, "0 + x", "41"),
            new Form("unary", "imut x = -41", "-x", "41"),
            new Form("conversion", X, "imut f: float = x\nf", "41.0"),
            new Form("call argument", X, "id(x)", "41"),
            new Form("generic call argument", X, "generic(x)", "41"),
            new Form("builtin argument", XT, "len(x)", "2"),
            new Form("closure call target", XF, "x(1)", "41"),
            new Form("closure call argument", X, "imut f = fn(n: int) -> int { n }\nf(x)", "41"),
            new Form("java call argument", X, "Integer.toString(x)", "41"),
            new Form("java call target", "imut x = strings.builder()", "x.append(\"41\")\ntoString(x)", "41"),
            new Form("method target", XP, "x.plus(0)", "41"),
            new Form("method argument", X, "S { v: 0 }.plus(x)", "41"),
            new Form("interface call target", XP, "asNamed(x)", "41"),
            new Form("actor method argument", X, "imut b = spawn Box.new()\nb.set(x)\nb.get()", "41"),
            new Form("actor spawn argument", X, "imut b = spawn Held.new(x)\nb.get()", "41"),
            new Form("spawn of a call", X, "imut c = channel(1)\nscope { spawn sendTo(c, x) }\nc.receive()", "41"),
            new Form("send", X, "imut b = spawn Box.new()\nsend b.set(x)\nb.get() * 0 + x", "41"),
            new Form("interpolation", X, "\"$x\"", "41"),
            new Form("interpolated expression", X, "\"${x + 0}\"", "41"),
            new Form("if condition", X, "mut r = 0\nif x > 0 { r = 41 }\nr", "41"),
            new Form("if branch", X, "mut r = 0\nif true { r = x }\nr", "41"),
            new Form("else branch", X, "mut r = 0\nif false { r = 1 } else { r = x }\nr", "41"),
            new Form("else if condition", X, "mut r = 0\nif false { r = 1 } else if x > 0 { r = 41 }\nr", "41"),
            new Form("if expression", X, "if true { x } else { 0 }", "41"),
            new Form("while condition", X, "mut i = 0\nwhile i < x { i = i + 1 }\ni", "41"),
            new Form("while body", X, "mut r = 0\nmut i = 0\nwhile i < 1 { r = x\ni = i + 1 }\nr", "41"),
            new Form("do while", X, "mut r = 0\ndo { r = x } while r < 0\nr", "41"),
            new Form("for initializer", X, "mut r = 0\nfor mut i = x i < 42 i = i + 1 { r = i }\nr", "41"),
            new Form("for condition", X, "mut r = 0\nfor mut i = 0 i < x i = i + 1 { r = i + 1 }\nr", "41"),
            new Form("for iterator", X, "mut r = 0\nfor mut i = 0 i < 41 i = i + x { r = 41 }\nr", "41"),
            new Form("for body", X, "mut r = 0\nfor mut i = 0 i < 1 i = i + 1 { r = x }\nr", "41"),
            new Form("for in collection", XS, "mut r = 0\nfor v in x { r = v }\nr", "41"),
            new Form("for in body", X, "mut r = 0\nfor v in [1] { r = x }\nr", "41"),
            new Form("break in a loop leaves x readable", X, "mut r = 0\nfor v in [1, 2] {\nr = x\nbreak\n}\nr", "41"),
            new Form("match target", X, "match x { 41 => 1, _ => 0 }", "1"),
            new Form("match pattern", X, "match 41 { x => 1, _ => 0 }", "1"),
            new Form("match arm", X, "match 1 { 1 => x, _ => 0 }", "41"),
            new Form("match arm statements", X, "imut r = match 1 { 1 => { imut y = x\ny }, _ => 0 }\nr", "41"),
            new Form("match default", X, "match 1 { 2 => 0, _ => x }", "41"),
            new Form("variant arm", X, "match A(1) { A(v) => v + x - 1, B => 0 }", "41"),
            new Form("try body", X, "mut r = 0\ntry { r = x } catch e { r = 0 }\nr", "41"),
            new Form("catch body", X, "mut r = 0\ntry { throw 1 } catch e { r = x }\nr", "41"),
            new Form("try expression", X, "imut r = try { x } catch e { 0 }\nr", "41"),
            new Form("catch expression", X, "imut r = try { id(1 / 0) } catch e { x }\nr", "41"),
            new Form("throw payload", X, "imut r = try { throw x } catch e { e }\nr", "41"),
            new Form("return value", X, "if true { return toString(x) }\n0", "41"),
            new Form("declaration", X, "imut y = x\ny", "41"),
            new Form("assignment value", X, "mut y = 0\ny = x\ny", "41"),
            new Form("compound assignment value", X, "mut y = 0\ny += x\ny", "41"),
            new Form("nested block", X, "mut r = 0\n{ r = x }\nr", "41"),
            new Form("scope", X, "mut r = 0\nscope { r = x }\nr", "41"),
            new Form("spawn in scope", X, "imut c = channel(1)\nscope { spawn { c.send(x) } }\nc.receive()", "41"),
            new Form("nested lambda", X, "imut f = fn() -> int { x }\nf()", "41"),
            new Form("doubly nested lambda", X, "imut f = fn() -> int {\nimut g = fn() -> int { x }\ng()\n}\nf()", "41"),
            new Form("lambda passed to map", X, "map([1], fn(n: int) -> int { n + x - 1 })[0]", "41"),
            new Form("lambda in a union argument", X, "imut f = match A(1) { A(v) => fn() -> int { x }, B => fn() -> int { 0 } }\nf()", "41"),
            new Form("lambda in an array", X, "[fn() -> int { x }][0]()", "41"),
            new Form("named function called", X, "fn read() -> int { x }\nread()", "41"),
            new Form("named function referenced", X, "fn read() -> int { x }\nimut f = read\nf()", "41"));

    private static String namedFunction(Form f) {
        return PRELUDE + """
                fn make() -> fn() -> string {
                    %s
                    fn k() -> string {
                        %s
                    }
                    k
                }
                fn main() {
                    imut f = make()
                    println(f())
                }
                """.formatted(f.parent(), wrapTail(f.body()));
    }

    private static String lambda(Form f) {
        return PRELUDE + """
                fn make() -> fn() -> string {
                    %s
                    fn() -> string {
                        %s
                    }
                }
                fn main() {
                    imut f = make()
                    println(f())
                }
                """.formatted(f.parent(), wrapTail(f.body()));
    }

    /** The last line of a body is its value, and every form is printed the same way. */
    private static String wrapTail(String body) {
        int lastLine = body.lastIndexOf('\n');
        String head = lastLine < 0 ? "" : body.substring(0, lastLine + 1);
        String tail = body.substring(lastLine + 1);
        return head + "toString(" + tail + ")";
    }

    private static String className(String prefix, Form f) {
        return prefix + f.form().replaceAll("[^a-zA-Z]", "");
    }

    @TestFactory
    Stream<DynamicTest> anEscapedNamedFunctionCapturesFromEveryForm() {
        return FORMS.stream().map(f -> DynamicTest.dynamicTest(f.form(), () -> {
            String source = namedFunction(f);
            assertEquals(f.expected(), harness.interpret(source, className("Fn", f)), source);
        }));
    }

    @TestFactory
    Stream<DynamicTest> compiledANamedFunctionUsingAFormIsReportedNeverMiscompiled() {
        return FORMS.stream().map(f -> DynamicTest.dynamicTest(f.form(), () -> {
            // A global of the same name is what a missed capture would read silently.
            String source = "imut x = 1\n" + namedFunction(f);
            String outcome;
            try {
                outcome = "ran: " + harness.run(source, className("FnGlobal", f));
            } catch (AssertionError expected) {
                outcome = expected.getMessage();
            }
            assertTrue(outcome.contains("from the function around it"), f.form() + ": " + outcome);
        }));
    }

    @TestFactory
    Stream<DynamicTest> aLambdaCapturesFromEveryForm() {
        return FORMS.stream().map(f -> DynamicTest.dynamicTest(f.form(), () -> {
            String source = lambda(f);
            assertEquals(f.expected(), harness.interpret(source, className("Lambda", f)), source);
        }));
    }

    @TestFactory
    Stream<DynamicTest> aCompiledLambdaCapturesFromEveryForm() {
        return FORMS.stream().map(f -> DynamicTest.dynamicTest(f.form(), () -> {
            String source = lambda(f);
            if (f.form().startsWith("named function")) {
                // Its function uses x from around it, which is not compiled yet.
                String outcome;
                try {
                    outcome = "ran: " + harness.run(source, className("CompiledLambda", f));
                } catch (AssertionError expected) {
                    outcome = expected.getMessage();
                }
                assertTrue(outcome.contains("from the function around it"), outcome);
                return;
            }
            assertEquals(f.expected(), harness.run(source, className("CompiledLambda", f)), source);
        }));
    }

    @Test
    void deeplyNestedClosuresDoNotCostExponentialTime() {
        StringBuilder spawns = new StringBuilder("println(\"x\")");
        for (int i = 0; i < 40; i++) {
            spawns = new StringBuilder("scope { spawn { " + spawns + " } }");
        }
        String source = "fn main() {\n" + spawns + "\n}\n";
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () -> {
            StringBuilder nested = new StringBuilder("return 7");
            for (int i = 0; i < 40; i++) {
                nested = new StringBuilder("imut f = fn() -> int { " + nested + " }\nreturn f()");
            }
            String deep = "fn main() {\nimut g = fn() -> int { " + nested + " }\nprintln(toString(g()))\n}\n";
            assertEquals("7", harness.run(deep, "DeepLambdas"));
            assertEquals("x", harness.run(source, "DeepSpawns"));
        });
    }

    @Test
    void aLambdaInterpretedWalksACapturedMapOrSet() throws Exception {
        String source = """
                fn make() -> fn() -> string {
                    imut m = {"k": 1}
                    mut s: Set<int> = #{41}
                    fn() -> string {
                        mut r = ""
                        for k in m { r = r + k }
                        for v in s { r = r + toString(v) }
                        r
                    }
                }
                fn main() {
                    imut f = make()
                    println(f())
                }
                """;
        assertEquals("k41", harness.interpret(source, "CapturedMapSet"));
    }

    /**
     * Everything a bound node's fields may hold. A field of a type this does not
     * know, such as a new holder of nodes, fails here until it has been looked at.
     */
    private static final Set<String> LEAF_FIELD_TYPES = Set.of(
            "java.lang.Class", "java.lang.String", "java.lang.Object", "codeanalysis.VariableSymbol",
            "codeanalysis.FunctionSymbol", "codeanalysis.LabelSymbol", "codeanalysis.ParameterSymbol",
            "codeanalysis.StructSymbol", "codeanalysis.UnionSymbol", "codeanalysis.InterfaceSymbol",
            "codeanalysis.JavaClassInfo", "codeanalysis.JavaMethodSignature", "codeanalysis.JavaResolvedType",
            "codeanalysis.binding.BoundBinaryOperator", "codeanalysis.binding.BoundUnaryOperator",
            "codeanalysis.binding.BoundBinaryOperatorType", "codeanalysis.binding.BoundUnaryOperatorType",
            "codeanalysis.text.TextSpan");

    @Test
    void everyBoundNodeHoldsOnlyWhatTheTraversalUnderstands() throws Exception {
        List<String> unknown = new ArrayList<>();
        int nodes = 0;
        Path binding = Path.of("target", "classes", "codeanalysis", "binding");
        try (Stream<Path> classes = Files.list(binding)) {
            for (Path file : classes.filter(p -> p.getFileName().toString().endsWith(".class")).toList()) {
                String simple = file.getFileName().toString().replace(".class", "");
                Class<?> type = Class.forName("codeanalysis.binding." + simple, false, BoundNode.class.getClassLoader());
                if (!BoundNode.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) continue;
                nodes++;
                for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        if (!understood(field.getGenericType())) {
                            unknown.add(simple + "." + field.getName() + ": " + field.getGenericType());
                        }
                    }
                }
            }
        }
        assertTrue(nodes > 30, "only found " + nodes + " node classes");
        assertEquals(List.of(), unknown);
    }

    private static boolean understood(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw == Class.class) return true;
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (!understood(argument)) return false;
            }
            return raw == List.class || raw == java.util.ArrayList.class || raw == Set.class
                    || raw == java.util.Map.class || raw == java.util.LinkedHashMap.class;
        }
        if (type instanceof Class<?> c) {
            if (c.isRecord()) {
                for (java.lang.reflect.RecordComponent component : c.getRecordComponents()) {
                    if (!component.getType().isPrimitive() && !understood(component.getGenericType())) return false;
                }
                return true;
            }
            return BoundNode.class.isAssignableFrom(c) || LEAF_FIELD_TYPES.contains(c.getName());
        }
        return false;
    }
}
