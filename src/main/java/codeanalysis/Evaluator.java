package codeanalysis;

import codeanalysis.binding.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

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
    private final Map<VariableSymbol, Object> _globals;
    private final Map<FunctionSymbol, BoundBlockStatement> _functions;
    private final Stack<StackFrame> _callStack = new Stack<>();
    private Object _lastValue;
    private boolean _returnTriggered = false;
    private Object _returnValue = null;

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
        return evaluateBlock(_root);
    }

    /**
     * Evaluates a block of statements and returns the last value.
     *
     * @param block The block to evaluate.
     * @return The computed result.
     * @throws Exception if an error occurs during evaluation.
     */
    private Object evaluateBlock(BoundBlockStatement block) throws Exception {
        HashMap<LabelSymbol, Integer> labelToIndex = new HashMap<>();

        for (int i = 0; i < block.getStatements().size(); i++) {
            if (block.getStatements().get(i) instanceof BoundLabelStatement l) {
                labelToIndex.put(l.getLabel(), i + 1);
            }
        }

        int index = 0;
        while (index < block.getStatements().size()) {
            if (_returnTriggered) {
                break;
            }

            BoundStatement s = block.getStatements().get(index);
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
                case ReturnStatement -> {
                    BoundReturnStatement rs = (BoundReturnStatement) s;
                    _returnValue = rs.getExpression() == null ? null : evaluateExpression(rs.getExpression());
                    _returnTriggered = true;
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
     * @param variables The global variables of the evaluator.
     * @param functions The function bodies map.
     */
    public Evaluator(BoundBlockStatement root, Map<VariableSymbol, Object> variables, Map<FunctionSymbol, BoundBlockStatement> functions) {
        _root = root;
        _globals = variables;
        _functions = functions;
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
        assignVariable(node.getVariable(), value);
        _lastValue = value;
    }

    /**
     * Assigns a value to a variable, checking stack frame locals first.
     *
     * @param variable The variable symbol.
     * @param value    The value to assign.
     */
    private void assignVariable(VariableSymbol variable, Object value) {
        if (!_callStack.isEmpty()) {
            StackFrame frame = _callStack.peek();
            // Check if it's a local variable (including parameters)
            if (frame.getLocals().containsKey(variable) || isParameter(variable, frame.getFunction())) {
                frame.getLocals().put(variable, value);
                return;
            }
        }
        _globals.put(variable, value);
    }

    /**
     * Checks if a variable is a parameter of the current function.
     *
     * @param variable The variable symbol.
     * @param function The function symbol.
     * @return True if the variable is a parameter.
     */
    private boolean isParameter(VariableSymbol variable, FunctionSymbol function) {
        if (function == null) return false;
        for (ParameterSymbol param : function.getParameters()) {
            if (param.getName().equals(variable.getName())) {
                return true;
            }
        }
        return false;
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
            case CallExpression -> evaluateCallExpression((BoundCallExpression) node);
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
        return lookupVariable(v.getVariable());
    }

    /**
     * Looks up a variable value, checking stack frame locals first.
     *
     * @param variable The variable symbol.
     * @return The variable value.
     */
    private Object lookupVariable(VariableSymbol variable) {
        if (!_callStack.isEmpty()) {
            StackFrame frame = _callStack.peek();
            if (frame.getLocals().containsKey(variable)) {
                return frame.getLocals().get(variable);
            }
        }
        return _globals.get(variable);
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
        assignVariable(a.getVariable(), value);
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

    /**
     * Evaluates the specified call expression syntax node and computes the result.
     *
     * @param c The call expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation.
     */
    private Object evaluateCallExpression(BoundCallExpression c) throws Exception {
        FunctionSymbol function = c.getFunction();

        // Evaluate arguments
        Object[] arguments = new Object[c.getArguments().size()];
        for (int i = 0; i < c.getArguments().size(); i++) {
            arguments[i] = evaluateExpression(c.getArguments().get(i));
        }

        // Get the function body
        BoundBlockStatement body = _functions.get(function);
        if (body == null) {
            throw new Exception("Function body not found: " + function.getName());
        }

        // Create new stack frame
        StackFrame frame = new StackFrame(function);

        // Bind parameters to argument values
        for (int i = 0; i < function.getParameters().size(); i++) {
            ParameterSymbol param = function.getParameters().get(i);
            frame.getLocals().put(param, arguments[i]);
        }

        // Push frame and execute
        _callStack.push(frame);
        _returnTriggered = false;
        _returnValue = null;

        evaluateBlock(body);

        // Pop frame
        _callStack.pop();

        // Return the result
        Object result = _returnValue;
        _returnTriggered = false;
        _returnValue = null;

        return result;
    }
}

