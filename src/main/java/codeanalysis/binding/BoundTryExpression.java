package codeanalysis.binding;

import codeanalysis.VariableSymbol;
import java.util.Iterator;

/**
 * try { expr } catch e { fallback } — as an expression returning a value.
 */
public class BoundTryExpression extends BoundExpression {
    private final BoundStatement _tryBody;
    private final VariableSymbol _errorVariable;
    private final BoundStatement _catchBody;
    private final Class<?> _type;

    public BoundTryExpression(BoundStatement tryBody, VariableSymbol errorVariable,
                               BoundStatement catchBody, Class<?> type) {
        _tryBody = tryBody;
        _errorVariable = errorVariable;
        _catchBody = catchBody;
        _type = type;
    }

    public BoundStatement getTryBody() { return _tryBody; }
    public VariableSymbol getErrorVariable() { return _errorVariable; }
    public BoundStatement getCatchBody() { return _catchBody; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.TryExpression; }

    @Override
    public Class<?> getClassType() { return _type; }

    @Override
    public Iterator<BoundNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 2; }
            @Override public BoundNode next() {
                return switch (idx++) {
                    case 0 -> _tryBody;
                    case 1 -> _catchBody;
                    default -> throw new java.util.NoSuchElementException();
                };
            }
        };
    }
}
