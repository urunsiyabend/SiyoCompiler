package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a do-while statement in the syntax tree: {@code do { body } while cond}.
 *
 * <p>The body runs once before the condition is first checked, which is what a
 * plain while loop cannot express without repeating the body.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class DoWhileStatementSyntax extends StatementSyntax {
    private final SyntaxToken _doKeyword;
    private final StatementSyntax _body;
    private final SyntaxToken _whileKeyword;
    private final ExpressionSyntax _condition;

    /**
     * Initializes a new instance of the DoWhileStatementSyntax class.
     *
     * @param doKeyword    The do keyword token.
     * @param body         The body statement.
     * @param whileKeyword The while keyword token.
     * @param condition    The condition expression.
     */
    public DoWhileStatementSyntax(SyntaxToken doKeyword, StatementSyntax body,
                                  SyntaxToken whileKeyword, ExpressionSyntax condition) {
        _doKeyword = doKeyword;
        _body = body;
        _whileKeyword = whileKeyword;
        _condition = condition;
    }

    /**
     * Gets the body statement.
     *
     * @return The body of the loop.
     */
    public StatementSyntax getBody() {
        return _body;
    }

    /**
     * Gets the condition expression.
     *
     * @return The condition checked after each pass.
     */
    public ExpressionSyntax getCondition() {
        return _condition;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.DoWhileStatement;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<SyntaxNode> {
        private int index;

        @Override
        public boolean hasNext() {
            return index < 4;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return switch (index++) {
                case 0 -> _doKeyword;
                case 1 -> _body;
                case 2 -> _whileKeyword;
                case 3 -> _condition;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
