package codeanalysis.binding;

import codeanalysis.JavaClassInfo;
import codeanalysis.JavaTypeMapper;
import java.util.Collections;
import java.util.Iterator;

public class BoundJavaStaticFieldExpression extends BoundExpression {
    private final JavaClassInfo _classInfo;
    private final String _fieldName;
    private final String _fieldDescriptor;

    public BoundJavaStaticFieldExpression(JavaClassInfo classInfo, String fieldName, String fieldDescriptor) {
        _classInfo = classInfo;
        _fieldName = fieldName;
        _fieldDescriptor = fieldDescriptor;
    }

    public JavaClassInfo getClassInfo() { return _classInfo; }
    public String getFieldName() { return _fieldName; }
    public String getFieldDescriptor() { return _fieldDescriptor; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.JavaStaticFieldExpression; }

    @Override
    public Class<?> getClassType() {
        return JavaTypeMapper.descriptorToSiyoType(_fieldDescriptor);
    }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
