package codeanalysis.binding;

import codeanalysis.LabelSymbol;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Class that represents a bound conditional goto statement in the code analysis process.
 * A bound conditional goto statement is a statement that contains a label and a condition.
 * It is used to jump to a label if the condition is true or false.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundConditionalGotoStatement extends BoundStatement {
    private final LabelSymbol _label;
    private final BoundExpression _condition;
    private final boolean _jumpIfFalse;

    /**
     * Constructs a BoundConditionalGotoStatement object with the specified label and condition.
     * The label is the label to jump to if the condition is true or false.
     *
     * @param label The label to jump to if the condition is true or false.
     * @param condition The condition to be checked.
     */
    public BoundConditionalGotoStatement(LabelSymbol label, BoundExpression condition) {
        this(label, condition, false);
    }

    /**
     * Constructs a BoundConditionalGotoStatement object with the specified label, condition and jumpIfFalse.
     * The label is the label to jump to if the condition is true or false.
     * The jumpIfFalse is the boolean value that indicates whether the jump should be made if the condition is false.
     *
     * @param label The label to jump to if the condition is true or false.
     * @param condition The condition to be checked.
     * @param jumpIfFalse The boolean value that indicates whether the jump should be made if the condition is false.
     */
    public BoundConditionalGotoStatement(LabelSymbol label, BoundExpression condition, boolean jumpIfFalse) {
        _label = label;
        _condition = condition;
        _jumpIfFalse = jumpIfFalse;
    }

    /**
     * Gets the label to jump to if the condition is true or false.
     * The label is the label to jump to if the condition is true or false.
     *
     * @return The label to jump to if the condition is true or false.
     */
    public LabelSymbol getLabel() {
        return _label;
    }

    /**
     * Gets the condition to be checked.
     *
     * @return The condition to be checked.
     */
    public BoundExpression getCondition() {
        return _condition;
    }

    /**
     * Gets the boolean value that indicates whether the jump should be made if the condition is false.
     *
     * @return The boolean value that indicates whether the jump should be made if the condition is false.
     */
    public boolean getJumpIfFalse() {
        return _jumpIfFalse;
    }

    /**
     * Gets the type of the bound conditional goto statement.
     *
     * @return The type of the bound conditional goto statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ConditionalGotoStatement;
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
                case 0 -> _condition;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
