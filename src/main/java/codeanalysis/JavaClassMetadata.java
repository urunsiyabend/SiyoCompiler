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
    private final List<JavaMethodSignature> _methods;
    private final List<JavaMethodSignature> _constructors;

    private JavaClassMetadata(String simpleName, String fullName, String internalName,
                               boolean isInterface, List<JavaMethodSignature> methods,
                               List<JavaMethodSignature> constructors) {
        _simpleName = simpleName;
        _fullName = fullName;
        _internalName = internalName;
        _isInterface = isInterface;
        _methods = methods;
        _constructors = constructors;
    }

    /**
     * Load class metadata from classpath using ASM ClassReader.
     */
    public static JavaClassMetadata load(String fullClassName) {
        String internalName = fullClassName.replace('.', '/');
        String resourcePath = internalName + ".class";

        InputStream is = ClassLoader.getSystemResourceAsStream(resourcePath);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
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
        List<JavaMethodSignature> methods = new ArrayList<>();
        List<JavaMethodSignature> constructors = new ArrayList<>();

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                isInterface[0] = (access & Opcodes.ACC_INTERFACE) != 0;
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

        return new JavaClassMetadata(simpleName, fullClassName, internalName, isInterface[0], methods, constructors);
    }

    public String getSimpleName() { return _simpleName; }
    public String getFullName() { return _fullName; }
    public String getInternalName() { return _internalName; }
    public boolean isInterface() { return _isInterface; }

    public JavaMethodSignature resolveMethod(String name, int argCount) {
        for (JavaMethodSignature sig : _methods) {
            if (sig.getName().equals(name) && sig.getParamCount() == argCount) {
                return sig;
            }
        }
        return null;
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
}
