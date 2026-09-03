package codeanalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * A declared function type: the parameter types and return type written in
 * {@code fn(int, string) -> bool}.
 *
 * <p>A function type reaches the binder as a type name, the way an array type
 * does — {@code int[]} for an array, {@code fn(int)->bool} for a function — so
 * a signature travels with a parameter, a local, a struct field or a return
 * clause without every one of them needing its own syntax node. This class is
 * the parser for that name.</p>
 *
 * <p>A bare {@code fn} carries no signature: it accepts any closure, which is
 * what the language did for every function type before they were checked.</p>
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class FunctionTypeSignature {
    private final List<String> _parameterTypeNames;
    private final String _returnTypeName;

    private FunctionTypeSignature(List<String> parameterTypeNames, String returnTypeName) {
        _parameterTypeNames = parameterTypeNames;
        _returnTypeName = returnTypeName;
    }

    /**
     * Builds the type name that carries a signature, which is what the parser
     * writes and {@link #parse(String)} reads back.
     *
     * @param parameterTypeNames The parameter type names, in order.
     * @param returnTypeName     The return type name, or null when none was written.
     * @return The encoded type name.
     */
    public static String encode(List<String> parameterTypeNames, String returnTypeName) {
        StringBuilder name = new StringBuilder("fn(");
        for (int i = 0; i < parameterTypeNames.size(); i++) {
            if (i > 0) name.append(',');
            name.append(parameterTypeNames.get(i));
        }
        name.append(')');
        if (returnTypeName != null) name.append("->").append(returnTypeName);
        return name.toString();
    }

    /**
     * Reports whether a type name carries a function signature.
     *
     * <p>An array of functions is written {@code fn()[]} and a function
     * returning an array {@code fn()->int[]}, so the suffix after the
     * parameter list decides which of the two a name is.</p>
     *
     * @param typeName The type name.
     * @return True for {@code fn(...)}, false for a bare {@code fn}, for an
     *         array of functions, and for every other type.
     */
    public static boolean isSignature(String typeName) {
        if (typeName == null || !typeName.startsWith("fn(")) return false;
        int close = matchingParenEnd(typeName);
        if (close < 0) return false;
        String rest = typeName.substring(close + 1).trim();
        return rest.isEmpty() || rest.startsWith("->");
    }

    /**
     * Reports whether a type name is an array of functions: {@code fn()[]}.
     *
     * @param typeName The type name.
     * @return True when it is.
     */
    public static boolean isFunctionArray(String typeName) {
        if (typeName == null || !typeName.startsWith("fn(")) return false;
        int close = matchingParenEnd(typeName);
        if (close < 0) return false;
        return typeName.substring(close + 1).trim().equals("[]");
    }

    /**
     * Finds the parenthesis that closes a function type's parameter list.
     *
     * @param typeName The type name.
     * @return The index of the closing parenthesis, or -1 when unbalanced.
     */
    private static int matchingParenEnd(String typeName) {
        int depth = 0;
        for (int i = "fn".length(); i < typeName.length(); i++) {
            char c = typeName.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Reads a signature out of a type name.
     *
     * @param typeName The type name, as produced by {@link #encode}.
     * @return The signature, or null when the name carries none.
     */
    public static FunctionTypeSignature parse(String typeName) {
        if (!isSignature(typeName)) return null;

        int close = matchingParenEnd(typeName);
        if (close < 0) return null;

        // The split tracks nesting, so a parameter that is itself a function
        // type — fn(fn(int)->int)->bool — splits at the right places.
        int depth;
        List<String> parameters = new ArrayList<>();
        String inside = typeName.substring("fn(".length(), close).trim();
        if (!inside.isEmpty()) {
            int start = 0;
            depth = 0;
            for (int i = 0; i <= inside.length(); i++) {
                char c = i < inside.length() ? inside.charAt(i) : ',';
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 0) {
                    parameters.add(inside.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }

        String returnTypeName = null;
        String rest = typeName.substring(close + 1).trim();
        if (rest.startsWith("->")) {
            String declared = rest.substring(2).trim();
            if (!declared.isEmpty()) returnTypeName = declared;
        }
        return new FunctionTypeSignature(parameters, returnTypeName);
    }

    /**
     * Gets the declared parameter type names, in order.
     *
     * @return The parameter type names.
     */
    public List<String> getParameterTypeNames() {
        return _parameterTypeNames;
    }

    /**
     * Gets the declared return type name.
     *
     * @return The return type name, or null when none was written.
     */
    public String getReturnTypeName() {
        return _returnTypeName;
    }

    /**
     * Gets the number of parameters the signature declares.
     *
     * @return The parameter count.
     */
    public int getParameterCount() {
        return _parameterTypeNames.size();
    }

    /**
     * Renders the signature the way it is written in source.
     *
     * @return The rendered signature.
     */
    @Override
    public String toString() {
        String rendered = "fn(" + String.join(", ", _parameterTypeNames) + ")";
        return _returnTypeName == null ? rendered : rendered + " -> " + _returnTypeName;
    }
}
