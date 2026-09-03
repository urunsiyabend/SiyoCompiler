package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents one alternative of a sum type declaration: {@code Ok(int)},
 * {@code Err(string)}, or a payload-less {@code None}.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class UnionVariantSyntax extends SyntaxNode {
    private final SyntaxToken _identifier;
    private final List<SyntaxToken> _payloadTypes;

    public UnionVariantSyntax(SyntaxToken identifier, List<SyntaxToken> payloadTypes) {
        _identifier = identifier;
        _payloadTypes = payloadTypes;
    }

    /**
     * Gets the variant name.
     *
     * @return The variant name token.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the declared payload types, in source order. Empty for a variant
     * written without parentheses.
     *
     * @return The payload type tokens.
     */
    public List<SyntaxToken> getPayloadTypes() {
        return _payloadTypes;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.UnionVariant;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_identifier).iterator();
    }
}
