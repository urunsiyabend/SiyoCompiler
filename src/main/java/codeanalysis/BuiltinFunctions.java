package codeanalysis;

import java.util.List;

/**
 * Defines the built-in functions available in the Siyo language.
 */
public class BuiltinFunctions {
    public static final FunctionSymbol LEN = new FunctionSymbol(
            "len",
            List.of(new ParameterSymbol("s", Object.class)),
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

    public static final FunctionSymbol PARSE_FLOAT = new FunctionSymbol(
            "parseFloat",
            List.of(new ParameterSymbol("text", String.class)),
            Double.class
    );

    public static final FunctionSymbol TO_INT = new FunctionSymbol(
            "toInt",
            List.of(new ParameterSymbol("value", Double.class)),
            Integer.class
    );

    public static final FunctionSymbol TO_FLOAT = new FunctionSymbol(
            "toFloat",
            List.of(new ParameterSymbol("value", Integer.class)),
            Double.class
    );

    public static List<FunctionSymbol> getAll() {
        return List.of(LEN, TO_STRING, PARSE_INT, PARSE_FLOAT, TO_INT, TO_FLOAT);
    }

    public static boolean isBuiltin(FunctionSymbol function) {
        return function == LEN || function == TO_STRING || function == PARSE_INT
                || function == PARSE_FLOAT || function == TO_INT || function == TO_FLOAT;
    }
}
