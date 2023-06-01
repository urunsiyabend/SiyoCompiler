package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents an if statement in the syntax tree.
 * It encapsulates an if keyword, a condition, a then statement and an else clause.
 * If the else clause is null, then the if statement does not have an else clause.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class IfStatementSyntax extends StatementSyntax {
    private final SyntaxToken _ifKeyword;
    private final ExpressionSyntax _condition;
    private final StatementSyntax _thenStatement;
    private final ElseClauseSyntax _elseClause;

    /**
     * Creates a new instance of the IfStatementSyntax class with the specified if keyword, condition, then statement and else clause.
     *
     * @param ifKeyword     The if keyword.
     * @param condition     The condition.
     * @param thenStatement The then statement.
     * @param elseClause    The else clause.
     */
    public IfStatementSyntax(SyntaxToken ifKeyword, ExpressionSyntax condition, StatementSyntax thenStatement, ElseClauseSyntax elseClause) {
        _ifKeyword = ifKeyword;
        _condition = condition;
        _thenStatement = thenStatement;
        _elseClause = elseClause;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.IfStatement;
    }

    /**
     * Retrieves an iterator over the child nodes of the syntax node.
     *
     * @return An iterator over the child nodes.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    /**
     * Retrieves the if keyword token.
     *
     * @return The if keyword token.
     */
    public SyntaxToken getIfKeyword() {
        return _ifKeyword;
    }

    /**
     * Retrieves the condition expression.
     *
     * @return The condition expression.
     */
    public ExpressionSyntax getCondition() {
        return _condition;
    }

    /**
     * Retrieves the then statement.
     *
     * @return The then statement.
     */
    public StatementSyntax getThenStatement() {
        return _thenStatement;
    }

    /**
     * Retrieves the else clause.
     *
     * @return The else clause.
     */
    public ElseClauseSyntax getElseClause() {
        return _elseClause;
    }

    /**
     * The ChildrenIterator class represents an iterator over the child nodes of an if statement syntax.
     * It encapsulates an index to keep track of the current child node.
     *
     * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
     * @author Siyabend Urun
     * @version 1.0
     */
    private class ChildrenIterator implements Iterator<SyntaxNode> {

        private int index;

        /**
         * Checks if there are more child nodes to iterate over.
         * If the else clause is null, then there are 3 child nodes: if keyword, condition and then statement.
         * Otherwise, there are 4 child nodes: if keyword, condition, then statement and else clause.
         *
         * @return true if there are more child nodes; otherwise, false.
         */
        @Override
        public boolean hasNext() {
            if (_elseClause == null) {
                return index < 3;
            } else {
                return index < 4;
            }
        }

        /**
         * Retrieves the next child node.
         * If the else clause is null, then the child nodes are: if keyword, condition and then statement.
         * Otherwise, the child nodes are: if keyword, condition, then statement and else clause.
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
                    return _ifKeyword;
                }
                case 1 -> {
                    index++;
                    return _condition;
                }
                case 2 -> {
                    index++;
                    return _thenStatement;
                }
                case 3 -> {
                    if (_elseClause != null) {
                        index++;
                        return _elseClause;
                    } else {
                        index++;
                        return next();
                    }
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
