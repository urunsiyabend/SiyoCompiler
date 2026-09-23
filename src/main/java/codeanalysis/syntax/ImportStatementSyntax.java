package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * An import of a Siyo module: {@code import "std/math"}, optionally under a
 * name of the importer's choosing: {@code import "std/math" as m}.
 */
public class ImportStatementSyntax extends StatementSyntax {
    private final SyntaxToken _importKeyword;
    private final SyntaxToken _moduleName;
    private final SyntaxToken _alias;

    public ImportStatementSyntax(SyntaxToken importKeyword, SyntaxToken moduleName) {
        this(importKeyword, moduleName, null);
    }

    public ImportStatementSyntax(SyntaxToken importKeyword, SyntaxToken moduleName, SyntaxToken alias) {
        _importKeyword = importKeyword;
        _moduleName = moduleName;
        _alias = alias;
    }

    public SyntaxToken getModuleName() { return _moduleName; }

    /** The name the module's members are qualified with, or null for its own. */
    public SyntaxToken getAlias() { return _alias; }

    @Override
    public SyntaxType getType() { return SyntaxType.ImportStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_importKeyword).iterator();
    }
}
