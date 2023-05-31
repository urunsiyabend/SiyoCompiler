package codeanalysis.binding;

/**
 * Class for representing bound expression statements in the code analysis process.
 * A bound expression statement is a statement that contains an expression.
 * It is used to evaluate an expression.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundExpressionStatement extends BoundStatement {
    private final BoundExpression _expression;

    /**
     * Constructs a BoundExpressionStatement object with the specified expression.
     * The expression is the expression to be contained in the expression statement.
     *
     * @param expression The expression to be contained in the expression statement.
     */
    public BoundExpressionStatement(BoundExpression expression) {
        _expression = expression;
    }

    /**
     * Gets the expression contained in the expression statement.
     *
     * @return The expression contained in the expression statement.
     */
    public BoundExpression getExpression() {
        return _expression;
    }

    /**
     * Gets the type of the bound expression statement.
     *
     * @return The class type of the bound expression statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ExpressionStatement;
    }
}
