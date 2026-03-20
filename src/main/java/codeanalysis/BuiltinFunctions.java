package codeanalysis;

import java.util.List;

/**
 * Defines the built-in functions available in the Siyo language.
 */
public class BuiltinFunctions {
    public static final FunctionSymbol LEN = new FunctionSymbol(
            "len",
            List.of(new ParameterSymbol("s", String.class)),
            Integer.class
    );

    public static final FunctionSymbol TO_STRING = new FunctionSymbol(
            "toString",
            List.of(new ParameterSymbol("value", Object.class)),
            String.class
    );

    public static final FunctionSymbol PARSE_INT = new FunctionSymbol(
            "parseInt",
            List.of(new ParameterSymbol("text", String.class)),
            Integer.class
    );

    /**
     * Returns all built-in functions.
     */
    public static List<FunctionSymbol> getAll() {
        return List.of(LEN, TO_STRING, PARSE_INT);
    }

    /**
     * Checks if the given function is a built-in function.
     */
    public static boolean isBuiltin(FunctionSymbol function) {
        return function == LEN || function == TO_STRING || function == PARSE_INT;
    }
}
