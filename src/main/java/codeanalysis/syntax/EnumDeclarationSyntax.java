package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an enum declaration: {@code enum Color { Red, Green, Blue }}.
 *
 * <p>A member may carry an explicit value — {@code enum Status { OK = 200 }} —
 * which is what modelling an external protocol needs. Members without one
 * continue from the previous value, as in C and Java.
 */
public class EnumDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _enumKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _openBrace;
    private final List<SyntaxToken> _members;
    private final List<Integer> _explicitValues;
    private final SyntaxToken _closeBrace;

    public EnumDeclarationSyntax(SyntaxToken enumKeyword, SyntaxToken identifier, SyntaxToken openBrace, List<SyntaxToken> members, SyntaxToken closeBrace) {
        this(enumKeyword, identifier, openBrace, members, java.util.Collections.nCopies(members.size(), null), closeBrace);
    }

    public EnumDeclarationSyntax(SyntaxToken enumKeyword, SyntaxToken identifier, SyntaxToken openBrace,
                                 List<SyntaxToken> members, List<Integer> explicitValues, SyntaxToken closeBrace) {
        _enumKeyword = enumKeyword;
        _identifier = identifier;
        _openBrace = openBrace;
        _members = members;
        _explicitValues = explicitValues;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getIdentifier() { return _identifier; }
    public List<SyntaxToken> getMembers() { return _members; }

    /**
     * The value written for each member, parallel to {@link #getMembers()},
     * with null where the member had none.
     */
    public List<Integer> getExplicitValues() { return _explicitValues; }

    @Override
    public SyntaxType getType() { return SyntaxType.EnumDeclaration; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_enumKeyword).iterator();
    }
}
