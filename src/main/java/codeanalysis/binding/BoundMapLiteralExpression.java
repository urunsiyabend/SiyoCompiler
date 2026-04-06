package codeanalysis.binding;

import codeanalysis.SiyoMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BoundMapLiteralExpression extends BoundExpression {
    private final List<BoundExpression> _keys;
    private final List<BoundExpression> _values;

    public BoundMapLiteralExpression(List<BoundExpression> keys, List<BoundExpression> values) {
        _keys = keys;
        _values = values;
    }

    public List<BoundExpression> getKeys() { return _keys; }
    public List<BoundExpression> getValues() { return _values; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.MapLiteralExpression; }

    @Override
    public Class<?> getClassType() { return SiyoMap.class; }

    @Override
    public Iterator<BoundNode> getChildren() {
        List<BoundNode> children = new ArrayList<>();
        children.addAll(_keys);
        children.addAll(_values);
        return children.iterator();
    }
}
