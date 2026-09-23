package codeanalysis;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Comparator;
import java.util.List;

/**
 * Runtime support methods for compiled Siyo bytecode.
 */
public class SiyoRuntime {
    /** Program arguments, set by the entry main method. */
    public static volatile String[] programArgs = new String[0];

    /** Returns program arguments as a SiyoArray. */
    public static SiyoArray getProgramArgs() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        for (String arg : programArgs) list.add(arg);
        return new SiyoArray(list, String.class);
    }


    /**
     * Sorts a list using a Siyo closure as comparator.
     * The closure is Object[]{Integer(lambdaId), Object[]{captured}}.
     * Calls closureDispatch$ on the class that defined the closure.
     */
    @SuppressWarnings("unchecked")
    public static void sortList(List<?> list, Object closureObj) {
        Object[] closure = (Object[]) closureObj;
        int lambdaId = (Integer) closure[0];
        Object[] captured = (Object[]) closure[1];

        // Use origin class name from closure[2] if available, otherwise fallback to StackWalker
        Class<?> callerClass;
        if (closure.length > 2 && closure[2] instanceof String className) {
            try {
                callerClass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("sort: class not found: " + className, e);
            }
        } else {
            StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
            callerClass = walker.walk(frames ->
                    frames.skip(1).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        }

        try {
            MethodHandle dispatch = MethodHandles.lookup().findStatic(callerClass, "closureDispatch$",
                    MethodType.methodType(Object.class, int.class, Object[].class, Object[].class));

            ((List<Object>) list).sort((a, b) -> {
                try {
                    Object result = dispatch.invoke(lambdaId, captured, new Object[]{a, b});
                    return (result instanceof Integer i) ? i : 0;
                } catch (Throwable e) {
                    return 0;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("sort: cannot invoke closure comparator", e);
        }
    }

    /**
     * Calls a closure held in its array representation.
     *
     * <p>A closure carries the class that declared it, so a call works from
     * anywhere: the higher-order builtins are implemented here rather than
     * emitted inline, and behave the same whichever class holds the lambda.</p>
     *
     * @param closureObj The closure representation.
     * @param args       The call arguments.
     * @return The closure's result.
     */
    public static Object callClosure(Object closureObj, Object... args) {
        if (!(closureObj instanceof Object[] closure) || closure.length < 2) {
            throw new RuntimeException("expected a function value");
        }
        int lambdaId = (Integer) closure[0];
        Object[] captured = (Object[]) closure[1];
        if (closure.length > 2 && closure[2] instanceof String className) {
            return dispatchClosure(className, lambdaId, captured, args);
        }
        throw new RuntimeException("function value carries no origin class");
    }

    /**
     * map(arr, fn): the array of fn applied to every element.
     *
     * @param listObj The source array.
     * @param closure The function to apply.
     * @return A new array.
     */
    public static SiyoArray mapList(Object listObj, Object closure) {
        List<?> list = (List<?>) listObj;
        SiyoArray result = new SiyoArray(new java.util.ArrayList<>(), Object.class);
        for (Object element : list) {
            result.add(callClosure(closure, element));
        }
        return result;
    }

    /**
     * filter(arr, fn): the elements fn accepts.
     *
     * @param listObj The source array.
     * @param closure The predicate.
     * @return A new array.
     */
    public static SiyoArray filterList(Object listObj, Object closure) {
        List<?> list = (List<?>) listObj;
        SiyoArray result = new SiyoArray(new java.util.ArrayList<>(),
                listObj instanceof SiyoArray source ? source.getElementType() : Object.class);
        for (Object element : list) {
            if (callClosure(closure, element) instanceof Boolean keep && keep) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * reduce(arr, fn, initial): the elements folded into one value, with the
     * accumulator as the first argument.
     *
     * @param listObj The source array.
     * @param closure The folding function.
     * @param initial The starting accumulator.
     * @return The final accumulator.
     */
    public static Object reduceList(Object listObj, Object closure, Object initial) {
        List<?> list = (List<?>) listObj;
        Object accumulator = initial;
        for (Object element : list) {
            accumulator = callClosure(closure, accumulator, element);
        }
        return accumulator;
    }

    /**
     * forEach(arr, fn): fn run on every element, for its effects.
     *
     * @param listObj The source array.
     * @param closure The function to run.
     */
    public static void forEachList(Object listObj, Object closure) {
        List<?> list = (List<?>) listObj;
        for (Object element : list) {
            callClosure(closure, element);
        }
    }

    /**
     * Dispatch a closure call across class boundaries.
     * Used when a closure created in class A is invoked by class B (e.g., module functions).
     */
    public static Object dispatchClosure(String className, int lambdaId, Object[] captured, Object[] args) {
        try {
            // Try context classloader first, then calling class's classloader
            Class<?> cls = null;
            ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader != null) {
                try { cls = Class.forName(className, true, ctxLoader); } catch (ClassNotFoundException ignored) {}
            }
            if (cls == null) {
                // Fallback: walk the stack to find the caller's classloader
                StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
                ClassLoader callerLoader = walker.walk(frames ->
                        frames.skip(1).findFirst().map(f -> f.getDeclaringClass().getClassLoader()).orElse(null));
                if (callerLoader != null) {
                    cls = Class.forName(className, true, callerLoader);
                }
            }
            if (cls == null) {
                cls = Class.forName(className);
            }
            MethodHandle dispatch = MethodHandles.lookup().findStatic(cls, "closureDispatch$",
                    MethodType.methodType(Object.class, int.class, Object[].class, Object[].class));
            return dispatch.invoke(lambdaId, captured, args);
        } catch (Throwable e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("closure dispatch failed for " + className, e);
        }
    }

    /**
     * The value a catch block binds for a thrown error.
     *
     * <p>A Siyo {@code throw} carries a payload, so the payload is what the
     * catch variable sees and a sum-type variant can be matched on. Anything
     * else crossing the boundary is a Java exception: its message, or — when it
     * has none — its type name, because binding null told the reader nothing.
     *
     * @param error The throwable caught at the boundary.
     * @return The value to bind to the catch variable.
     */
    public static Object errorPayload(Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.lang.reflect.InvocationTargetException wrapper
                && wrapper.getCause() != null) {
            cause = wrapper.getCause();
        }
        if (cause instanceof SiyoThrow raised) {
            Object payload = raised.getPayload();
            // Raising a Java exception raises the exception, not a value that
            // happens to be one, so it is described the way a Java failure is.
            if (!(payload instanceof Throwable nested)) return payload;
            cause = nested;
        }
        String message = cause.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return cause.getClass().getSimpleName();
    }

    /**
     * The length of any value {@code len} accepts: the characters of a string,
     * or the elements of an array, map or set.
     *
     * <p>Both backends call this, so they cannot disagree about what a set's
     * length is — {@code len} used to know only strings and arrays and failed
     * at run time on the other two.
     *
     * @param value The value to measure.
     * @return The number of characters or elements.
     */
    public static int lengthOf(Object value) {
        if (value == null) return 0;
        if (value instanceof String text) return text.length();
        if (value instanceof SiyoMap map) return map.size();
        if (value instanceof SiyoSet set) return set.size();
        if (value instanceof java.util.Collection<?> collection) return collection.size();
        if (value instanceof java.util.Map<?, ?> map) return map.size();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        throw new SiyoThrow("len is not defined for " + value.getClass().getSimpleName());
    }

    /**
     * The name of the struct a value is, or null when it is not a struct.
     *
     * <p>A call through an interface dispatches on this, so it has to answer
     * for both a struct built by the interpreter and one built by compiled
     * code.
     *
     * @param value The receiver.
     * @return The struct's declared name, or null.
     */
    public static String structNameOf(Object value) {
        if (value instanceof SiyoObject object) return object.getTypeName();
        if (value instanceof SiyoStruct struct) {
            return struct.getStructType() != null ? struct.getStructType().getName() : null;
        }
        if (value instanceof SiyoActor actor) return actor.getActorTypeName();
        return null;
    }

    /**
     * The error raised when a value reaches an interface call without being
     * one of the structs that implement it.
     *
     * @param structName    The struct the value turned out to be, or null.
     * @param interfaceName The interface it was reached through.
     * @return The error to raise.
     */
    public static SiyoThrow unimplementedInterface(String structName, String interfaceName) {
        return new SiyoThrow(String.format("%s does not implement %s",
                structName == null ? "value" : structName, interfaceName));
    }

    /**
     * The fields of a struct, in declaration order.
     *
     * @param value The struct.
     * @return Its field names, or an empty array when the value is not a struct.
     */
    public static SiyoArray structFields(Object value) {
        java.util.List<Object> names = new java.util.ArrayList<>(fieldMapOf(value).keySet());
        return new SiyoArray(names, String.class);
    }

    /**
     * Reads one field of a struct by name.
     *
     * @param value The struct.
     * @param name  The field name.
     * @return The field's value, or null when there is no such field.
     */
    public static Object structField(Object value, String name) {
        return fieldMapOf(value).get(name);
    }

    /**
     * Writes one field of a struct by name.
     *
     * @param value      The struct.
     * @param name       The field name.
     * @param fieldValue The value to store.
     */
    public static void setStructField(Object value, String name, Object fieldValue) {
        if (value instanceof SiyoStruct struct) {
            struct.setField(name, fieldValue);
            return;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> fields = (java.util.Map<String, Object>) map;
            fields.put(name, fieldValue);
        }
    }

    /**
     * A struct's fields as a map, so it can be serialised without naming each
     * field by hand.
     *
     * @param value The struct.
     * @return Its fields, keyed by name.
     */
    public static SiyoMap structToMap(Object value) {
        SiyoMap result = new SiyoMap();
        for (var entry : fieldMapOf(value).entrySet()) {
            result.set(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * The name of the struct a value is, for a program to read.
     *
     * @param value The value.
     * @return The struct's name, or an empty string when it is not a struct.
     */
    public static String typeNameOf(Object value) {
        String name = structNameOf(value);
        return name == null ? "" : name;
    }

    /** The field map behind a struct, whichever backend built it. */
    private static java.util.Map<String, Object> fieldMapOf(Object value) {
        if (value instanceof SiyoStruct struct) return struct.getFieldsMap();
        if (value instanceof SiyoActor actor) return fieldMapOf(actor.getState());
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> fields = new java.util.LinkedHashMap<>();
            for (var entry : map.entrySet()) fields.put(String.valueOf(entry.getKey()), entry.getValue());
            return fields;
        }
        return java.util.Map.of();
    }

    /** Raise a Siyo error carrying an arbitrary payload. */
    public static SiyoThrow raise(Object payload) {
        return new SiyoThrow(payload);
    }

    /** parseInt that returns 0 on invalid input (matches interpreter behavior). */
    public static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** parseDouble that returns 0.0 on invalid input (matches interpreter behavior). */
    public static double safeParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Read a file as binary. Returns SiyoArray of int values (0–255). */
    public static SiyoArray readBytes(String path) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
        return SiyoArray.fromJavaArray(bytes);
    }

    /** Write binary data to a file. Accepts SiyoArray of int values (0–255). */
    public static void writeBytes(String path, Object arr) throws Exception {
        SiyoArray siyoArr = (SiyoArray) arr;
        byte[] bytes = new byte[siyoArr.size()];
        for (int i = 0; i < siyoArr.size(); i++) {
            Object elem = siyoArr.get(i);
            if (elem instanceof Integer n) bytes[i] = n.byteValue();
        }
        java.nio.file.Files.write(java.nio.file.Paths.get(path), bytes);
    }

    /** Copy a file preserving binary content. Overwrites destination if it exists. */
    public static void copyFile(String src, String dst) throws Exception {
        java.nio.file.Files.copy(
            java.nio.file.Paths.get(src),
            java.nio.file.Paths.get(dst),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Parses a JSON object, reporting malformed input.
     *
     * <p>The lenient {@link #jsonParse(String)} cannot distinguish a document
     * it failed to read from the valid document {@code &#123;&#125;}, which is
     * what {@code std/json} used to expose. This one validates and throws with
     * the position of the first thing it could not read.</p>
     *
     * @param s The JSON text.
     * @return The parsed object.
     */
    public static SiyoMap jsonParseStrict(String s) {
        if (s == null) throw new RuntimeException("json: no input");
        int[] pos = {0};
        skipWs(s, pos);
        if (pos[0] >= s.length()) throw jsonError("json: empty input", s, pos[0]);
        if (s.charAt(pos[0]) != '{') {
            throw jsonError("json: expected an object", s, pos[0]);
        }
        SiyoMap parsed = parseJsonObject(s, pos);
        skipWs(s, pos);
        if (pos[0] < s.length()) {
            throw jsonError("json: unexpected trailing input", s, pos[0]);
        }
        return parsed;
    }

    /**
     * Parses a JSON string into a SiyoMap, returning an empty map for input it
     * cannot read.
     *
     * @param s The JSON text.
     * @return The parsed object, or an empty map.
     */
    public static SiyoMap jsonParse(String s) {
        try {
            return jsonParseStrict(s);
        } catch (RuntimeException e) {
            return new SiyoMap();
        }
    }

    /**
     * Builds a parse error naming the position it was found at.
     *
     * @param message The message.
     * @param s       The input being parsed.
     * @param pos     The position of the offending character.
     * @return The exception to throw.
     */
    private static RuntimeException jsonError(String message, String s, int pos) {
        if (pos < s.length()) {
            return new RuntimeException(message + " at position " + pos + " ('" + s.charAt(pos) + "')");
        }
        return new RuntimeException(message + " at position " + pos + " (end of input)");
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r') pos[0]++; else break;
        }
    }

    private static String parseJsonString(String s, int[] pos) {
        if (pos[0] >= s.length() || s.charAt(pos[0]) != '"') {
            throw jsonError("json: expected a string", s, pos[0]);
        }
        pos[0]++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '"') { pos[0]++; return sb.toString(); }
            if (c == '\\') {
                pos[0]++;
                if (pos[0] >= s.length()) break;
                char esc = s.charAt(pos[0]);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (pos[0] + 4 >= s.length()) {
                            throw jsonError("json: truncated unicode escape", s, pos[0]);
                        }
                        String hex = s.substring(pos[0] + 1, pos[0] + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw jsonError("json: bad unicode escape", s, pos[0]);
                        }
                        pos[0] += 4;
                    }
                    default -> throw jsonError("json: unknown escape '\\" + esc + "'", s, pos[0]);
                }
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        throw jsonError("json: unterminated string", s, pos[0]);
    }

    private static Object parseJsonValue(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) throw jsonError("json: expected a value", s, pos[0]);
        char c = s.charAt(pos[0]);
        if (c == '"') return parseJsonString(s, pos);
        if (c == '{') return parseJsonObject(s, pos);
        if (c == '[') return parseJsonArray(s, pos);
        if (s.startsWith("true", pos[0])) { pos[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", pos[0])) { pos[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", pos[0])) { pos[0] += 4; return null; }
        return parseJsonNumber(s, pos);
    }

    /**
     * Parses a number, including the exponent form the old parser dropped.
     *
     * @param s   The input.
     * @param pos The cursor.
     * @return An Integer, Long or Double.
     */
    private static Object parseJsonNumber(String s, int[] pos) {
        int start = pos[0];
        if (pos[0] < s.length() && s.charAt(pos[0]) == '-') pos[0]++;
        int digitsStart = pos[0];
        while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        if (pos[0] == digitsStart) throw jsonError("json: expected a value", s, start);

        boolean isDecimal = false;
        if (pos[0] < s.length() && s.charAt(pos[0]) == '.') {
            isDecimal = true;
            pos[0]++;
            int fracStart = pos[0];
            while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
            if (pos[0] == fracStart) throw jsonError("json: expected a digit", s, pos[0]);
        }
        if (pos[0] < s.length() && (s.charAt(pos[0]) == 'e' || s.charAt(pos[0]) == 'E')) {
            isDecimal = true;
            pos[0]++;
            if (pos[0] < s.length() && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) pos[0]++;
            int expStart = pos[0];
            while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
            if (pos[0] == expStart) throw jsonError("json: expected an exponent", s, pos[0]);
        }

        String text = s.substring(start, pos[0]);
        if (isDecimal) return Double.parseDouble(text);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return Long.parseLong(text);
        }
    }

    private static SiyoMap parseJsonObject(String s, int[] pos) {
        pos[0]++; // skip '{'
        SiyoMap m = new SiyoMap();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') { pos[0]++; return m; }
        while (true) {
            skipWs(s, pos);
            String key = parseJsonString(s, pos);
            skipWs(s, pos);
            if (pos[0] >= s.length() || s.charAt(pos[0]) != ':') {
                throw jsonError("json: expected ':' after a key", s, pos[0]);
            }
            pos[0]++; // skip ':'
            m.set(key, parseJsonValue(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) throw jsonError("json: unterminated object", s, pos[0]);
            char c = s.charAt(pos[0]);
            if (c == ',') { pos[0]++; continue; }
            if (c == '}') { pos[0]++; return m; }
            throw jsonError("json: expected ',' or '}'", s, pos[0]);
        }
    }

    private static SiyoArray parseJsonArray(String s, int[] pos) {
        pos[0]++; // skip '['
        java.util.List<Object> elements = new java.util.ArrayList<>();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') {
            pos[0]++;
            return new SiyoArray(elements, Object.class);
        }
        while (true) {
            elements.add(parseJsonValue(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) throw jsonError("json: unterminated array", s, pos[0]);
            char c = s.charAt(pos[0]);
            if (c == ',') { pos[0]++; continue; }
            if (c == ']') {
                pos[0]++;
                return new SiyoArray(elements, Object.class);
            }
            throw jsonError("json: expected ',' or ']'", s, pos[0]);
        }
    }

    /** Stringify any Siyo value to JSON. Handles nested SiyoMap and SiyoArray. */
    public static String jsonStringify(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Boolean b) return b.toString();
        if (obj instanceof Integer || obj instanceof Long || obj instanceof Double) return obj.toString();
        if (obj instanceof SiyoMap m) {
            StringBuilder sb = new StringBuilder("{");
            SiyoArray keys = m.keys();
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                Object k = keys.get(i);
                sb.append('"').append(jsonEscapeStr(k.toString())).append('"');
                sb.append(':');
                sb.append(jsonStringify(m.get(k)));
            }
            sb.append('}');
            return sb.toString();
        }
        if (obj instanceof SiyoArray arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(jsonStringify(arr.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        // String or other
        String s = obj.toString();
        // Try to detect numbers to avoid quoting them
        try { Integer.parseInt(s); return s; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(s); return s; } catch (NumberFormatException ignored) {}
        return '"' + jsonEscapeStr(s) + '"';
    }

    private static String jsonEscapeStr(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Recursively collect all regular files under dir.
     * Returns SiyoArray of paths relative to dir, sorted, symlinks skipped.
     */
    public static SiyoArray walkFiles(String dir) throws Exception {
        java.nio.file.Path base = java.nio.file.Paths.get(dir);
        java.util.List<Object> files = new java.util.ArrayList<>();
        try (var stream = java.nio.file.Files.walk(base)) {
            stream.filter(p -> {
                try {
                    return !java.nio.file.Files.isSymbolicLink(p)
                        && java.nio.file.Files.isRegularFile(p);
                } catch (Exception e) { return false; }
            })
            .sorted()
            .forEach(p -> files.add(base.relativize(p).toString().replace('\\', '/')));
        }
        return new SiyoArray(files, String.class);
    }
}
