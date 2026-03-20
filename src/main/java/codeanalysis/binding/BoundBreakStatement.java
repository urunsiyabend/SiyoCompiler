package codeanalysis.binding;

import codeanalysis.LabelSymbol;
import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a bound break statement.
 */
public class BoundBreakStatement extends BoundStatement {
    private final LabelSymbol _breakLabel;

    public BoundBreakStatement(LabelSymbol breakLabel) {
        _breakLabel = breakLabel;
    }

    public LabelSymbol getBreakLabel() {
        return _breakLabel;
    }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.BreakStatement;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        return Collections.emptyIterator();
    }
}
