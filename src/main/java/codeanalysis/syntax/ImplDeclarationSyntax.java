package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

/**
 * Methods on a struct: {@code impl Point { ... }}, or the methods that make it
 * an interface: {@code impl Printable for Point { ... }}.
 */
public class ImplDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _implKeyword;
    private final SyntaxToken _typeName;
    private final SyntaxToken _interfaceName;
    private final SyntaxToken _openBrace;
    private final List<FunctionDeclarationSyntax> _methods;
    private final SyntaxToken _closeBrace;

    public ImplDeclarationSyntax(SyntaxToken implKeyword, SyntaxToken typeName,
                                  SyntaxToken openBrace, List<FunctionDeclarationSyntax> methods,
                                  SyntaxToken closeBrace) {
        this(implKeyword, typeName, null, openBrace, methods, closeBrace);
    }

    public ImplDeclarationSyntax(SyntaxToken implKeyword, SyntaxToken typeName,
                                  SyntaxToken interfaceName, SyntaxToken openBrace,
                                  List<FunctionDeclarationSyntax> methods, SyntaxToken closeBrace) {
        _implKeyword = implKeyword;
        _typeName = typeName;
        _interfaceName = interfaceName;
        _openBrace = openBrace;
        _methods = methods;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getTypeName() { return _typeName; }

    /** The interface these methods implement, or null when there is none. */
    public SyntaxToken getInterfaceName() { return _interfaceName; }
    public List<FunctionDeclarationSyntax> getMethods() { return _methods; }

    @Override
    public SyntaxType getType() { return SyntaxType.ImplDeclaration; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_implKeyword, _typeName).iterator();
    }
}
