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

    public static final FunctionSymbol PARSE_LONG = new FunctionSymbol(
            "parseLong",
            List.of(new ParameterSymbol("s", String.class)),
            Long.class
    );

    public static final FunctionSymbol TO_LONG = new FunctionSymbol(
            "toLong",
            List.of(new ParameterSymbol("value", Integer.class)),
            Long.class
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

    public static final FunctionSymbol ERROR = new FunctionSymbol(
            "error",
            List.of(new ParameterSymbol("message", String.class)),
            null
    );

    public static final FunctionSymbol CHR = new FunctionSymbol(
            "chr",
            List.of(new ParameterSymbol("code", Integer.class)),
            String.class
    );

    public static final FunctionSymbol ORD = new FunctionSymbol(
            "ord",
            List.of(new ParameterSymbol("ch", String.class)),
            Integer.class
    );

    public static final FunctionSymbol INDEX_OF = new FunctionSymbol(
            "indexOf",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("sub", String.class)),
            Integer.class
    );

    public static final FunctionSymbol STARTS_WITH = new FunctionSymbol(
            "startsWith",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("prefix", String.class)),
            Boolean.class
    );

    public static final FunctionSymbol ENDS_WITH = new FunctionSymbol(
            "endsWith",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("suffix", String.class)),
            Boolean.class
    );

    public static final FunctionSymbol REPLACE = new FunctionSymbol(
            "replace",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("old", String.class), new ParameterSymbol("new_", String.class)),
            String.class
    );

    public static final FunctionSymbol TRIM = new FunctionSymbol(
            "trim",
            List.of(new ParameterSymbol("s", String.class)),
            String.class
    );

    public static final FunctionSymbol TO_UPPER = new FunctionSymbol(
            "toUpper",
            List.of(new ParameterSymbol("s", String.class)),
            String.class
    );

    public static final FunctionSymbol TO_LOWER = new FunctionSymbol(
            "toLower",
            List.of(new ParameterSymbol("s", String.class)),
            String.class
    );

    // toInt(string) -> int — alias for parseInt
    public static final FunctionSymbol TO_INT_STR = new FunctionSymbol(
            "toInt",
            List.of(new ParameterSymbol("value", String.class)),
            Integer.class
    );

    // toDouble(int) -> double — alias for toFloat
    public static final FunctionSymbol TO_DOUBLE = new FunctionSymbol(
            "toDouble",
            List.of(new ParameterSymbol("value", Integer.class)),
            Double.class
    );

    public static final FunctionSymbol REMOVE_AT = new FunctionSymbol(
            "removeAt",
            List.of(new ParameterSymbol("arr", SiyoArray.class), new ParameterSymbol("index", Integer.class)),
            null
    );

    public static final FunctionSymbol POP = new FunctionSymbol(
            "pop",
            List.of(new ParameterSymbol("arr", SiyoArray.class)),
            Object.class
    );

    public static final FunctionSymbol NEW_MAP = new FunctionSymbol(
            "map",
            List.of(),
            SiyoMap.class
    );

    // Internal builtin: convert map to keys array (used by for-in desugaring)
    public static final FunctionSymbol MAP_KEYS = new FunctionSymbol(
            "$mapKeys",
            List.of(new ParameterSymbol("map", SiyoMap.class)),
            SiyoArray.class
    );

    public static final FunctionSymbol NEW_SET = new FunctionSymbol(
            "set",
            List.of(),
            SiyoSet.class
    );

    public static final FunctionSymbol CHANNEL = new FunctionSymbol(
            "channel",
            List.of(),
            SiyoChannel.class
    );

    public static final FunctionSymbol CHANNEL_BUFFERED = new FunctionSymbol(
            "channel",
            List.of(new ParameterSymbol("capacity", Integer.class)),
            SiyoChannel.class
    );

    public static final FunctionSymbol SORT = new FunctionSymbol(
            "sort",
            List.of(new ParameterSymbol("arr", SiyoArray.class), new ParameterSymbol("comparator", SiyoClosure.class)),
            null
    );

    public static final FunctionSymbol SPLIT = new FunctionSymbol(
            "split",
            List.of(new ParameterSymbol("s", String.class), new ParameterSymbol("delimiter", String.class)),
            SiyoArray.class
    );

    public static final FunctionSymbol RANDOM = new FunctionSymbol(
            "random",
            List.of(new ParameterSymbol("max", Integer.class)),
            Integer.class
    );

    public static final FunctionSymbol ACTOR_HANDLE = new FunctionSymbol(
            "actorHandle",
            List.of(new ParameterSymbol("self", SiyoStruct.class)),
            Object.class
    );

    public static final FunctionSymbol CAN_READ = new FunctionSymbol(
            "canRead",
            List.of(new ParameterSymbol("reader", Object.class)),
            Boolean.class
    );

    public static final FunctionSymbol HTTP_GET = new FunctionSymbol(
            "httpGet",
            List.of(new ParameterSymbol("url", String.class)),
            String.class
    );

    public static final FunctionSymbol HTTP_POST = new FunctionSymbol(
            "httpPost",
            List.of(new ParameterSymbol("url", String.class), new ParameterSymbol("body", String.class)),
            String.class
    );

    public static List<FunctionSymbol> getAll() {
        return List.of(LEN, TO_STRING, PARSE_INT, PARSE_LONG, PARSE_FLOAT, TO_INT, TO_INT_STR, TO_LONG, TO_FLOAT, TO_DOUBLE,
                PRINT, PRINTLN, RANGE, PUSH, REMOVE_AT, POP, NEW_MAP, NEW_SET, MAP_KEYS, SORT, CHANNEL, CHANNEL_BUFFERED, SUBSTRING, CONTAINS, INPUT, ERROR, RANDOM,
                CHR, ORD, INDEX_OF, STARTS_WITH, ENDS_WITH, REPLACE, TRIM, TO_UPPER, TO_LOWER, SPLIT, HTTP_GET, HTTP_POST, CAN_READ, ACTOR_HANDLE);
    }

    public static boolean isBuiltin(FunctionSymbol function) {
        for (FunctionSymbol builtin : getAll()) {
            if (builtin == function) return true; // reference equality, not name-based
        }
        return false;
    }
}
