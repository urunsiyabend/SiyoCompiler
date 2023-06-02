package codeanalysis.binding;

import codeanalysis.LabelSymbol;

import java.util.Collections;
import java.util.Iterator;

/**
 * Class that represents a bound goto statement in the code analysis process.
 * A bound goto statement is a statement that contains a label.
 * It is used to jump to a label.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundGotoStatement extends BoundStatement {
    private final LabelSymbol _label;

    /**
     * Constructs a BoundGotoStatement object with the specified label.
     * The label is the label to jump to.
     *
     * @param label The label to jump to.
     */
    public BoundGotoStatement(LabelSymbol label) {
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
     * Gets the type of the bound goto statement.
     *
     * @return The class type of the bound goto statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.GotoStatement;
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
