package codeanalysis.binding;

import java.util.ArrayList;

/**
 * Class for representing bound block statements in the code analysis process.
 * A bound block statement is a statement that contains a list of statements.
 * It is used to group statements together.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundBlockStatement extends BoundStatement {
    private final ArrayList<BoundStatement> _statements;

    /**
     * Constructs a BoundBlockStatement object with the specified statements.
     * The statements are the statements to be contained in the block statement.
     *
     * @param statements The statements to be contained in the block statement.
     */
    public BoundBlockStatement(ArrayList<BoundStatement> statements) {
        _statements = statements;
    }

    /**
     * Gets the statements contained in the block statement.
     *
     * @return The statements contained in the block statement.
     */
    public ArrayList<BoundStatement> getStatements() {
        return _statements;
    }

    /**
     * Gets the type of the bound block statement.
     *
     * @return The class type of the bound block statement.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.BlockStatement;
    }
}
