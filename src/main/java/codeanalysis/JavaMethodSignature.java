package codeanalysis;

/**
 * Compile-time resolved method signature for Java interop.
 * No reflection needed - all info comes from ASM ClassReader.
 */
public class JavaMethodSignature {
    private final String _name;
    private final String _descriptor;         // full descriptor: "(Ljava/lang/String;)Z"
    private final String _returnDescriptor;   // "Z", "Ljava/lang/String;", "V"
    private final String[] _paramDescriptors; // ["Ljava/lang/String;", "I"]
    private final String _ownerInternalName;  // "java/io/File"
    private final boolean _isStatic;
    private final boolean _isConstructor;
    private final boolean _isInterface;       // owner is interface → INVOKEINTERFACE
    private final int _invokeOpcode;          // INVOKEVIRTUAL, INVOKESTATIC, etc.

    public JavaMethodSignature(String name, String descriptor, String returnDescriptor,
                                String[] paramDescriptors, String ownerInternalName,
                                boolean isStatic, boolean isConstructor, boolean isInterface) {
        _name = name;
        _descriptor = descriptor;
        _returnDescriptor = returnDescriptor;
        _paramDescriptors = paramDescriptors;
        _ownerInternalName = ownerInternalName;
        _isStatic = isStatic;
        _isConstructor = isConstructor;
        _isInterface = isInterface;

        if (isConstructor) _invokeOpcode = org.objectweb.asm.Opcodes.INVOKESPECIAL;
        else if (isStatic) _invokeOpcode = org.objectweb.asm.Opcodes.INVOKESTATIC;
        else if (isInterface) _invokeOpcode = org.objectweb.asm.Opcodes.INVOKEINTERFACE;
        else _invokeOpcode = org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
    }

    public String getName() { return _name; }
    public String getDescriptor() { return _descriptor; }
    public String getReturnDescriptor() { return _returnDescriptor; }
    public String[] getParamDescriptors() { return _paramDescriptors; }
    public String getOwnerInternalName() { return _ownerInternalName; }
    public boolean isStatic() { return _isStatic; }
    public boolean isConstructor() { return _isConstructor; }
    public boolean isInterface() { return _isInterface; }
    public int getInvokeOpcode() { return _invokeOpcode; }
    public int getParamCount() { return _paramDescriptors.length; }
}
