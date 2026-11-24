package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a struct declaration: struct Point { x: int, y: int }
 */
public class StructDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _structKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _openBrace;
    private final List<ParameterSyntax> _fields;
    private final SyntaxToken _closeBrace;

    public StructDeclarationSyntax(SyntaxToken structKeyword, SyntaxToken identifier, SyntaxToken openBrace, List<ParameterSyntax> fields, SyntaxToken closeBrace) {
        _structKeyword = structKeyword;
        _identifier = identifier;
        _openBrace = openBrace;
        _fields = fields;
        _closeBrace = closeBrace;
    }

    private List<String> _typeParameters = List.of();

    public SyntaxToken getStructKeyword() { return _structKeyword; }

    /**
     * The type parameters this struct declares, as in
     * {@code struct Group<T> { key: string, items: Array<T> }}.
     *
     * @return The type parameter names, empty when it declares none.
     */
    public List<String> getTypeParameters() { return _typeParameters; }

    /** Records the type parameters parsed after the struct's name. */
    public void setTypeParameters(List<String> typeParameters) {
        _typeParameters = typeParameters == null ? List.of() : typeParameters;
    }
    public SyntaxToken getIdentifier() { return _identifier; }
    public List<ParameterSyntax> getFields() { return _fields; }

    @Override
    public SyntaxType getType() {
        return SyntaxType.StructDeclaration;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_structKeyword).iterator();
    }
}
