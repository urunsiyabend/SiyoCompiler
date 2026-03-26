package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

public class ImportStatementSyntax extends StatementSyntax {
    private final SyntaxToken _importKeyword;
    private final SyntaxToken _moduleName;

    public ImportStatementSyntax(SyntaxToken importKeyword, SyntaxToken moduleName) {
        _importKeyword = importKeyword;
        _moduleName = moduleName;
    }

    public SyntaxToken getModuleName() { return _moduleName; }

    @Override
    public SyntaxType getType() { return SyntaxType.ImportStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_importKeyword).iterator();
    }
}
