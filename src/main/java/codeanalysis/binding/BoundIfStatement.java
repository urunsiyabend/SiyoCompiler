package codeanalysis.binding;

/**
 * Represents a bound if statement in the code analysis process.
 * Bound if statements are used to represent if statements in the code analysis process.
 * Bound if statements have a condition, a statement to be executed if the condition is true and a statement to be executed if the condition is false.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundIfStatement extends BoundStatement {
    private final BoundExpression _condition;
    private final BoundStatement _thenStatement;
    private final BoundStatement _elseStatement;

    /**
     * Constructs a new instance of the BoundIfStatement class.
     *
     * @param condition     The condition of the if statement.
     * @param thenStatement The statement to be executed if the condition is true.
     * @param elseStatement The statement to be executed if the condition is false.
     */
    public BoundIfStatement(BoundExpression condition, BoundStatement thenStatement, BoundStatement elseStatement) {
        _condition = condition;
        _thenStatement = thenStatement;
        _elseStatement = elseStatement;
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
     * Gets the statement to be executed if the condition is true.
     *
     * @return The statement to be executed if the condition is true.
     */
    public BoundStatement getThenStatement() {
        return _thenStatement;
    }

    /**
     * Gets the statement to be executed if the condition is false.
     *
     * @return The statement to be executed if the condition is false.
     */
    public BoundStatement getElseStatement() {
        return _elseStatement;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.IfStatement;
    }
}
