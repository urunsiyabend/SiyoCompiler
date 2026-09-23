package codeanalysis;

import java.util.LinkedHashMap;

/**
 * A struct value in compiled code, carrying the name of the struct it is.
 *
 * <p>Compiled structs are field maps, which is enough to read and write a field
 * whose owner is known at compile time. It is not enough to call a method on a
 * value whose concrete struct is only known at run time — which is exactly what
 * a value reached through an interface is. This is that same field map with the
 * struct's name attached, so a dynamic call has something to dispatch on.
 *
 * <p>It extends {@link LinkedHashMap} rather than wrapping one so that every
 * place already emitting {@code Map} operations against a struct keeps working
 * unchanged.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 */
public class SiyoObject extends LinkedHashMap<String, Object> {
    private final String _typeName;

    /**
     * Creates an empty struct value of the named struct type.
     *
     * @param typeName The struct's declared name.
     */
    public SiyoObject(String typeName) {
        _typeName = typeName;
    }

    /**
     * The name of the struct this value is.
     *
     * @return The struct name.
     */
    public String getTypeName() {
        return _typeName;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(_typeName == null ? "struct" : _typeName);
        text.append(" { ");
        boolean first = true;
        for (var entry : entrySet()) {
            if (!first) text.append(", ");
            text.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        text.append(" }");
        return text.toString();
    }
}
