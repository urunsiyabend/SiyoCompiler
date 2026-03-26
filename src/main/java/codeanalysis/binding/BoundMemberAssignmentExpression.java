package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;

public class BoundMemberAssignmentExpression extends BoundExpression {
    private final BoundExpression _target;
    private final String _memberName;
    private final BoundExpression _value;

    public BoundMemberAssignmentExpression(BoundExpression target, String memberName, BoundExpression value) {
        _target = target;
        _memberName = memberName;
        _value = value;
    }

    public BoundExpression getTarget() { return _target; }
    public String getMemberName() { return _memberName; }
    public BoundExpression getValue() { return _value; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.MemberAssignmentExpression; }

    @Override
    public Class<?> getClassType() { return _value.getClassType(); }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
