package codeanalysis.binding;

import codeanalysis.LabelSymbol;
import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a bound continue statement.
 */
public class BoundContinueStatement extends BoundStatement {
    private final LabelSymbol _continueLabel;

    public BoundContinueStatement(LabelSymbol continueLabel) {
        _continueLabel = continueLabel;
    }

    public LabelSymbol getContinueLabel() {
        return _continueLabel;
    }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ContinueStatement;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        return Collections.emptyIterator();
    }
}
