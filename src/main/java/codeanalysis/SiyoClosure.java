package codeanalysis;

import codeanalysis.binding.BoundBlockStatement;
import java.util.List;
import java.util.Map;

/**
 * Runtime representation of a closure (first-class function) in Siyo.
 * Captures variables from the enclosing scope at creation time.
 */
public class SiyoClosure {
    private final List<ParameterSymbol> _parameters;
    private final BoundBlockStatement _body;
    private final Map<VariableSymbol, Object> _capturedVars;
    private final Class<?> _returnType;

    public SiyoClosure(List<ParameterSymbol> parameters, BoundBlockStatement body,
                        Map<VariableSymbol, Object> capturedVars, Class<?> returnType) {
        _parameters = parameters;
        _body = body;
        _capturedVars = capturedVars;
        _returnType = returnType;
    }

    public List<ParameterSymbol> getParameters() { return _parameters; }
    public BoundBlockStatement getBody() { return _body; }
    public Map<VariableSymbol, Object> getCapturedVars() { return _capturedVars; }
    public Class<?> getReturnType() { return _returnType; }
    public int getParamCount() { return _parameters.size(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("fn(");
        for (int i = 0; i < _parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(_parameters.get(i).getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
