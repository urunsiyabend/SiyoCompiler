package codeanalysis;

import codeanalysis.binding.*;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code Evaluator} class is responsible for evaluating an expression syntax tree and computing the result.
 * It provides a method to evaluate the root expression syntax and return the computed result.
 * The evaluator supports evaluating different types of expressions, including numbers, binary expressions
 * (addition, subtraction, multiplication, and division), and parenthesized expressions.
 * Note: The evaluator throws an exception if it encounters an unexpected node or operator during evaluation.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Evaluator {
    private final BoundBlockStatement _root;
    private final Map<VariableSymbol, Object> _variables;
    private Object _lastValue;

    /**
     * Evaluates the expression syntax tree and computes the result.
     * The evaluator supports evaluating different types of expressions, including numbers, binary expressions
     * (addition, subtraction, multiplication, and division), and parenthesized expressions.
     * Note: The evaluator throws an exception if it encounters an unexpected node or operator during evaluation.
     *
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation.
     */
    public Object evaluate() throws Exception {
        HashMap<LabelSymbol, Integer> labelToIndex = new HashMap<>();

        for (int i = 0; i < _root.getStatements().size(); i++) {
            if (_root.getStatements().get(i) instanceof BoundLabelStatement l) {
                labelToIndex.put(l.getLabel(), i + 1);
            }
        }

        int index = 0;
        while (index < _root.getStatements().size()) {
            BoundStatement s = _root.getStatements().get(index);
            switch (s.getType()) {
                case VariableDeclaration -> {
                    evaluateVariableDeclaration((BoundVariableDeclaration) s);
                    index++;
                }
                case ExpressionStatement -> {
                    evaluateExpressionStatement((BoundExpressionStatement) s);
                    index++;
                }
                case GotoStatement -> {
                    BoundGotoStatement gs = (BoundGotoStatement) s;
                    index = labelToIndex.get(gs.getLabel());
                }
                case ConditionalGotoStatement -> {
                    BoundConditionalGotoStatement cgs = (BoundConditionalGotoStatement) s;
                    boolean condition = (boolean) evaluateExpression(cgs.getCondition());
                    if (condition == cgs.getJumpIfTrue()) {
                        index = labelToIndex.get(cgs.getLabel());
                    }
                    else {
                        index++;
                    }
                }
                case LabelStatement -> {
                    index++;
                }
                default -> throw new Exception("Unexpected node: " + s.getType());
            };
        }

        return _lastValue;
    }

    /**
     * Initializes a new instance of the {@code Evaluator} class with the specified root expression syntax.
     *
     * @param root      The root expression syntax to evaluate.
     * @param variables The variables of the evaluator.
     */
    public Evaluator(BoundBlockStatement root, Map<VariableSymbol, Object> variables) {
        _root = root;
        _variables = variables;
    }

    /**
     * Evaluates the specified expression statement syntax node and computes the result.
     *
     * @param statement The bound statement node to evaluate.
     * @throws Exception if an error occurs during evaluation or if an unexpected node is encountered.
     */
    private void evaluateExpressionStatement(BoundExpressionStatement statement) throws Exception {
        _lastValue = evaluateExpression(statement.getExpression());
    }

    /**
     * Evaluates the specified variable declaration syntax node and computes the result.
     *
     * @param node The bound expression node to evaluate.
     * @throws Exception if an error occurs during evaluation or if an unexpected node is encountered.
     */
    private void evaluateVariableDeclaration(BoundVariableDeclaration node) throws Exception {
        Object value = evaluateExpression(node.getInitializer());
        _variables.put(node.getVariable(), value);
        _lastValue = value;
    }

    /**
     * Recursively evaluates the specified expression syntax node and computes the result.
     *
     * @param node The expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation or if an unexpected node is encountered.
     */
    private Object evaluateExpression(BoundExpression node) throws Exception {
        return switch (node.getType()) {
            case LiteralExpression -> evaluateLiteralExpression((BoundLiteralExpression) node);
            case VariableExpression -> evaluateVariableExpression((BoundVariableExpression) node);
            case AssignmentExpression -> evaluateAssignmentExpression((BoundAssignmentExpression) node);
            case UnaryExpression -> evaluateUnaryExpression((BoundUnaryExpression) node);
            case BinaryExpression -> evaluateBinaryExpression((BoundBinaryExpression) node);
            default -> throw new Exception("Unexpected node: " + node.getType());
        };
    }

    /**
     * Evaluates the specified literal expression syntax node and computes the result.
     *
     * @param n The literal expression syntax node to evaluate.
     * @return The computed result of the expression.
     */
    private static Object evaluateLiteralExpression(BoundLiteralExpression n) {
        return n.getValue();
    }

    /**
     * Evaluates the specified variable expression syntax node and computes the result.
     *
     * @param v The variable expression syntax node to evaluate.
     * @return The computed result of the expression.
     */
    private Object evaluateVariableExpression(BoundVariableExpression v) {
        return _variables.get(v.getVariable());
    }

    /**
     * Evaluates the specified assignment expression syntax node and computes the result.
     *
     * @param a The assignment expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation.
     */
    private Object evaluateAssignmentExpression(BoundAssignmentExpression a) throws Exception {
        Object value = evaluateExpression(a.getExpression());
        _variables.put(a.getVariable(), value);
        return value;
    }

    /**
     * @param u The unary expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation or if an unexpected operator is encountered.
     */
    private Object evaluateUnaryExpression(BoundUnaryExpression u) throws Exception {
        Object operand = evaluateExpression(u.getOperand());
        return switch (u.getOperator().getType()) {
            case Identity -> (int) operand;
            case Negation -> -(int) operand;
            case LogicalNegation -> !(boolean) operand;
            case OnesComplement -> ~(int) operand;
            default -> throw new Exception(String.format("Unexpected unary operator: %s", u.getOperator().getType()));
        };
    }

    /**
     * Evaluates the specified binary expression syntax node and computes the result.
     *
     * @param b The binary expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation or if an unexpected operator is encountered.
     */
    private Object evaluateBinaryExpression(BoundBinaryExpression b) throws Exception {
        Object left = evaluateExpression(b.getLeft());
        Object right = evaluateExpression(b.getRight());

        return switch (b.getOperator().getType()) {
            case Addition -> (int) left + (int) right;
            case Subtraction -> (int) left - (int) right;
            case Multiplication -> (int) left * (int) right;
            case Division -> (int) left / (int) right;
            case Modulo -> (int) left % (int) right;
            case BitwiseAnd -> b.getClassType() == Boolean.class ? (boolean) left & (boolean) right : (int) left & (int) right;
            case BitwiseOr -> b.getClassType() == Boolean.class ? (boolean) left | (boolean) right : (int) left | (int) right;
            case BitwiseXor -> b.getClassType() == Boolean.class ? (boolean) left ^ (boolean) right : (int) left ^ (int) right;
            case LeftShift -> (int) left << (int) right;
            case RightShift -> (int) left >> (int) right;
            case LogicalAnd -> (boolean) left && (boolean) right;
            case LogicalOr -> (boolean) left || (boolean) right;
            case Equals -> left.equals(right);
            case NotEquals -> !left.equals(right);
            case LessThan -> (int) left < (int) right;
            case LessOrEqualsThan -> (int) left <= (int) right;
            case GreaterThan -> (int) left > (int) right;
            case GreaterOrEqualsThen -> (int) left >= (int) right;
            default -> throw new Exception(String.format("Unexpected binary operator: %s", b.getOperator().getType()));
        };
    }
}

