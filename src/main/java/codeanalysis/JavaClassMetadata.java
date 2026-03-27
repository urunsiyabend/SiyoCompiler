package codeanalysis;

import org.objectweb.asm.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile-time Java class metadata loaded via ASM ClassReader.
 * No reflection - reads .class files directly from classpath.
 */
public class JavaClassMetadata {
    private final String _simpleName;
    private final String _fullName;
    private final String _internalName;
    private final boolean _isInterface;
    private final String _superClassName;
    private final List<JavaMethodSignature> _methods;
    private final List<JavaMethodSignature> _constructors;
    private final java.util.Map<String, String> _staticFields; // name → descriptor

    private JavaClassMetadata(String simpleName, String fullName, String internalName,
                               boolean isInterface, String superClassName,
                               List<JavaMethodSignature> methods, List<JavaMethodSignature> constructors,
                               java.util.Map<String, String> staticFields) {
        _simpleName = simpleName;
        _fullName = fullName;
        _internalName = internalName;
        _isInterface = isInterface;
        _superClassName = superClassName;
        _methods = methods;
        _constructors = constructors;
        _staticFields = staticFields;
    }

    /**
     * Load class metadata from classpath using ASM ClassReader.
     */
    public static JavaClassMetadata load(String fullClassName) {
        String internalName = fullClassName.replace('.', '/');
        String resourcePath = internalName + ".class";

        // Search order: system classloader → context classloader → external JARs
        InputStream is = ClassLoader.getSystemResourceAsStream(resourcePath);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        }
        if (is == null) {
            // Search external JARs registered via -cp flag
            is = JavaClasspath.findClass(resourcePath);
        }
        if (is == null) return null;

        try {
            JavaClassMetadata result = parseClassFile(is, fullClassName, internalName);
            is.close();
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    private static JavaClassMetadata parseClassFile(InputStream is, String fullClassName, String internalName) throws IOException {
        ClassReader reader = new ClassReader(is);
        String simpleName = fullClassName.contains(".")
                ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1)
                : fullClassName;

        boolean[] isInterface = {false};
        String[] superClass = {null};
        List<JavaMethodSignature> methods = new ArrayList<>();
        List<JavaMethodSignature> constructors = new ArrayList<>();
        java.util.Map<String, String> staticFields = new java.util.HashMap<>();

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                isInterface[0] = (access & Opcodes.ACC_INTERFACE) != 0;
                if (superName != null && !superName.equals("java/lang/Object")) {
                    superClass[0] = superName.replace('/', '.');
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_STATIC) != 0) {
                    staticFields.put(name, descriptor);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_PUBLIC) == 0) return null;

                String[] paramDescs = JavaTypeMapper.parseParamDescriptors(descriptor);
                String returnDesc = JavaTypeMapper.parseReturnDescriptor(descriptor);
                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                boolean isCtor = name.equals("<init>");

                JavaMethodSignature sig = new JavaMethodSignature(
                        isCtor ? "new" : name, descriptor, returnDesc, paramDescs,
                        internalName, isStatic, isCtor, isInterface[0]);

                if (isCtor) constructors.add(sig);
                else methods.add(sig);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new JavaClassMetadata(simpleName, fullClassName, internalName, isInterface[0], superClass[0], methods, constructors, staticFields);
    }

    public String getSimpleName() { return _simpleName; }
    public String getFullName() { return _fullName; }
    public String getInternalName() { return _internalName; }
    public boolean isInterface() { return _isInterface; }

    public JavaMethodSignature resolveMethod(String name, int argCount) {
        return resolveMethod(name, argCount, null);
    }

    /**
     * Resolve method with optional argument type matching for overload resolution.
     */
    public JavaMethodSignature resolveMethod(String name, int argCount, Class<?>[] argTypes) {
        JavaMethodSignature result = findMethodInClass(name, argCount, argTypes);
        if (result != null) return result;

        // Search superclass hierarchy
        if (_superClassName != null) {
            JavaClassMetadata superMeta = load(_superClassName);
            if (superMeta != null) {
                return superMeta.resolveMethod(name, argCount, argTypes);
            }
        }
        return null;
    }

    private JavaMethodSignature findMethodInClass(String name, int argCount, Class<?>[] argTypes) {
        JavaMethodSignature fallback = null;
        for (JavaMethodSignature sig : _methods) {
            if (!sig.getName().equals(name) || sig.getParamCount() != argCount) continue;

            if (argTypes == null) {
                // No type info - return first match (backward compat)
                return sig;
            }

            // Type-aware matching
            boolean match = true;
            String[] paramDescs = sig.getParamDescriptors();
            for (int i = 0; i < argCount; i++) {
                if (!isTypeCompatible(argTypes[i], paramDescs[i])) {
                    match = false;
                    break;
                }
            }
            if (match) return sig;
            if (argTypes == null && fallback == null) fallback = sig;
        }
        return fallback; // only used when no type info provided
    }

    private boolean isTypeCompatible(Class<?> siyoType, String jvmDesc) {
        if (siyoType == null) return true;
        if (siyoType == Integer.class) return jvmDesc.equals("I") || jvmDesc.equals("J");
        if (siyoType == Boolean.class) return jvmDesc.equals("Z");
        if (siyoType == Double.class) return jvmDesc.equals("D") || jvmDesc.equals("F");
        if (siyoType == String.class) return jvmDesc.equals("Ljava/lang/String;") || jvmDesc.startsWith("Ljava/lang/CharSequence;");
        if (siyoType == Object.class) {
            // Object is compatible with reference types only, NOT primitives
            return jvmDesc.startsWith("L") || jvmDesc.startsWith("[");
        }
        return jvmDesc.startsWith("L") || jvmDesc.startsWith("["); // reference types
    }

    public JavaMethodSignature resolveConstructor(int argCount) {
        for (JavaMethodSignature sig : _constructors) {
            if (sig.getParamCount() == argCount) {
                return sig;
            }
        }
        return null;
    }

    public List<JavaMethodSignature> getMethods() { return _methods; }
    public List<JavaMethodSignature> getConstructors() { return _constructors; }

    /** Returns field descriptor or null. */
    public String getStaticFieldDescriptor(String name) {
        return _staticFields.get(name);
    }
}
