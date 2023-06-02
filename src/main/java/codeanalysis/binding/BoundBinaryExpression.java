package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound binary expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundBinaryExpression extends BoundExpression {
    private final BoundBinaryOperator _operator;
    private BoundExpression _left;
    private BoundExpression _right;

    /**
     * Constructs a new instance of the BoundBinaryExpression class.
     *
     * @param left         The left operand bound expression.
     * @param operator The bound binary operator type.
     * @param right        The right operand bound expression.
     */
    public BoundBinaryExpression(BoundExpression left, BoundBinaryOperator operator, BoundExpression right) {
        _operator = operator;
        _left = left;
        _right = right;
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.BinaryExpression;
    }

    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    @Override
    public Class<?> getClassType() {
        return getOperator().getResultType();
    }

    /**
     * Gets the bound binary operator of the expression.
     *
     * @return The bound binary operator.
     */
    public BoundBinaryOperator getOperator() {
        return _operator;
    }

    /**
     * Gets the left operand bound expression.
     *
     * @return The left operand bound expression.
     */
    public BoundExpression getLeft() {
        return _left;
    }

    /**
     * Sets the left operand bound expression.
     *
     * @param left The left operand bound expression to set.
     */
    public void setLeft(BoundExpression left) {
        _left = left;
    }

    /**
     * Gets the right operand bound expression.
     *
     * @return The right operand bound expression.
     */
    public BoundExpression getRight() {
        return _right;
    }

    /**
     * Sets the right operand bound expression.
     *
     * @param right The right operand bound expression to set.
     */
    public void setRight(BoundExpression right) {
        _right = right;
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
            return _index < 2;
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
                case 0 -> _left;
                case 1 -> _right;
                default -> throw new NoSuchElementException();
            };
        }
    }
}