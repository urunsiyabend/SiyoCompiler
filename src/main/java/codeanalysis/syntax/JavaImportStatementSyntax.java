package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * import java "java.io.File"
 */
public class JavaImportStatementSyntax extends StatementSyntax {
    private final SyntaxToken _importKeyword;
    private final SyntaxToken _javaKeyword;
    private final SyntaxToken _className;

    public JavaImportStatementSyntax(SyntaxToken importKeyword, SyntaxToken javaKeyword, SyntaxToken className) {
        _importKeyword = importKeyword;
        _javaKeyword = javaKeyword;
        _className = className;
    }

    public SyntaxToken getClassName() { return _className; }

    @Override
    public SyntaxType getType() { return SyntaxType.JavaImportStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_importKeyword).iterator();
    }
}
