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
    private final Class<?> _resultType;

    public BoundClosureCallExpression(BoundExpression closure, List<BoundExpression> arguments) {
        this(closure, arguments, Object.class);
    }

    /**
     * @param closure    The expression holding the closure.
     * @param arguments  The call arguments.
     * @param resultType The declared return type, or {@code Object} when the
     *                   closure's shape is only known at run time.
     */
    public BoundClosureCallExpression(BoundExpression closure, List<BoundExpression> arguments,
                                      Class<?> resultType) {
        _closure = closure;
        _arguments = arguments;
        _resultType = resultType;
    }

    public BoundExpression getClosure() { return _closure; }
    public List<BoundExpression> getArguments() { return _arguments; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.ClosureCallExpression; }

    @Override
    public Class<?> getClassType() { return _resultType; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
