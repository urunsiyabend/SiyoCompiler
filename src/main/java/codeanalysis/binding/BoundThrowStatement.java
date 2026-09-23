package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound throw statement in the code analysis process.
 * The value of the expression is raised as an error and is what a catch block
 * binds, so an error may carry a variant, a struct or a number rather than only
 * text.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundThrowStatement extends BoundStatement {
    private final BoundExpression _expression;

    /**
     * Constructs a new instance of the BoundThrowStatement class.
     *
     * @param expression The expression whose value is raised.
     */
    public BoundThrowStatement(BoundExpression expression) {
        _expression = expression;
    }

    /**
     * Gets the expression whose value is raised.
     *
     * @return The expression.
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
        return BoundNodeType.ThrowStatement;
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
