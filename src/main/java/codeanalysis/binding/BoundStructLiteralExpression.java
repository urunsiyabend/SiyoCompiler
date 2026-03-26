package codeanalysis.binding;

import codeanalysis.SiyoStruct;
import codeanalysis.StructSymbol;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class BoundStructLiteralExpression extends BoundExpression {
    private final StructSymbol _structType;
    private final Map<String, BoundExpression> _fieldValues;

    public BoundStructLiteralExpression(StructSymbol structType, Map<String, BoundExpression> fieldValues) {
        _structType = structType;
        _fieldValues = fieldValues;
    }

    public StructSymbol getStructType() { return _structType; }
    public Map<String, BoundExpression> getFieldValues() { return _fieldValues; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.StructLiteralExpression; }

    @Override
    public Class<?> getClassType() { return SiyoStruct.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
