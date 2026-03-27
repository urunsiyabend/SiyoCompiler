package codeanalysis.binding;

import codeanalysis.JavaClassInfo;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a Java method/constructor call in the bound tree.
 * Covers: File.new("path"), file.exists(), Files.readString(path)
 */
public class BoundJavaMethodCallExpression extends BoundExpression {
    private final JavaClassInfo _classInfo;
    private final BoundExpression _target;      // null for static/constructor calls
    private final String _methodName;           // "new" for constructor
    private final List<BoundExpression> _arguments;
    private final boolean _isConstructor;
    private final boolean _isStatic;

    public BoundJavaMethodCallExpression(JavaClassInfo classInfo, BoundExpression target, String methodName,
                                         List<BoundExpression> arguments, boolean isConstructor, boolean isStatic) {
        _classInfo = classInfo;
        _target = target;
        _methodName = methodName;
        _arguments = arguments;
        _isConstructor = isConstructor;
        _isStatic = isStatic;
    }

    public JavaClassInfo getClassInfo() { return _classInfo; }
    public BoundExpression getTarget() { return _target; }
    public String getMethodName() { return _methodName; }
    public List<BoundExpression> getArguments() { return _arguments; }
    public boolean isConstructor() { return _isConstructor; }
    public boolean isStatic() { return _isStatic; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.JavaMethodCallExpression; }

    @Override
    public Class<?> getClassType() { return Object.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
