package codeanalysis;

/**
 * Compile-time resolved method signature for Java interop.
 * Includes both erased descriptor and generic signature.
 */
public class JavaMethodSignature {
    private final String _name;
    private final String _descriptor;         // erased: "(Ljava/lang/String;)Z"
    private final String _genericSignature;   // generic: "()Ljava/util/Iterator<TE;>;" (may be null)
    private final String _returnDescriptor;   // "Z", "Ljava/lang/String;", "V"
    private final String[] _paramDescriptors;
    private final String _ownerInternalName;
    private final boolean _isStatic;
    private final boolean _isConstructor;
    private final boolean _isInterface;
    private final int _invokeOpcode;

    public JavaMethodSignature(String name, String descriptor, String returnDescriptor,
                                String[] paramDescriptors, String ownerInternalName,
                                boolean isStatic, boolean isConstructor, boolean isInterface) {
        this(name, descriptor, null, returnDescriptor, paramDescriptors, ownerInternalName, isStatic, isConstructor, isInterface);
    }

    public JavaMethodSignature(String name, String descriptor, String genericSignature, String returnDescriptor,
                                String[] paramDescriptors, String ownerInternalName,
                                boolean isStatic, boolean isConstructor, boolean isInterface) {
        _name = name;
        _descriptor = descriptor;
        _genericSignature = genericSignature;
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
    public String getGenericSignature() { return _genericSignature; }
    public String getReturnDescriptor() { return _returnDescriptor; }
    public String[] getParamDescriptors() { return _paramDescriptors; }
    public String getOwnerInternalName() { return _ownerInternalName; }
    public boolean isStatic() { return _isStatic; }
    public boolean isConstructor() { return _isConstructor; }
    public boolean isInterface() { return _isInterface; }
    public int getInvokeOpcode() { return _invokeOpcode; }
    public int getParamCount() { return _paramDescriptors.length; }
}
