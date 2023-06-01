package codeanalysis.binding;

/**
 * Represents a bound while statement in the code analysis process.
 * Bound while statements are used to represent while statements in the code analysis process.
 * Bound while statements have a condition and a statement to be executed while the condition is true.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundWhileStatement extends BoundStatement {
    private final BoundExpression _condition;
    private final BoundStatement _body;

    /**
     * Constructs a new instance of the BoundWhileStatement class.
     *
     * @param condition The condition of the while statement.
     * @param body      The statement to be executed while the condition is true.
     */
    public BoundWhileStatement(BoundExpression condition, BoundStatement body) {
        _condition = condition;
        _body = body;
    }

    /**
     * Gets the condition expression of the bound node.
     *
     * @return The condition expression of the bound node.
     */
    public BoundExpression getCondition() {
        return _condition;
    }

    /**
     * Gets the statement to be executed while the condition is true.
     *
     * @return The statement to be executed while the condition is true.
     */
    public BoundStatement getBody() {
        return _body;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.WhileStatement;
    }
}
