package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Calling a closure variable: f(args) where f is a SiyoClosure.
 */
public class BoundClosureCallExpression extends BoundExpression {
    private final BoundExpression _closure;
    private final List<BoundExpression> _arguments;

    public BoundClosureCallExpression(BoundExpression closure, List<BoundExpression> arguments) {
        _closure = closure;
        _arguments = arguments;
    }

    public BoundExpression getClosure() { return _closure; }
    public List<BoundExpression> getArguments() { return _arguments; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.ClosureCallExpression; }

    @Override
    public Class<?> getClassType() { return Object.class; } // return type determined at runtime

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
