package codeanalysis;

import java.util.Arrays;

/**
 * A value of a sum type: one named variant of a declared {@code type}, carrying
 * the payload that variant was declared with.
 *
 * <p>A sum type is declared as a set of alternatives —
 * {@code type Result = Ok(int) | Err(string)} — and a value of it is always
 * exactly one of them. The variant name is what a {@code match} arm selects on,
 * and the payload is what the arm destructures.</p>
 *
 * <p>Instances are immutable. Both backends build them through
 * {@link #of(String, String, Object...)}: the interpreter calls it directly and
 * the emitter compiles a call to it, so a union value has one representation
 * whichever path produced it.</p>
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class SiyoUnion {
    private final String _typeName;
    private final String _variantName;
    private final Object[] _values;

    private SiyoUnion(String typeName, String variantName, Object[] values) {
        _typeName = typeName;
        _variantName = variantName;
        _values = values == null ? new Object[0] : values;
    }

    /**
     * Builds a union value.
     *
     * @param typeName    The name of the declared sum type.
     * @param variantName The name of the variant this value is.
     * @param values      The payload, in declaration order.
     * @return The union value.
     */
    public static SiyoUnion of(String typeName, String variantName, Object... values) {
        return new SiyoUnion(typeName, variantName, values);
    }

    /**
     * Gets the name of the sum type this value belongs to.
     *
     * @return The type name.
     */
    public String getTypeName() {
        return _typeName;
    }

    /**
     * Gets the name of the variant this value is.
     *
     * @return The variant name.
     */
    public String getVariantName() {
        return _variantName;
    }

    /**
     * Gets the number of payload slots this value carries.
     *
     * @return The payload size.
     */
    public int size() {
        return _values.length;
    }

    /**
     * Gets one payload slot.
     *
     * @param index The zero-based position of the slot.
     * @return The value in that slot.
     */
    public Object get(int index) {
        return _values[index];
    }

    /**
     * Reports whether this value is the named variant.
     *
     * @param variantName The variant name to test.
     * @return True when this value is that variant.
     */
    public boolean is(String variantName) {
        return _variantName.equals(variantName);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SiyoUnion o)) return false;
        return _typeName.equals(o._typeName)
                && _variantName.equals(o._variantName)
                && Arrays.equals(_values, o._values);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * _typeName.hashCode() + _variantName.hashCode()) + Arrays.hashCode(_values);
    }

    /**
     * Renders the value the way it is written in source: {@code Ok(5)} for a
     * variant with a payload, {@code None} for one without.
     *
     * @return The rendered value.
     */
    @Override
    public String toString() {
        if (_values.length == 0) return _variantName;
        StringBuilder text = new StringBuilder(_variantName).append('(');
        for (int i = 0; i < _values.length; i++) {
            if (i > 0) text.append(", ");
            text.append(_values[i]);
        }
        return text.append(')').toString();
    }
}
