package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;

public class BoundIndexExpression extends BoundExpression {
    private final BoundExpression _target;
    private final BoundExpression _index;
    private final Class<?> _resultType;

    public BoundIndexExpression(BoundExpression target, BoundExpression index, Class<?> resultType) {
        _target = target;
        _index = index;
        _resultType = resultType;
    }

    public BoundExpression getTarget() { return _target; }
    public BoundExpression getIndex() { return _index; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.IndexExpression; }

    @Override
    public Class<?> getClassType() { return _resultType; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
