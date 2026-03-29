package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

public class ActorDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _actorKeyword;
    private final SyntaxToken _name;
    private final SyntaxToken _openBrace;
    private final List<ParameterSyntax> _fields;
    private final SyntaxToken _closeBrace;

    public ActorDeclarationSyntax(SyntaxToken actorKeyword, SyntaxToken name,
                                   SyntaxToken openBrace, List<ParameterSyntax> fields,
                                   SyntaxToken closeBrace) {
        _actorKeyword = actorKeyword;
        _name = name;
        _openBrace = openBrace;
        _fields = fields;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getIdentifier() { return _name; }
    public List<ParameterSyntax> getFields() { return _fields; }

    @Override
    public SyntaxType getType() { return SyntaxType.ActorDeclaration; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_actorKeyword, _name).iterator();
    }
}
