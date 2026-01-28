package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound return statement in the code analysis process.
 * A bound return statement contains an optional expression to return.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundReturnStatement extends BoundStatement {
    private final BoundExpression _expression;

    /**
     * Constructs a new instance of the BoundReturnStatement class.
     *
     * @param expression The expression to return (can be null for void functions).
     */
    public BoundReturnStatement(BoundExpression expression) {
        _expression = expression;
    }

    /**
     * Gets the expression to return.
     *
     * @return The expression, or null if no expression is returned.
     */
    public BoundExpression getExpression() {
        return _expression;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ReturnStatement;
    }

    /**
     * Gets an iterator that iterates over the children of the bound node.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<BoundNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<BoundNode> {
        private int _index = 0;

        @Override
        public boolean hasNext() {
            return _expression != null && _index < 1;
        }

        @Override
        public BoundNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            _index++;
            return _expression;
        }
    }
}
