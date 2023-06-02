package codeanalysis.binding;

import codeanalysis.LabelSymbol;

import java.util.Collections;
import java.util.Iterator;

/**
 * Class that represents a bound label statement in the code analysis process.
 * A bound label statement is a statement that contains a label.
 * It is used to jump to a label.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundLabelStatement extends BoundStatement {
    private final LabelSymbol _label;

    /**
     * Constructs a BoundLabelStatement object with the specified label.
     * The label is the label to jump to.
     *
     * @param label The label to jump to.
     */
    public BoundLabelStatement(LabelSymbol label) {
        _label = label;
    }

    /**
     * Gets the label to jump to.
     *
     * @return The label to jump to.
     */
    public LabelSymbol getLabel() {
        return _label;
    }

    /**
     * Gets the type of the bound label statement.
     *
     * @return The type of the bound label statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.LabelStatement;
    }

    /**
     * Gets an iterator that iterates over the children of the bound node.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<BoundNode> getChildren() {
        return Collections.emptyIterator();
    }
}
