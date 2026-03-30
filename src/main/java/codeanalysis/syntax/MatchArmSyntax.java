package codeanalysis.syntax;

import java.util.Iterator;

/**
 * A single arm in a match expression: pattern => body
 * Pattern is an expression (literal value) or _ (default/wildcard)
 */
public class MatchArmSyntax extends SyntaxNode {
    private final ExpressionSyntax _pattern; // null for _ (default)
    private final SyntaxToken _arrow;
    private final ExpressionSyntax _body;
    private final boolean _isDefault;

    public MatchArmSyntax(ExpressionSyntax pattern, SyntaxToken arrow, ExpressionSyntax body, boolean isDefault) {
        _pattern = pattern;
        _arrow = arrow;
        _body = body;
        _isDefault = isDefault;
    }

    public ExpressionSyntax getPattern() { return _pattern; }
    public SyntaxToken getArrow() { return _arrow; }
    public ExpressionSyntax getBody() { return _body; }
    public boolean isDefault() { return _isDefault; }

    @Override
    public SyntaxType getType() { return SyntaxType.MatchArm; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < (_pattern != null ? 3 : 2); }
            @Override public SyntaxNode next() {
                if (_pattern != null && idx == 0) { idx++; return _pattern; }
                if (idx <= 1) { idx++; return _arrow; }
                idx++; return _body;
            }
        };
    }
}
