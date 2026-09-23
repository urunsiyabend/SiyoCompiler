package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a sum type declaration:
 * {@code type Result = Ok(int) | Err(string)}.
 *
 * <p>Each alternative is a {@link UnionVariantSyntax}: a name and an optional
 * parenthesised payload. A variant without a payload — {@code None} — is
 * written with no parentheses.</p>
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class TypeDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _typeKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _equals;
    private final List<UnionVariantSyntax> _variants;

    public TypeDeclarationSyntax(SyntaxToken typeKeyword, SyntaxToken identifier, SyntaxToken equals,
                                 List<UnionVariantSyntax> variants) {
        _typeKeyword = typeKeyword;
        _identifier = identifier;
        _equals = equals;
        _variants = variants;
    }

    /**
     * Gets the name of the declared type.
     *
     * @return The type name token.
     */
    private java.util.List<String> _typeParameters = java.util.List.of();

    /**
     * The type parameters this sum type declares, as in
     * {@code type Option<T> = Some(T) | None}.
     *
     * @return The type parameter names, empty when it declares none.
     */
    public java.util.List<String> getTypeParameters() {
        return _typeParameters;
    }

    /** Records the type parameters parsed after the type's name. */
    public void setTypeParameters(java.util.List<String> typeParameters) {
        _typeParameters = typeParameters == null ? java.util.List.of() : typeParameters;
    }

    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the declared variants, in source order.
     *
     * @return The variants.
     */
    public List<UnionVariantSyntax> getVariants() {
        return _variants;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.TypeDeclaration;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_typeKeyword).iterator();
    }
}
