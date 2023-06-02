package codeanalysis.binding;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a bound while statement in the code analysis process.
 * Bound while statements are used to represent while statements in the code analysis process.
 * Bound while statements have a condition and a statement to be executed while the condition is true.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundWhileStatement extends BoundStatement {
    private final BoundExpression _condition;
    private final BoundStatement _body;

    /**
     * Constructs a new instance of the BoundWhileStatement class.
     *
     * @param condition The condition of the while statement.
     * @param body      The statement to be executed while the condition is true.
     */
    public BoundWhileStatement(BoundExpression condition, BoundStatement body) {
        _condition = condition;
        _body = body;
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
     * Gets the statement to be executed while the condition is true.
     *
     * @return The statement to be executed while the condition is true.
     */
    public BoundStatement getBody() {
        return _body;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.WhileStatement;
    }


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
                case 0 -> _condition;
                case 1 -> _body;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
