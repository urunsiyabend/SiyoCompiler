package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an expression statement in the syntax tree.
 * This class defines an expression statement, which consists of an expression.
 * It is used to represent statements like "1 + 2" or "a = b = 3".
 * <p>
 * This class inherits from the StatementSyntax class and provides additional properties and methods
 * specific to expression statements.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ExpressionStatementSyntax extends StatementSyntax {
    private final ExpressionSyntax _expression;

    /**
     * Initializes a new instance of the ExpressionStatementSyntax class.
     *
     * @param expressionSyntax The expression.
     */
    public ExpressionStatementSyntax(ExpressionSyntax expressionSyntax) {
        _expression = expressionSyntax;
    }

    /**
     * Gets the expression.
     *
     * @return The expression.
     */
    public ExpressionSyntax getExpression() {
        return _expression;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The type of the syntax node.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.ExpressionStatement;
    }

    /**
     * Gets the children of the syntax node.
     * This method is used to implement the Iterable interface.
     *
     * @return The children of the syntax node.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> childNodes = new ArrayList<>();
        childNodes.add(_expression);
        return childNodes.iterator();
    }
}
