package codeanalysis.binding;

import codeanalysis.VariableSymbol;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class BoundSpawnExpression extends BoundExpression {
    private final BoundBlockStatement _body;
    private final Set<VariableSymbol> _capturedVariables; // only immutable vars allowed

    public BoundSpawnExpression(BoundBlockStatement body, Set<VariableSymbol> capturedVariables) {
        _body = body;
        _capturedVariables = capturedVariables;
    }

    public BoundBlockStatement getBody() { return _body; }
    public Set<VariableSymbol> getCapturedVariables() { return _capturedVariables; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.SpawnExpression; }

    private Class<?> _returnType = null;
    private String _actorTypeName = null;
    public void setReturnType(Class<?> type) { _returnType = type; }
    public void setActorTypeName(String name) { _actorTypeName = name; }
    public String getActorTypeName() { return _actorTypeName; }
    public Class<?> getClassType() { return _returnType; }

    @Override
    public Iterator<BoundNode> getChildren() { return Collections.emptyIterator(); }
}
