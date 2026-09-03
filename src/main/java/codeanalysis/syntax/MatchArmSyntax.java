package codeanalysis.syntax;

import java.util.Iterator;

/**
 * A single arm in a match expression: {@code pattern => body}.
 *
 * <p>A pattern is one of three things: an expression, which the arm compares
 * the matched value against; {@code _}, which matches anything; or a variant
 * pattern — {@code Ok(value)} — which selects one variant of a sum type and
 * binds its payload to names for the body to use.</p>
 */
public class MatchArmSyntax extends SyntaxNode {
    private final ExpressionSyntax _pattern; // null for _ (default) and for a variant pattern
    private final SyntaxToken _arrow;
    private final ExpressionSyntax _body;
    private final boolean _isDefault;
    private final SyntaxToken _variantTypeName; // the T in T.Variant(...), or null
    private final SyntaxToken _variantName;     // the variant selected, or null
    private final java.util.List<SyntaxToken> _bindings; // one per payload slot, null entry for _

    public MatchArmSyntax(ExpressionSyntax pattern, SyntaxToken arrow, ExpressionSyntax body, boolean isDefault) {
        this(pattern, arrow, body, isDefault, null, null, null);
    }

    public MatchArmSyntax(ExpressionSyntax pattern, SyntaxToken arrow, ExpressionSyntax body, boolean isDefault,
                          SyntaxToken variantTypeName, SyntaxToken variantName,
                          java.util.List<SyntaxToken> bindings) {
        _pattern = pattern;
        _arrow = arrow;
        _body = body;
        _isDefault = isDefault;
        _variantTypeName = variantTypeName;
        _variantName = variantName;
        _bindings = bindings;
    }

    /**
     * Whether this arm selects a variant of a sum type and binds its payload.
     *
     * @return true when it is a variant pattern.
     */
    public boolean isVariantPattern() { return _variantName != null; }

    /**
     * The type qualifying the variant, when the pattern was written
     * {@code Result.Ok(v)}.
     *
     * @return The type name token, or null when the variant was written bare.
     */
    public SyntaxToken getVariantTypeName() { return _variantTypeName; }

    /**
     * The variant this arm selects.
     *
     * @return The variant name token, or null for a non-variant pattern.
     */
    public SyntaxToken getVariantName() { return _variantName; }

    /**
     * The name each payload slot is bound to, in order, with a null entry where
     * the pattern wrote {@code _} and discarded the slot.
     *
     * @return The bindings, or null for a non-variant pattern.
     */
    public java.util.List<SyntaxToken> getBindings() { return _bindings; }

    public ExpressionSyntax getPattern() { return _pattern; }
    public SyntaxToken getArrow() { return _arrow; }
    public ExpressionSyntax getBody() { return _body; }
    public boolean isDefault() { return _isDefault; }

    @Override
    public SyntaxType getType() { return SyntaxType.MatchArm; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        java.util.List<SyntaxNode> children = new java.util.ArrayList<>();
        if (_variantTypeName != null) children.add(_variantTypeName);
        if (_variantName != null) children.add(_variantName);
        if (_pattern != null) children.add(_pattern);
        children.add(_arrow);
        children.add(_body);
        return children.iterator();
    }
}
