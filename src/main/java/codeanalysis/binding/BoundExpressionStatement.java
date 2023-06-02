package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Class for representing bound expression statements in the code analysis process.
 * A bound expression statement is a statement that contains an expression.
 * It is used to evaluate an expression.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundExpressionStatement extends BoundStatement {
    private final BoundExpression _expression;

    /**
     * Constructs a BoundExpressionStatement object with the specified expression.
     * The expression is the expression to be contained in the expression statement.
     *
     * @param expression The expression to be contained in the expression statement.
     */
    public BoundExpressionStatement(BoundExpression expression) {
        _expression = expression;
    }

    /**
     * Gets the expression contained in the expression statement.
     *
     * @return The expression contained in the expression statement.
     */
    public BoundExpression getExpression() {
        return _expression;
    }

    /**
     * Gets the type of the bound expression statement.
     *
     * @return The class type of the bound expression statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ExpressionStatement;
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

    /**
     * Iterator class that iterates over the children of the bound node.
     */
    private class ChildrenIterator implements Iterator<BoundNode> {
        private int _index = 0;

        /**
         * Checks whether there is a next element in the iterator.
         *
         * @return True if there is a next element, false otherwise.
         */
        @Override
        public boolean hasNext() {
            return _index < 1;
        }

        /**
         * Retrieves the next element in the iterator.
         *
         * @return The next element.
         */
        @Override
        public BoundNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return switch (_index++) {
                case 0 -> _expression;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
