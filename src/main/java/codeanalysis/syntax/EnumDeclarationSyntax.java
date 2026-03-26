package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an enum declaration: enum Color { Red, Green, Blue }
 */
public class EnumDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _enumKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _openBrace;
    private final List<SyntaxToken> _members;
    private final SyntaxToken _closeBrace;

    public EnumDeclarationSyntax(SyntaxToken enumKeyword, SyntaxToken identifier, SyntaxToken openBrace, List<SyntaxToken> members, SyntaxToken closeBrace) {
        _enumKeyword = enumKeyword;
        _identifier = identifier;
        _openBrace = openBrace;
        _members = members;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getIdentifier() { return _identifier; }
    public List<SyntaxToken> getMembers() { return _members; }

    @Override
    public SyntaxType getType() { return SyntaxType.EnumDeclaration; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_enumKeyword).iterator();
    }
}
