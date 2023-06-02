package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound if statement in the code analysis process.
 * Bound if statements are used to represent if statements in the code analysis process.
 * Bound if statements have a condition, a statement to be executed if the condition is true and a statement to be executed if the condition is false.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundIfStatement extends BoundStatement {
    private final BoundExpression _condition;
    private final BoundStatement _thenStatement;
    private final BoundStatement _elseStatement;

    /**
     * Constructs a new instance of the BoundIfStatement class.
     *
     * @param condition     The condition of the if statement.
     * @param thenStatement The statement to be executed if the condition is true.
     * @param elseStatement The statement to be executed if the condition is false.
     */
    public BoundIfStatement(BoundExpression condition, BoundStatement thenStatement, BoundStatement elseStatement) {
        _condition = condition;
        _thenStatement = thenStatement;
        _elseStatement = elseStatement;
    }

    /**
     * Gets the condition expression of the bound node.
     *
     * @return The condition expression of the bound node.
     */
    public BoundExpression getCondition() {
        return _condition;
    }

    /**
     * Gets the statement to be executed if the condition is true.
     *
     * @return The statement to be executed if the condition is true.
     */
    public BoundStatement getThenStatement() {
        return _thenStatement;
    }

    /**
     * Gets the statement to be executed if the condition is false.
     *
     * @return The statement to be executed if the condition is false.
     */
    public BoundStatement getElseStatement() {
        return _elseStatement;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.IfStatement;
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
            return _elseStatement != null ? _index < 3 : _index < 2;
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
                case 0 -> _condition;
                case 1 -> _thenStatement;
                case 2 -> _elseStatement == null ? next() : _elseStatement;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
