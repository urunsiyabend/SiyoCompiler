package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An interface declaration: a name and the methods a struct must have to be
 * one.
 *
 * <pre>
 * interface Printable {
 *     fn describe() -&gt; string
 * }
 * </pre>
 *
 * <p>The methods are signatures with no body. A struct becomes a
 * {@code Printable} by an {@code impl Printable for Point} block.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class InterfaceDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _interfaceKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _openBrace;
    private final List<FunctionDeclarationSyntax> _methods;
    private final SyntaxToken _closeBrace;

    public InterfaceDeclarationSyntax(SyntaxToken interfaceKeyword, SyntaxToken identifier,
                                      SyntaxToken openBrace, List<FunctionDeclarationSyntax> methods,
                                      SyntaxToken closeBrace) {
        _interfaceKeyword = interfaceKeyword;
        _identifier = identifier;
        _openBrace = openBrace;
        _methods = methods;
        _closeBrace = closeBrace;
    }

    /**
     * Gets the interface's name.
     *
     * @return The identifier token.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the method signatures the interface requires.
     *
     * @return The declared methods, each with no body.
     */
    public List<FunctionDeclarationSyntax> getMethods() {
        return _methods;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.InterfaceDeclaration;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(_interfaceKeyword);
        children.add(_identifier);
        children.add(_openBrace);
        children.addAll(_methods);
        children.add(_closeBrace);
        return children.iterator();
    }
}
