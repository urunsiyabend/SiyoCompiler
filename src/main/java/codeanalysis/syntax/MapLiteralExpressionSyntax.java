package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a map literal expression: {"key": value, "key2": value2}
 */
public class MapLiteralExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _openBrace;
    private final List<ExpressionSyntax> _keys;
    private final List<SyntaxToken> _colons;
    private final List<ExpressionSyntax> _values;
    private final SyntaxToken _closeBrace;

    public MapLiteralExpressionSyntax(SyntaxToken openBrace,
                                       List<ExpressionSyntax> keys,
                                       List<SyntaxToken> colons,
                                       List<ExpressionSyntax> values,
                                       SyntaxToken closeBrace) {
        _openBrace = openBrace;
        _keys = keys;
        _colons = colons;
        _values = values;
        _closeBrace = closeBrace;
    }

    public List<ExpressionSyntax> getKeys() { return _keys; }
    public List<ExpressionSyntax> getValues() { return _values; }

    @Override
    public SyntaxType getType() { return SyntaxType.MapLiteralExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(_openBrace);
        for (int i = 0; i < _keys.size(); i++) {
            children.add(_keys.get(i));
            children.add(_colons.get(i));
            children.add(_values.get(i));
        }
        children.add(_closeBrace);
        return children.iterator();
    }
}
