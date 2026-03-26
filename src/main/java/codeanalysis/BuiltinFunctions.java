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

    public static final FunctionSymbol PRINT = new FunctionSymbol(
            "print",
            List.of(new ParameterSymbol("value", Object.class)),
            null // void
    );

    public static final FunctionSymbol PRINTLN = new FunctionSymbol(
            "println",
            List.of(new ParameterSymbol("value", Object.class)),
            null // void
    );

    public static final FunctionSymbol RANGE = new FunctionSymbol(
            "range",
            List.of(new ParameterSymbol("start", Integer.class), new ParameterSymbol("end", Integer.class)),
            SiyoArray.class
    );

    public static final FunctionSymbol PUSH = new FunctionSymbol(
            "push",
            List.of(new ParameterSymbol("arr", SiyoArray.class), new ParameterSymbol("value", Object.class)),
            null
    );

    public static final FunctionSymbol SUBSTRING = new FunctionSymbol(
            "substring",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("start", Integer.class), new ParameterSymbol("end", Integer.class)),
            String.class
    );

    public static final FunctionSymbol CONTAINS = new FunctionSymbol(
            "contains",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("sub", String.class)),
            Boolean.class
    );

    public static final FunctionSymbol INPUT = new FunctionSymbol(
            "input",
            List.of(new ParameterSymbol("prompt", String.class)),
            String.class
    );

    public static List<FunctionSymbol> getAll() {
        return List.of(LEN, TO_STRING, PARSE_INT, PARSE_FLOAT, TO_INT, TO_FLOAT, PRINT, PRINTLN, RANGE, PUSH, SUBSTRING, CONTAINS, INPUT);
    }

    public static boolean isBuiltin(FunctionSymbol function) {
        return function == LEN || function == TO_STRING || function == PARSE_INT
                || function == PARSE_FLOAT || function == TO_INT || function == TO_FLOAT
                || function == PRINT || function == PRINTLN || function == RANGE
                || function == PUSH || function == SUBSTRING || function == CONTAINS
                || function == RANGE || function == INPUT;
    }
}
