package codeanalysis.binding;

import java.util.Iterator;

/**
 * Bound send statement: fire-and-forget actor dispatch.
 * Wraps a BoundCallExpression that should use actor.send() instead of actor.call().
 */
public class BoundSendStatement extends BoundStatement {
    private final BoundExpression _expression;

    public BoundSendStatement(BoundExpression expression) {
        _expression = expression;
    }

    public BoundExpression getExpression() { return _expression; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.SendStatement; }

    @Override
    public Iterator<BoundNode> getChildren() {
        return new Iterator<>() {
            boolean done = false;
            @Override public boolean hasNext() { return !done; }
            @Override public BoundNode next() { done = true; return _expression; }
        };
    }
}
