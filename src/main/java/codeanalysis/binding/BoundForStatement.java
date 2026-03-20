package codeanalysis.binding;

import codeanalysis.LabelSymbol;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The BoundForStatement class represents a for statement in the bound tree.
 * It consists of a bound statement representing the initializer, a bound expression representing the condition,
 * a bound expression representing the iterator, and a bound statement representing the body.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundForStatement extends BoundStatement {
    private final BoundStatement _initializer;
    private final BoundExpression _condition;
    private final BoundExpression _iterator;
    private final BoundStatement _body;
    private final LabelSymbol _breakLabel;
    private final LabelSymbol _continueLabel;

    public BoundForStatement(BoundStatement initializer, BoundExpression condition, BoundExpression iterator, BoundStatement body) {
        this(initializer, condition, iterator, body, null, null);
    }

    public BoundForStatement(BoundStatement initializer, BoundExpression condition, BoundExpression iterator, BoundStatement body, LabelSymbol breakLabel, LabelSymbol continueLabel) {
        _initializer = initializer;
        _condition = condition;
        _iterator = iterator;
        _body = body;
        _breakLabel = breakLabel;
        _continueLabel = continueLabel;
    }

    public LabelSymbol getBreakLabel() { return _breakLabel; }
    public LabelSymbol getContinueLabel() { return _continueLabel; }

    /**
     * Retrieves the type of the bound node.
     *
     * @return The bound node type.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ForStatement;
    }

    /**
     * Retrieves the initializer.
     *
     * @return The initializer.
     */
    public BoundStatement getInitializer() {
        return _initializer;
    }

    /**
     * Retrieves the condition.
     *
     * @return The condition.
     */
    public BoundExpression getCondition() {
        return _condition;
    }

    /**
     * Retrieves the iterator.
     *
     * @return The iterator.
     */
    public BoundExpression getIterator() {
        return _iterator;
    }

    /**
     * Retrieves the body.
     *
     * @return The body.
     */
    public BoundStatement getBody() {
        return _body;
    }

    /**
     * Gets an iterator that iterates over the children of the bound node.
     *
     * @return The iterator.
     */
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
                case 0 -> _body;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
