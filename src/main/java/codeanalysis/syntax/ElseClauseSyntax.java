package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents an else clause in the syntax tree.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ElseClauseSyntax extends SyntaxNode {
    private final SyntaxToken _elseKeyword;
    private final StatementSyntax _elseStatement;

    public ElseClauseSyntax(SyntaxToken elseKeyword, StatementSyntax elseStatement) {
        this._elseKeyword = elseKeyword;
        this._elseStatement = elseStatement;
    }

    public SyntaxToken getElseKeyword() {
        return _elseKeyword;
    }

    public StatementSyntax getElseStatement() {
        return _elseStatement;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.ElseClause;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<SyntaxNode> {

        private int index;

        /**
         * Checks if there are more child nodes to iterate over.
         *
         * @return true if there are more child nodes; otherwise, false.
         */
        @Override
        public boolean hasNext() {
            return index < 2;
        }

        /**
         * Retrieves the next child node.
         *
         * @return The next child node.
         * @throws NoSuchElementException if there are no more child nodes to iterate over.
         */
        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _elseKeyword;
                }
                case 1 -> {
                    index++;
                    return _elseStatement;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
