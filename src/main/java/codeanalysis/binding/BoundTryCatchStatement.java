package codeanalysis.binding;

import codeanalysis.VariableSymbol;
import java.util.Collections;
import java.util.Iterator;

public class BoundTryCatchStatement extends BoundStatement {
    private final BoundStatement _tryBody;
    private final VariableSymbol _errorVariable;
    private final BoundStatement _catchBody;

    public BoundTryCatchStatement(BoundStatement tryBody, VariableSymbol errorVariable, BoundStatement catchBody) {
        _tryBody = tryBody;
        _errorVariable = errorVariable;
        _catchBody = catchBody;
    }

    public BoundStatement getTryBody() { return _tryBody; }
    public VariableSymbol getErrorVariable() { return _errorVariable; }
    public BoundStatement getCatchBody() { return _catchBody; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.TryCatchStatement; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
