package codeanalysis.binding;

import codeanalysis.SiyoStruct;
import codeanalysis.StructSymbol;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class BoundStructLiteralExpression extends BoundExpression {
    private final StructSymbol _structType;
    private final Map<String, BoundExpression> _fieldValues;

    private final String _typeName;

    public BoundStructLiteralExpression(StructSymbol structType, Map<String, BoundExpression> fieldValues) {
        this(structType, fieldValues, null);
    }

    /**
     * @param typeName The literal's type with the type arguments its fields gave
     *                 a generic struct, such as {@code Group<Row>}, or null.
     */
    public BoundStructLiteralExpression(StructSymbol structType, Map<String, BoundExpression> fieldValues,
                                        String typeName) {
        _structType = structType;
        _fieldValues = fieldValues;
        _typeName = typeName;
    }

    public StructSymbol getStructType() { return _structType; }

    /** The literal's type written with its type arguments, or null for a plain struct. */
    public String getTypeName() { return _typeName; }
    public Map<String, BoundExpression> getFieldValues() { return _fieldValues; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.StructLiteralExpression; }

    @Override
    public Class<?> getClassType() { return SiyoStruct.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
