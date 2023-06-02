package codeanalysis.lowering;

import codeanalysis.binding.BoundStatement;
import codeanalysis.binding.BoundTreeRewriter;

/**
 * The Lowerer class is responsible for lowering the bound tree.
 * Lowering the bound tree means transforming the bound tree into a tree that is easier to evaluate.
 * For example, the Lowerer class transforms the bound tree into a tree that does not contain any if statements.
 * This is done by the Lowerer class because the if statements are not supported by the interpreter.
 * The Lowerer class is a singleton class.
 * The Lowerer class is a subclass of the BoundTreeRewriter class.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Lowerer extends BoundTreeRewriter {
    private Lowerer() {}

    /**
     * Lowers the given bound statement.
     * This method is a static method.
     * This method is used to lower the given bound statement.
     *
     * @param statement The bound statement to be lowered.
     * @return The lowered bound statement.
     */
    public static BoundStatement lower(BoundStatement statement) {
        var lowerer = new Lowerer();
        return lowerer.rewriteStatement(statement);
    }
}
