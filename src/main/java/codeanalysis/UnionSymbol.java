package codeanalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a sum type declaration: {@code type Result = Ok(int) | Err(string)}.
 *
 * <p>A sum type is a closed set of named variants, each carrying a payload of
 * declared types. The set is closed, which is what lets a {@code match} over it
 * be checked for exhaustiveness, and what separates a sum type from a struct
 * with flags.</p>
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class UnionSymbol {
    private final String _name;
    private final LinkedHashMap<String, Variant> _variants;

    public UnionSymbol(String name, LinkedHashMap<String, Variant> variants) {
        _name = name;
        _variants = variants;
    }

    /**
     * Gets the name of the sum type.
     *
     * @return The type name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Gets the variants, in declaration order.
     *
     * @return The variants keyed by name.
     */
    public Map<String, Variant> getVariants() {
        return _variants;
    }

    /**
     * Gets one variant by name.
     *
     * @param name The variant name.
     * @return The variant, or null when the type has no such variant.
     */
    public Variant getVariant(String name) {
        return _variants.get(name);
    }

    /**
     * Reports whether the type declares the named variant.
     *
     * @param name The variant name.
     * @return True when it does.
     */
    public boolean hasVariant(String name) {
        return _variants.containsKey(name);
    }

    /**
     * Gets the variant names, in declaration order.
     *
     * @return The variant names.
     */
    public List<String> getVariantNames() {
        return List.copyOf(_variants.keySet());
    }

    @Override
    public String toString() {
        return _name;
    }

    /**
     * One alternative of a sum type, together with the payload it carries.
     */
    public static final class Variant {
        private final String _name;
        private final List<Class<?>> _payloadTypes;
        private final List<String> _payloadTypeNames;

        public Variant(String name, List<Class<?>> payloadTypes, List<String> payloadTypeNames) {
            _name = name;
            _payloadTypes = payloadTypes;
            _payloadTypeNames = payloadTypeNames;
        }

        /**
         * Gets the variant name.
         *
         * @return The variant name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Gets the Java class backing each payload slot.
         *
         * @return The payload types, in declaration order.
         */
        public List<Class<?>> getPayloadTypes() {
            return _payloadTypes;
        }

        /**
         * Gets the Siyo type name written for each payload slot, which is what
         * keeps a struct or a nested sum type in a payload identifiable.
         *
         * @return The payload type names, in declaration order.
         */
        public List<String> getPayloadTypeNames() {
            return _payloadTypeNames;
        }

        /**
         * Gets the number of payload slots.
         *
         * @return The payload size.
         */
        public int size() {
            return _payloadTypes.size();
        }

        @Override
        public String toString() {
            return _payloadTypes.isEmpty() ? _name : _name + "(" + String.join(", ", _payloadTypeNames) + ")";
        }
    }
}
