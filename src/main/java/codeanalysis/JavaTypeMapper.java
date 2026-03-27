package codeanalysis;

/**
 * Maps between JVM type descriptors and Siyo's Class<?> type system.
 */
public class JavaTypeMapper {

    /**
     * Convert JVM type descriptor to Siyo Class<?>.
     */
    public static Class<?> descriptorToSiyoType(String descriptor) {
        if (descriptor == null || descriptor.equals("V")) return null; // void
        return switch (descriptor) {
            case "I" -> Integer.class;
            case "Z" -> Boolean.class;
            case "D" -> Double.class;
            case "F" -> Double.class;  // float → double in Siyo
            case "J" -> Integer.class; // long → int in Siyo (lossy but practical)
            case "B", "S", "C" -> Integer.class; // byte/short/char → int
            case "Ljava/lang/String;" -> String.class;
            case "Ljava/lang/Integer;", "Ljava/lang/Long;", "Ljava/lang/Short;", "Ljava/lang/Byte;" -> Integer.class;
            case "Ljava/lang/Boolean;" -> Boolean.class;
            case "Ljava/lang/Double;", "Ljava/lang/Float;" -> Double.class;
            default -> Object.class; // all other Java types → Object in Siyo
        };
    }

    /**
     * Convert Siyo Class<?> to JVM type descriptor.
     */
    public static String siyoTypeToDescriptor(Class<?> type) {
        if (type == null) return "V";
        if (type == Integer.class) return "I";
        if (type == Boolean.class) return "Z";
        if (type == Double.class) return "D";
        if (type == String.class) return "Ljava/lang/String;";
        return "Ljava/lang/Object;";
    }

    /**
     * Parse parameter descriptors from a method descriptor string.
     * "(Ljava/lang/String;I)Z" → ["Ljava/lang/String;", "I"]
     */
    public static String[] parseParamDescriptors(String methodDescriptor) {
        java.util.List<String> params = new java.util.ArrayList<>();
        int i = 1; // skip '('
        while (i < methodDescriptor.length() && methodDescriptor.charAt(i) != ')') {
            int start = i;
            char c = methodDescriptor.charAt(i);
            if (c == 'L') {
                int end = methodDescriptor.indexOf(';', i);
                params.add(methodDescriptor.substring(start, end + 1));
                i = end + 1;
            } else if (c == '[') {
                i++;
                if (methodDescriptor.charAt(i) == 'L') {
                    int end = methodDescriptor.indexOf(';', i);
                    params.add(methodDescriptor.substring(start, end + 1));
                    i = end + 1;
                } else {
                    params.add(methodDescriptor.substring(start, i + 1));
                    i++;
                }
            } else {
                // primitive
                params.add(String.valueOf(c));
                i++;
            }
        }
        return params.toArray(new String[0]);
    }

    /**
     * Extract return type descriptor from method descriptor.
     * "(Ljava/lang/String;I)Z" → "Z"
     */
    public static String parseReturnDescriptor(String methodDescriptor) {
        int closeIdx = methodDescriptor.indexOf(')');
        return methodDescriptor.substring(closeIdx + 1);
    }
}
