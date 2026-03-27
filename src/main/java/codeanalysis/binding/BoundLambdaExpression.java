package codeanalysis.binding;

import codeanalysis.ParameterSymbol;
import codeanalysis.SiyoClosure;
import codeanalysis.VariableSymbol;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Bound lambda expression. Knows its parameters, body, return type, and captured variables.
 */
public class BoundLambdaExpression extends BoundExpression {
    private final List<ParameterSymbol> _parameters;
    private final BoundBlockStatement _body;
    private final Class<?> _returnType;
    private final Set<VariableSymbol> _capturedVariables;

    public BoundLambdaExpression(List<ParameterSymbol> parameters, BoundBlockStatement body,
                                  Class<?> returnType, Set<VariableSymbol> capturedVariables) {
        _parameters = parameters;
        _body = body;
        _returnType = returnType;
        _capturedVariables = capturedVariables;
    }

    public List<ParameterSymbol> getParameters() { return _parameters; }
    public BoundBlockStatement getBody() { return _body; }
    public Class<?> getReturnType() { return _returnType; }
    public Set<VariableSymbol> getCapturedVariables() { return _capturedVariables; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.LambdaExpression; }

    @Override
    public Class<?> getClassType() { return SiyoClosure.class; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
