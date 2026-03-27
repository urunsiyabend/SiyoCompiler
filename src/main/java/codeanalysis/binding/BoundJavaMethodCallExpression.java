package codeanalysis.binding;

import codeanalysis.JavaClassInfo;
import codeanalysis.JavaMethodSignature;
import codeanalysis.JavaTypeMapper;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a Java method/constructor call in the bound tree.
 * Now includes compile-time resolved method signature.
 */
public class BoundJavaMethodCallExpression extends BoundExpression {
    private final JavaClassInfo _classInfo;
    private final BoundExpression _target;
    private final String _methodName;
    private final List<BoundExpression> _arguments;
    private final JavaMethodSignature _resolvedSignature; // null for unresolved instance calls

    public BoundJavaMethodCallExpression(JavaClassInfo classInfo, BoundExpression target, String methodName,
                                         List<BoundExpression> arguments, JavaMethodSignature resolvedSignature) {
        _classInfo = classInfo;
        _target = target;
        _methodName = methodName;
        _arguments = arguments;
        _resolvedSignature = resolvedSignature;
    }

    public JavaClassInfo getClassInfo() { return _classInfo; }
    public BoundExpression getTarget() { return _target; }
    public String getMethodName() { return _methodName; }
    public List<BoundExpression> getArguments() { return _arguments; }
    public JavaMethodSignature getResolvedSignature() { return _resolvedSignature; }

    public boolean isConstructor() { return _resolvedSignature != null && _resolvedSignature.isConstructor(); }
    public boolean isStatic() { return _resolvedSignature != null && _resolvedSignature.isStatic(); }

    @Override
    public BoundNodeType getType() { return BoundNodeType.JavaMethodCallExpression; }

    @Override
    public Class<?> getClassType() {
        if (_resolvedSignature != null) {
            if (_resolvedSignature.isConstructor()) {
                return Object.class; // constructor returns the created object
            }
            return JavaTypeMapper.descriptorToSiyoType(_resolvedSignature.getReturnDescriptor());
        }
        return Object.class;
    }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
