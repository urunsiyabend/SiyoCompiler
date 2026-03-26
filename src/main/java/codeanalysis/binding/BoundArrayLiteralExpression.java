package codeanalysis.binding;

import codeanalysis.SiyoArray;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class BoundArrayLiteralExpression extends BoundExpression {
    private final List<BoundExpression> _elements;
    private final Class<?> _elementType;

    public BoundArrayLiteralExpression(List<BoundExpression> elements, Class<?> elementType) {
        _elements = elements;
        _elementType = elementType;
    }

    public List<BoundExpression> getElements() { return _elements; }
    public Class<?> getElementType() { return _elementType; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.ArrayLiteralExpression; }

    @Override
    public Class<?> getClassType() { return SiyoArray.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
