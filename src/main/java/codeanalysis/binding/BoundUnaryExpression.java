package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound unary expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundUnaryExpression extends BoundExpression {
    private final BoundUnaryOperator _operator;
    private final BoundExpression _operand;

    /**
     * Constructs a new instance of the BoundUnaryExpression class.
     *
     * @param operator The operator of the unary expression.
     * @param operand      The operand of the unary expression.
     */
    public BoundUnaryExpression(BoundUnaryOperator operator, BoundExpression operand) {
        _operator = operator;
        _operand = operand;
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.UnaryExpression;
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
     * Gets the operand of the unary expression.
     *
     * @return The operand of the unary expression.
     */
    public BoundExpression getOperand() {
        return _operand;
    }


    /**
     * Gets the operator of the unary expression.
     *
     * @return The operator of the unary expression.
     */
    public BoundUnaryOperator getOperator() {
        return _operator;
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
                case 0 -> getOperand();
                default -> throw new NoSuchElementException();
            };
        }
    }
}