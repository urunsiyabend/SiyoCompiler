package codeanalysis;

import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

/**
 * Parses JVM generic signatures (from .class Signature attribute) and resolves type variables.
 */
public class JavaGenericSignature {

    /**
     * Parse a class-level generic signature to extract type parameter names.
     * e.g., "<E:Ljava/lang/Object;>..." → ["E"]
     */
    public static List<String> parseClassTypeParams(String classSignature) {
        if (classSignature == null) return List.of();
        List<String> params = new ArrayList<>();
        SignatureReader reader = new SignatureReader(classSignature);
        reader.accept(new SignatureVisitor(Opcodes.ASM9) {
            @Override
            public void visitFormalTypeParameter(String name) {
                params.add(name);
            }
        });
        return params;
    }

    /**
     * Parse a method's generic return type signature and resolve it against type bindings.
     * Returns the fully qualified class name of the resolved return type, or null.
     *
     * @param methodSignature the generic method signature, e.g., "()Ljava/util/Iterator<TE;>;"
     * @param typeBindings    mapping of type param names to resolved types, e.g., {E: SelectionKey}
     * @return resolved return type info, or null if cannot resolve
     */
    public static ResolvedReturnType resolveReturnType(String methodSignature, Map<String, JavaResolvedType> typeBindings) {
        if (methodSignature == null) return null;

        // Extract return type portion (after the closing ')')
        int closeIdx = methodSignature.lastIndexOf(')');
        if (closeIdx < 0) return null;
        String returnSig = methodSignature.substring(closeIdx + 1);

        return resolveTypeSignature(returnSig, typeBindings);
    }

    /**
     * Resolve a type signature string (e.g., "TE;", "Ljava/util/Set<Ljava/nio/channels/SelectionKey;>;")
     * against type bindings.
     */
    public static ResolvedReturnType resolveTypeSignature(String typeSig, Map<String, JavaResolvedType> typeBindings) {
        if (typeSig == null || typeSig.isEmpty()) return null;

        // Type variable: "TE;" → look up T in bindings
        if (typeSig.startsWith("T") && typeSig.endsWith(";")) {
            String typeVarName = typeSig.substring(1, typeSig.length() - 1);
            if (typeBindings != null) {
                JavaResolvedType bound = typeBindings.get(typeVarName);
                if (bound != null) {
                    return new ResolvedReturnType(bound.getClassInfo().getFullName(), bound.getTypeArgs());
                }
            }
            return null; // unresolved type variable
        }

        // Parameterized type: "Ljava/util/Set<Ljava/nio/channels/SelectionKey;>;"
        if (typeSig.startsWith("L") && typeSig.contains("<")) {
            return parseParameterizedType(typeSig, typeBindings);
        }

        // Simple class reference: "Ljava/nio/channels/SelectionKey;"
        if (typeSig.startsWith("L") && typeSig.endsWith(";")) {
            String className = typeSig.substring(1, typeSig.length() - 1).replace('/', '.');
            return new ResolvedReturnType(className, Map.of());
        }

        // Array: "[..."
        if (typeSig.startsWith("[")) {
            return null; // arrays handled separately
        }

        // Primitive
        return null;
    }

    private static ResolvedReturnType parseParameterizedType(String sig, Map<String, JavaResolvedType> typeBindings) {
        // "Ljava/util/Set<Ljava/nio/channels/SelectionKey;>;"
        int angleIdx = sig.indexOf('<');
        String rawClass = sig.substring(1, angleIdx).replace('/', '.');

        // Parse type arguments
        String argsSection = sig.substring(angleIdx + 1);
        List<ResolvedReturnType> typeArgs = new ArrayList<>();
        parseTypeArgs(argsSection, typeBindings, typeArgs);

        // Load the class to get its type parameter names
        JavaClassMetadata meta = JavaClassMetadata.load(rawClass);
        Map<String, JavaResolvedType> newBindings = new HashMap<>();
        if (meta != null) {
            List<String> typeParams = meta.getTypeParams();
            for (int i = 0; i < Math.min(typeParams.size(), typeArgs.size()); i++) {
                ResolvedReturnType argType = typeArgs.get(i);
                if (argType != null) {
                    JavaClassMetadata argMeta = JavaClassMetadata.load(argType.className);
                    if (argMeta != null) {
                        String simpleName = argType.className.contains(".")
                                ? argType.className.substring(argType.className.lastIndexOf('.') + 1)
                                : argType.className;
                        JavaClassInfo argInfo = new JavaClassInfo(simpleName, argType.className, argMeta);
                        newBindings.put(typeParams.get(i), new JavaResolvedType(argInfo, convertBindings(argType.typeArgBindings)));
                    }
                }
            }
        }

        return new ResolvedReturnType(rawClass, newBindings);
    }

    private static void parseTypeArgs(String argsSection, Map<String, JavaResolvedType> typeBindings, List<ResolvedReturnType> result) {
        int i = 0;
        while (i < argsSection.length()) {
            char c = argsSection.charAt(i);
            if (c == '>') break;
            if (c == '+' || c == '-') { i++; continue; } // wildcard bounds, skip
            if (c == '*') { result.add(null); i++; continue; } // unbounded wildcard

            if (c == 'T') {
                // Type variable: "TE;"
                int semi = argsSection.indexOf(';', i);
                String typeVar = argsSection.substring(i, semi + 1);
                result.add(resolveTypeSignature(typeVar, typeBindings));
                i = semi + 1;
            } else if (c == 'L') {
                // Class type, possibly parameterized
                int depth = 0;
                int start = i;
                while (i < argsSection.length()) {
                    char ch = argsSection.charAt(i);
                    if (ch == '<') depth++;
                    if (ch == '>') depth--;
                    if (ch == ';' && depth == 0) { i++; break; }
                    i++;
                }
                String typeSig = argsSection.substring(start, i);
                result.add(resolveTypeSignature(typeSig, typeBindings));
            } else {
                i++; // skip unknown
            }
        }
    }

    private static Map<String, JavaResolvedType> convertBindings(Map<String, JavaResolvedType> bindings) {
        return bindings != null ? new HashMap<>(bindings) : new HashMap<>();
    }

    /**
     * Result of resolving a generic return type.
     */
    public static class ResolvedReturnType {
        public final String className;
        public final Map<String, JavaResolvedType> typeArgBindings;

        public ResolvedReturnType(String className, Map<String, JavaResolvedType> typeArgBindings) {
            this.className = className;
            this.typeArgBindings = typeArgBindings != null ? typeArgBindings : Map.of();
        }
    }
}
