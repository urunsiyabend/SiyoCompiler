package codeanalysis.lowering;

import codeanalysis.LabelSymbol;
import codeanalysis.binding.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

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
    private int _labelCounter = 0;

    private Lowerer() {}

    private LabelSymbol generateLabel() {
        String name = "Label" + _labelCounter;
        _labelCounter++;
        return new LabelSymbol(name);
    }

    /**
     * Lowers the given bound statement.
     * This method is a static method.
     * This method is used to lower the given bound statement.
     *
     * @param statement The bound statement to be lowered.
     * @return The lowered bound statement.
     */
    public static BoundBlockStatement lower(BoundStatement statement) {
        Lowerer lowerer = new Lowerer();
        BoundStatement result = lowerer.rewriteStatement(statement);
        return flatten(result);
    }

    /**
     * Flattens the given bound statement by removing the nested bound block statements.
     *
     * @param statement The bound statement to be flattened.
     * @return The flattened bound statement.
     */
    private static BoundBlockStatement flatten(BoundStatement statement) {
        ArrayList<BoundStatement> builder = new ArrayList<>();
        Stack<BoundStatement> stack = new Stack<>();
        stack.push(statement);

        while (!stack.isEmpty()) {
            var current = stack.pop();

            if (current instanceof BoundBlockStatement block) {
                ArrayList<BoundStatement> reversed = new ArrayList<>(block.getStatements());
                Collections.reverse(reversed);
                for (var s : reversed) {
                    stack.push(s);
                }
            }
            else {
                builder.add(current);
            }
        }

        return new BoundBlockStatement(builder);
    }

    /**
     * Rewrites the given bound for statement.
     * Lowers the given bound for statement into a bound while statement.
     *
     * @param node The for statement to rewrite.
     * @return The rewritten bound for statement.
     */
    @Override
    protected BoundStatement rewriteForStatement(BoundForStatement node) {
        BoundStatement variableDeclaration = node.getInitializer();
        BoundExpression condition = node.getCondition();
        BoundExpression iterator = node.getIterator();
        ArrayList<BoundStatement> whileBodyStatements = new ArrayList<>();
        whileBodyStatements.add(node.getBody());
        whileBodyStatements.add(new BoundExpressionStatement(iterator));
        BoundBlockStatement whileBody = new BoundBlockStatement(whileBodyStatements);
        BoundWhileStatement whileStatement = new BoundWhileStatement(condition, whileBody);
        ArrayList<BoundStatement> whileStatements = new ArrayList<>();
        whileStatements.add(variableDeclaration);
        whileStatements.add(whileStatement);
        BoundBlockStatement result = new BoundBlockStatement(whileStatements);
        return rewriteStatement(result);
    }

    /**
     * Rewrites the given bound do while statement.
     * Lowers the given bound do while statement into a bound goto statement.
     *
     * @param node The do while statement to rewrite.
     * @return The rewritten bound do while statement.
     */
    @Override
    protected BoundStatement rewriteWhileStatement(BoundWhileStatement node) {
        var continueLabel = generateLabel();
        var checkLabel = generateLabel();
        var endLabel = generateLabel();

        var gotoCheck = new BoundGotoStatement(checkLabel);
        var continueLabelStatement = new BoundLabelStatement(continueLabel);
        var checkLabelStatement = new BoundLabelStatement(checkLabel);
        var gotoTrue = new BoundConditionalGotoStatement(continueLabel, node.getCondition(), true);
        var endLabelStatement = new BoundLabelStatement(endLabel);

        ArrayList<BoundStatement> resultStatements = new ArrayList<>();

        resultStatements.add(gotoCheck);
        resultStatements.add(continueLabelStatement);
        resultStatements.add(node.getBody());
        resultStatements.add(checkLabelStatement);
        resultStatements.add(gotoTrue);
        resultStatements.add(endLabelStatement);

        BoundBlockStatement result = new BoundBlockStatement(resultStatements);
        return rewriteStatement(result);
    }

    /**
     * Rewrites the given bound if statement.
     * Lowers the given bound if statement into a bound conditional goto statement.
     *
     * @param node The if statement to rewrite.
     * @return The rewritten bound if statement.
     */
    @Override
    protected BoundStatement rewriteIfStatement(BoundIfStatement node) {
        if (node.getElseStatement() == null) {
            LabelSymbol endLabel = generateLabel();
            BoundConditionalGotoStatement gotoFalse = new BoundConditionalGotoStatement(endLabel, node.getCondition(), false);
            BoundLabelStatement endLabelStatement = new BoundLabelStatement(endLabel);
            ArrayList<BoundStatement> resultStatements = new ArrayList<>();

            resultStatements.add(gotoFalse);
            resultStatements.add(node.getThenStatement());
            resultStatements.add(endLabelStatement);

            BoundBlockStatement result = new BoundBlockStatement(resultStatements);
            return rewriteStatement(result);
        }
        else {
            LabelSymbol elseLabel = generateLabel();
            LabelSymbol endLabel = generateLabel();

            BoundConditionalGotoStatement gotoFalse = new BoundConditionalGotoStatement(elseLabel, node.getCondition(), false);
            BoundGotoStatement gotoEndStatement = new BoundGotoStatement(endLabel);
            BoundLabelStatement elseLabelStatement = new BoundLabelStatement(elseLabel);
            BoundLabelStatement endLabelStatement = new BoundLabelStatement(endLabel);
            ArrayList<BoundStatement> resultStatements = new ArrayList<>();

            resultStatements.add(gotoFalse);
            resultStatements.add(node.getThenStatement());
            resultStatements.add(gotoEndStatement);
            resultStatements.add(elseLabelStatement);
            resultStatements.add(node.getElseStatement());
            resultStatements.add(endLabelStatement);

            BoundBlockStatement result = new BoundBlockStatement(resultStatements);
            return rewriteStatement(result);
        }
    }
}