package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;

public class BoundIndexAssignmentExpression extends BoundExpression {
    private final BoundExpression _target;
    private final BoundExpression _index;
    private final BoundExpression _value;

    public BoundIndexAssignmentExpression(BoundExpression target, BoundExpression index, BoundExpression value) {
        _target = target;
        _index = index;
        _value = value;
    }

    public BoundExpression getTarget() { return _target; }
    public BoundExpression getIndex() { return _index; }
    public BoundExpression getValue() { return _value; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.IndexAssignmentExpression; }

    @Override
    public Class<?> getClassType() { return _value.getClassType(); }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
