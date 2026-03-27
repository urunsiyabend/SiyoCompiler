package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

public class ImplDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _implKeyword;
    private final SyntaxToken _typeName;
    private final SyntaxToken _openBrace;
    private final List<FunctionDeclarationSyntax> _methods;
    private final SyntaxToken _closeBrace;

    public ImplDeclarationSyntax(SyntaxToken implKeyword, SyntaxToken typeName,
                                  SyntaxToken openBrace, List<FunctionDeclarationSyntax> methods,
                                  SyntaxToken closeBrace) {
        _implKeyword = implKeyword;
        _typeName = typeName;
        _openBrace = openBrace;
        _methods = methods;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getTypeName() { return _typeName; }
    public List<FunctionDeclarationSyntax> getMethods() { return _methods; }

    @Override
    public SyntaxType getType() { return SyntaxType.ImplDeclaration; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_implKeyword, _typeName).iterator();
    }
}
