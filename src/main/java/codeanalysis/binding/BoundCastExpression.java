package codeanalysis.binding;

import codeanalysis.JavaClassInfo;
import java.util.Collections;
import java.util.Iterator;

public class BoundCastExpression extends BoundExpression {
    private final BoundExpression _expression;
    private final JavaClassInfo _targetClassInfo;

    public BoundCastExpression(BoundExpression expression, JavaClassInfo targetClassInfo) {
        _expression = expression;
        _targetClassInfo = targetClassInfo;
    }

    public BoundExpression getExpression() { return _expression; }
    public JavaClassInfo getTargetClassInfo() { return _targetClassInfo; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.CastExpression; }

    @Override
    public Class<?> getClassType() { return Object.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
