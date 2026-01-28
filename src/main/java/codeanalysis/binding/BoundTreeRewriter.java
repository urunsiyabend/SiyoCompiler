package codeanalysis.binding;

import java.util.ArrayList;

/**
 * Represents a bound tree rewriter in the code analysis process.
 * Bound tree rewrites are used to rewrite the bound tree in the code analysis process.
 * It lowers the bound tree to a lower level.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class BoundTreeRewriter {
    /**
     * Rewrites a statement in the bound tree.
     * This method determines the type of the bound node and calls the appropriate method to rewrite the bound node.
     *
     * @param node The bound node to rewrite.
     * @return The rewritten bound node.
     */
    public BoundStatement rewriteStatement(BoundStatement node) {
        return switch (node.getType()) {
            case BlockStatement -> rewriteBlockStatement((BoundBlockStatement) node);
            case VariableDeclaration -> rewriteVariableDeclaration((BoundVariableDeclaration) node);
            case IfStatement -> rewriteIfStatement((BoundIfStatement) node);
            case WhileStatement -> rewriteWhileStatement((BoundWhileStatement) node);
            case ForStatement -> rewriteForStatement((BoundForStatement) node);
            case LabelStatement -> rewriteLabelStatement((BoundLabelStatement) node);
            case GotoStatement -> rewriteGotoStatement((BoundGotoStatement) node);
            case ConditionalGotoStatement -> rewriteConditionalGotoStatement((BoundConditionalGotoStatement) node);
            case ExpressionStatement -> rewriteExpressionStatement((BoundExpressionStatement) node);
            case ReturnStatement -> rewriteReturnStatement((BoundReturnStatement) node);
            default -> throw new IllegalStateException("Unhandled bound statement type: " + node.getType() + ". This is a compiler bug.");
        };
    }

    /**
     * Rewrites a block statement in the bound tree.
     * When a block statement is rewritten, it rewrites all the statements in the block statement.
     * If the block statement does not contain any statements to rewrite, it returns the block statement itself.
     * By this way, it prevents the creation of unnecessary block statements and memory leaks.
     *
     * @param node The block statement to rewrite.
     * @return The rewritten block statement.
     */
    private BoundStatement rewriteBlockStatement(BoundBlockStatement node) {
        ArrayList<BoundStatement> statements = null;
        for (int i = 0; i < node.getStatements().size(); i++) {
            BoundStatement statement = node.getStatements().get(i);
            BoundStatement rewrittenStatement = rewriteStatement(statement);
            if (rewrittenStatement != statement) {
                if (statements == null) {
                    statements = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        statements.add(node.getStatements().get(j));
                    }
                }
            }

            if (statements != null) {
                statements.add(rewrittenStatement);
            }
        }

        if (statements == null) {
            return node;
        }

        return new BoundBlockStatement(statements);
    }


    /**
     * Rewrites a variable declaration statement in the bound tree.
     * When a variable declaration statement is rewritten, it rewrites the initializer of the variable declaration statement.
     * If the initializer of the variable declaration statement does not change, it returns the variable declaration statement itself.
     *
     * @param node The variable declaration statement to rewrite.
     * @return The rewritten variable declaration statement.
     */
    private BoundStatement rewriteVariableDeclaration(BoundVariableDeclaration node) {
        BoundExpression initializer = rewriteExpression(node.getInitializer());
        if (initializer == node.getInitializer()) {
            return node;
        }
        return new BoundVariableDeclaration(node.getVariable(), initializer);
    }

    /**
     * Rewrites an if statement in the bound tree.
     * When an if statement is rewritten, it rewrites the condition, then statement and else statement of the if statement.
     * If the condition, then statement and else statement of the if statement does not change, it returns the if statement itself.
     *
     * @param node The if statement to rewrite.
     * @return The rewritten if statement.
     */
    protected BoundStatement rewriteIfStatement(BoundIfStatement node) {
        BoundExpression condition = rewriteExpression(node.getCondition());
        BoundStatement thenStatement = rewriteStatement(node.getThenStatement());
        BoundStatement elseStatement = node.getElseStatement() == null ? null : rewriteStatement(node.getElseStatement());
        if (condition == node.getCondition() && thenStatement == node.getThenStatement() && elseStatement == node.getElseStatement()) {
            return node;
        }
        return new BoundIfStatement(condition, thenStatement, elseStatement);
    }

    /**
     * Rewrites a while statement in the bound tree.
     * When a while statement is rewritten, it rewrites the condition and body of the while statement.
     * If the condition and body of the while statement does not change, it returns the while statement itself.
     *
     * @param node The while statement to rewrite.
     * @return The rewritten while statement.
     */
    protected BoundStatement rewriteWhileStatement(BoundWhileStatement node) {
        BoundExpression condition = rewriteExpression(node.getCondition());
        BoundStatement body = rewriteStatement(node.getBody());
        if (condition == node.getCondition() && body == node.getBody()) {
            return node;
        }
        return new BoundWhileStatement(condition, body);
    }

    /**
     * Rewrites a for statement in the bound tree.
     * When a for statement is rewritten, it rewrites the initializer, condition, iterator and body of the for statement.
     * If the initializer, condition, iterator and body of the for statement does not change, it returns the for statement itself.
     *
     * @param node The for statement to rewrite.
     * @return The rewritten for statement.
     */
    protected BoundStatement rewriteForStatement(BoundForStatement node) {
        BoundStatement initializer = rewriteStatement(node.getInitializer());
        BoundExpression condition = rewriteExpression(node.getCondition());
        BoundExpression iterator = rewriteExpression(node.getIterator());
        BoundStatement body = rewriteStatement(node.getBody());
        if (initializer == node.getInitializer() && condition == node.getCondition() && iterator == node.getIterator() && body == node.getBody()) {
            return node;
        }
        return new BoundForStatement(initializer, condition, iterator, body);
    }

    /**
     * Rewrites a label statement in the bound tree.
     * When a label statement is rewritten, it rewrites the label of the label statement.
     * If the label of the label statement does not change, it returns the label statement itself.
     *
     * @param node The label statement to rewrite.
     * @return The rewritten label statement.
     */
    protected BoundStatement rewriteLabelStatement(BoundLabelStatement node) {
        return node;
    }

    /**
     * Rewrites a goto statement in the bound tree.
     * When a goto statement is rewritten, it rewrites the label of the goto statement.
     * If the label of the goto statement does not change, it returns the goto statement itself.
     *
     * @param node The goto statement to rewrite.
     * @return The rewritten goto statement.
     */
    protected BoundStatement rewriteGotoStatement(BoundGotoStatement node) {
        return node;
    }

    /**
     * Rewrites a conditional goto statement in the bound tree.
     * When a conditional goto statement is rewritten, it rewrites the condition of the conditional goto statement.
     * If the condition of the conditional goto statement does not change, it returns the conditional goto statement itself.
     *
     * @param node The conditional goto statement to rewrite.
     * @return The rewritten conditional goto statement.
     */
    protected BoundStatement rewriteConditionalGotoStatement(BoundConditionalGotoStatement node) {
        BoundExpression condition = rewriteExpression(node.getCondition());
        if (condition == node.getCondition()) {
            return node;
        }
        return new BoundConditionalGotoStatement(node.getLabel(), condition, node.getJumpIfTrue());
    }

    /**
     * Rewrites an expression statement in the bound tree.
     * When an expression statement is rewritten, it rewrites the expression of the expression statement.
     * If the expression of the expression statement does not change, it returns the expression statement itself.
     *
     * @param node The expression statement to rewrite.
     * @return The rewritten expression statement.
     */
    protected BoundStatement rewriteExpressionStatement(BoundExpressionStatement node) {
        var expression = rewriteExpression(node.getExpression());
        if (expression == node.getExpression()) {
            return node;
        }
        return new BoundExpressionStatement(expression);
    }

    /**
     * Rewrites an expression in the bound tree.
     * This method determines the type of the bound node and calls the appropriate method to rewrite the bound node.
     *
     * @param node The bound node to rewrite.
     * @return The rewritten bound node.
     */
    public BoundExpression rewriteExpression(BoundExpression node) {
        return switch (node.getType()) {
            case AssignmentExpression -> rewriteAssignmentExpression((BoundAssignmentExpression) node);
            case BinaryExpression -> rewriteBinaryExpression((BoundBinaryExpression) node);
            case UnaryExpression -> rewriteUnaryExpression((BoundUnaryExpression) node);
            case LiteralExpression -> rewriteLiteralExpression((BoundLiteralExpression) node);
            case VariableExpression -> rewriteVariableExpression((BoundVariableExpression) node);
            case CallExpression -> rewriteCallExpression((BoundCallExpression) node);
            default -> throw new IllegalStateException("Unhandled bound expression type: " + node.getType() + ". This is a compiler bug.");
        };
    }

    /**
     * Rewrites an assignment expression in the bound tree.
     * When an assignment expression is rewritten, it rewrites the expression of the assignment expression.
     * If the expression of the assignment expression does not change, it returns the assignment expression itself.
     *
     * @param node The assignment expression to rewrite.
     * @return The rewritten assignment expression.
     */
    protected BoundExpression rewriteAssignmentExpression(BoundAssignmentExpression node) {
        var expression = rewriteExpression(node.getExpression());
        if (expression == node.getExpression()) {
            return node;
        }
        return new BoundAssignmentExpression(node.getVariable(), expression);
    }

    /**
     * Rewrites a binary expression in the bound tree.
     * When a binary expression is rewritten, it rewrites the left and right expression of the binary expression.
     * If the left and right expression of the binary expression does not change, it returns the binary expression itself.
     *
     * @param node The binary expression to rewrite.
     * @return The rewritten binary expression.
     */
    protected BoundExpression rewriteBinaryExpression(BoundBinaryExpression node) {
        var left = rewriteExpression(node.getLeft());
        var right = rewriteExpression(node.getRight());
        if (left == node.getLeft() && right == node.getRight()) {
            return node;
        }
        return new BoundBinaryExpression(left, node.getOperator(), right);
    }

    /**
     * Rewrites a unary expression in the bound tree.
     * When a unary expression is rewritten, it rewrites the operand of the unary expression.
     * If the operand of the unary expression does not change, it returns the unary expression itself.
     *
     * @param node The unary expression to rewrite.
     * @return The rewritten unary expression.
     */
    protected BoundExpression rewriteUnaryExpression(BoundUnaryExpression node) {
        var operand = rewriteExpression(node.getOperand());
        if (operand == node.getOperand()) {
            return node;
        }
        return new BoundUnaryExpression(node.getOperator(), operand);
    }

    /**
     * Rewrites a literal expression in the bound tree.
     * If the literal expression does not change, it returns the literal expression itself.
     *
     * @param node The literal expression to rewrite.
     * @return The rewritten literal expression.
     */
    protected BoundExpression rewriteLiteralExpression(BoundLiteralExpression node) {
        return node;
    }

    /**
     * Rewrites a variable expression in the bound tree.
     * If the variable expression does not change, it returns the variable expression itself.
     *
     * @param node The variable expression to rewrite.
     * @return The rewritten variable expression.
     */
    protected BoundExpression rewriteVariableExpression(BoundVariableExpression node) {
        return node;
    }

    /**
     * Rewrites a return statement in the bound tree.
     * When a return statement is rewritten, it rewrites the expression of the return statement.
     * If the expression of the return statement does not change, it returns the return statement itself.
     *
     * @param node The return statement to rewrite.
     * @return The rewritten return statement.
     */
    protected BoundStatement rewriteReturnStatement(BoundReturnStatement node) {
        var expression = node.getExpression() == null ? null : rewriteExpression(node.getExpression());
        if (expression == node.getExpression()) {
            return node;
        }
        return new BoundReturnStatement(expression);
    }

    /**
     * Rewrites a call expression in the bound tree.
     * When a call expression is rewritten, it rewrites the arguments of the call expression.
     * If the arguments of the call expression do not change, it returns the call expression itself.
     *
     * @param node The call expression to rewrite.
     * @return The rewritten call expression.
     */
    protected BoundExpression rewriteCallExpression(BoundCallExpression node) {
        ArrayList<BoundExpression> arguments = null;

        for (int i = 0; i < node.getArguments().size(); i++) {
            BoundExpression oldArgument = node.getArguments().get(i);
            BoundExpression newArgument = rewriteExpression(oldArgument);
            if (newArgument != oldArgument) {
                if (arguments == null) {
                    arguments = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        arguments.add(node.getArguments().get(j));
                    }
                }
            }

            if (arguments != null) {
                arguments.add(newArgument);
            }
        }

        if (arguments == null) {
            return node;
        }

        return new BoundCallExpression(node.getFunction(), arguments);
    }
}
