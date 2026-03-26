package codeanalysis.binding;

import codeanalysis.StructSymbol;
import java.util.Collections;
import java.util.Iterator;

public class BoundMemberAccessExpression extends BoundExpression {
    private final BoundExpression _target;
    private final String _memberName;
    private final Class<?> _memberType;

    public BoundMemberAccessExpression(BoundExpression target, String memberName, Class<?> memberType) {
        _target = target;
        _memberName = memberName;
        _memberType = memberType;
    }

    public BoundExpression getTarget() { return _target; }
    public String getMemberName() { return _memberName; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.MemberAccessExpression; }

    @Override
    public Class<?> getClassType() { return _memberType; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
