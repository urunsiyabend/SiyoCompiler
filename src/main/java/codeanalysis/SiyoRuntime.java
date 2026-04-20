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

    /** Parse a JSON string into a SiyoMap with full nested support. */
    public static SiyoMap jsonParse(String s) {
        int[] pos = {0};
        skipWs(s, pos);
        if (pos[0] >= s.length() || s.charAt(pos[0]) != '{') return new SiyoMap();
        return parseJsonObject(s, pos);
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r') pos[0]++; else break;
        }
    }

    private static String parseJsonString(String s, int[] pos) {
        pos[0]++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '"') { pos[0]++; return sb.toString(); }
            if (c == '\\') {
                pos[0]++;
                char esc = s.charAt(pos[0]);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '/' -> sb.append('/');
                    default -> { sb.append('\\'); sb.append(esc); }
                }
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        return sb.toString();
    }

    private static Object parseJsonValue(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) return null;
        char c = s.charAt(pos[0]);
        if (c == '"') return parseJsonString(s, pos);
        if (c == '{') return parseJsonObject(s, pos);
        if (c == '[') return parseJsonArray(s, pos);
        if (c == 't') { pos[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return null; }
        // Number
        int start = pos[0];
        if (c == '-') pos[0]++;
        while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) pos[0]++;
        String numStr = s.substring(start, pos[0]);
        if (numStr.contains(".")) return Double.parseDouble(numStr);
        try { return Integer.parseInt(numStr); } catch (NumberFormatException e) { return Long.parseLong(numStr); }
    }

    private static SiyoMap parseJsonObject(String s, int[] pos) {
        pos[0]++; // skip '{'
        SiyoMap m = new SiyoMap();
        boolean first = true;
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            if (s.charAt(pos[0]) == '}') { pos[0]++; break; }
            if (!first && s.charAt(pos[0]) == ',') pos[0]++;
            first = false;
            skipWs(s, pos);
            String key = parseJsonString(s, pos);
            skipWs(s, pos);
            pos[0]++; // skip ':'
            Object val = parseJsonValue(s, pos);
            m.set(key, val);
        }
        return m;
    }

    private static SiyoArray parseJsonArray(String s, int[] pos) {
        pos[0]++; // skip '['
        java.util.List<Object> elems = new java.util.ArrayList<>();
        boolean first = true;
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            if (s.charAt(pos[0]) == ']') { pos[0]++; break; }
            if (!first && s.charAt(pos[0]) == ',') pos[0]++;
            first = false;
            elems.add(parseJsonValue(s, pos));
        }
        return new SiyoArray(elems, Object.class);
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
