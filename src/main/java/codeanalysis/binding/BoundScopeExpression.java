package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;

public class BoundScopeExpression extends BoundExpression {
    private final BoundBlockStatement _body;

    public BoundScopeExpression(BoundBlockStatement body) {
        _body = body;
    }

    public BoundBlockStatement getBody() { return _body; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.ScopeExpression; }

    @Override
    public Class<?> getClassType() { return null; } // void

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
