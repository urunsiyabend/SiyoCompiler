package codeanalysis;

import codeanalysis.binding.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.List;

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
    private static final java.util.Scanner _stdinScanner = new java.util.Scanner(System.in);
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
                case TryCatchStatement -> {
                    evaluateTryCatchStatement((BoundTryCatchStatement) s);
                    index++;
                }
                case SendStatement -> {
                    evaluateSendStatement((codeanalysis.binding.BoundSendStatement) s);
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
            case StructLiteralExpression -> evaluateStructLiteralExpression((BoundStructLiteralExpression) node);
            case VariableExpression -> evaluateVariableExpression((BoundVariableExpression) node);
            case AssignmentExpression -> evaluateAssignmentExpression((BoundAssignmentExpression) node);
            case UnaryExpression -> evaluateUnaryExpression((BoundUnaryExpression) node);
            case BinaryExpression -> evaluateBinaryExpression((BoundBinaryExpression) node);
            case CallExpression -> evaluateCallExpression((BoundCallExpression) node);
            case ArrayLiteralExpression -> evaluateArrayLiteralExpression((BoundArrayLiteralExpression) node);
            case MapLiteralExpression -> evaluateMapLiteralExpression((codeanalysis.binding.BoundMapLiteralExpression) node);
            case IndexExpression -> evaluateIndexExpression((BoundIndexExpression) node);
            case MemberAccessExpression -> evaluateMemberAccessExpression((BoundMemberAccessExpression) node);
            case JavaMethodCallExpression -> evaluateJavaMethodCall((BoundJavaMethodCallExpression) node);
            case JavaStaticFieldExpression -> evaluateJavaStaticField((BoundJavaStaticFieldExpression) node);
            case CastExpression -> evaluateExpression(((BoundCastExpression) node).getExpression());
            case LambdaExpression -> evaluateLambdaExpression((BoundLambdaExpression) node);
            case ClosureCallExpression -> evaluateClosureCall((BoundClosureCallExpression) node);
            case ScopeExpression -> evaluateScopeExpression((BoundScopeExpression) node);
            case SpawnExpression -> evaluateSpawnExpression((BoundSpawnExpression) node);
            case MatchExpression -> evaluateMatchExpression((codeanalysis.binding.BoundMatchExpression) node);
            case TryExpression -> evaluateTryExpression((codeanalysis.binding.BoundTryExpression) node);
            case IndexAssignmentExpression -> evaluateIndexAssignment((BoundIndexAssignmentExpression) node);
            case MemberAssignmentExpression -> evaluateMemberAssignment((BoundMemberAssignmentExpression) node);
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
            case Identity -> operand instanceof Double d ? d : (int) operand;
            case Negation -> operand instanceof Double d ? -d : -(int) operand;
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
        var opType = b.getOperator().getType();
        if (opType == BoundBinaryOperatorType.LogicalAnd) {
            boolean left = (boolean) evaluateExpression(b.getLeft());
            return left && (boolean) evaluateExpression(b.getRight());
        }
        if (opType == BoundBinaryOperatorType.LogicalOr) {
            boolean left = (boolean) evaluateExpression(b.getLeft());
            return left || (boolean) evaluateExpression(b.getRight());
        }

        Object left = evaluateExpression(b.getLeft());
        Object right = evaluateExpression(b.getRight());

        boolean hasLong = left instanceof Long || right instanceof Long;
        return switch (b.getOperator().getType()) {
            case Addition -> {
                if (left instanceof String || right instanceof String)
                    yield String.valueOf(left) + String.valueOf(right);
                if (left instanceof Double l && right instanceof Double r) yield l + r;
                if (hasLong) yield toLong(left) + toLong(right);
                yield (int) left + (int) right;
            }
            case Subtraction -> {
                if (left instanceof Double l && right instanceof Double r) yield l - r;
                if (hasLong) yield toLong(left) - toLong(right);
                yield (int) left - (int) right;
            }
            case Multiplication -> {
                if (left instanceof Double l && right instanceof Double r) yield l * r;
                if (hasLong) yield toLong(left) * toLong(right);
                yield (int) left * (int) right;
            }
            case Division -> {
                if (left instanceof Double l && right instanceof Double r) yield l / r;
                if (hasLong) yield toLong(left) / toLong(right);
                yield (int) left / (int) right;
            }
            case Modulo -> {
                if (left instanceof Double l && right instanceof Double r) yield l % r;
                if (hasLong) yield toLong(left) % toLong(right);
                yield (int) left % (int) right;
            }
            case BitwiseAnd -> b.getClassType() == Boolean.class ? (boolean) left & (boolean) right : (int) left & (int) right;
            case BitwiseOr -> b.getClassType() == Boolean.class ? (boolean) left | (boolean) right : (int) left | (int) right;
            case BitwiseXor -> b.getClassType() == Boolean.class ? (boolean) left ^ (boolean) right : (int) left ^ (int) right;
            case LeftShift -> (int) left << (int) right;
            case RightShift -> (int) left >> (int) right;
            case LogicalAnd -> (boolean) left && (boolean) right;
            case LogicalOr -> (boolean) left || (boolean) right;
            case Equals -> java.util.Objects.equals(left, right);
            case NotEquals -> !java.util.Objects.equals(left, right);
            case LessThan -> {
                if (left instanceof String l && right instanceof String r) yield l.compareTo(r) < 0;
                if (left instanceof Double l && right instanceof Double r) yield l < r;
                if (hasLong) yield toLong(left) < toLong(right);
                yield (int) left < (int) right;
            }
            case LessOrEqualsThan -> {
                if (left instanceof String l && right instanceof String r) yield l.compareTo(r) <= 0;
                if (left instanceof Double l && right instanceof Double r) yield l <= r;
                if (hasLong) yield toLong(left) <= toLong(right);
                yield (int) left <= (int) right;
            }
            case GreaterThan -> {
                if (left instanceof String l && right instanceof String r) yield l.compareTo(r) > 0;
                if (left instanceof Double l && right instanceof Double r) yield l > r;
                if (hasLong) yield toLong(left) > toLong(right);
                yield (int) left > (int) right;
            }
            case GreaterOrEqualsThen -> {
                if (left instanceof String l && right instanceof String r) yield l.compareTo(r) >= 0;
                if (left instanceof Double l && right instanceof Double r) yield l >= r;
                if (hasLong) yield toLong(left) >= toLong(right);
                yield (int) left >= (int) right;
            }
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
        List<BoundExpression> args = c.getArguments();
        Object[] arguments = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            arguments[i] = evaluateExpression(args.get(i));
        }

        // Handle built-in functions
        if (BuiltinFunctions.isBuiltin(function)) {
            return evaluateBuiltinFunction(function, arguments);
        }

        // Actor method interception: if first arg is SiyoActor AND this is an instance method (first param is "self")
        if (arguments.length > 0 && arguments[0] instanceof SiyoActor actor
                && function.getName().contains(".")
                && function.getParameters().size() > 0
                && function.getParameters().get(0).getName().equals("self")) {
            String methodName = function.getName().substring(function.getName().indexOf('.') + 1);
            Object[] methodArgs = new Object[arguments.length - 1];
            System.arraycopy(arguments, 1, methodArgs, 0, methodArgs.length);
            return actor.call(methodName, methodArgs);
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

        // Return the result (explicit return or implicit last expression value)
        Object result = _returnTriggered ? _returnValue : _lastValue;
        _returnTriggered = false;
        _returnValue = null;

        return result;
    }

    private void evaluateTryCatchStatement(BoundTryCatchStatement node) throws Exception {
        try {
            BoundBlockStatement tryBlock = codeanalysis.lowering.Lowerer.lower(node.getTryBody());
            evaluateBlock(tryBlock);
        } catch (Exception e) {
            // Assign error message to the catch variable
            assignVariable(node.getErrorVariable(), e.getMessage() != null ? e.getMessage() : e.toString());
            BoundBlockStatement catchBlock = codeanalysis.lowering.Lowerer.lower(node.getCatchBody());
            evaluateBlock(catchBlock);
        }
    }

    private Object evaluateJavaMethodCall(BoundJavaMethodCallExpression node) throws Exception {
        // Evaluate arguments
        Object[] args = new Object[node.getArguments().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = evaluateExpression(node.getArguments().get(i));
        }

        if (node.isConstructor()) {
            // File.new("path") → new File("path")
            Class<?> cls = Class.forName(node.getClassInfo().getFullName(), true, JavaClasspath.getClassLoader());
            for (var ctor : cls.getConstructors()) {
                if (ctor.getParameterCount() == args.length) {
                    try {
                        return ctor.newInstance(args);
                    } catch (IllegalArgumentException e) {
                        continue; // try next constructor
                    }
                }
            }
            throw new Exception("No matching constructor for " + cls.getName() + " with " + args.length + " args");
        }

        if (node.isStatic()) {
            // Files.readString(path) → static method
            Class<?> cls = Class.forName(node.getClassInfo().getFullName(), true, JavaClasspath.getClassLoader());
            return invokeMethod(cls, null, node.getMethodName(), args);
        }

        // Instance method: obj.method(args)
        Object target = evaluateExpression(node.getTarget());
        if (target == null) throw new RuntimeException("Cannot call method on null");
        // Actor dispatch: check for direct SiyoActor methods first, then route through mailbox
        if (target instanceof SiyoActor actor) {
            // Direct methods on SiyoActor (stop, isStopped, etc.)
            for (var m : SiyoActor.class.getDeclaredMethods()) {
                if (m.getName().equals(node.getMethodName()) && m.getParameterCount() == args.length) {
                    return m.invoke(actor, args);
                }
            }
            return actor.call(node.getMethodName(), args);
        }
        return invokeMethod(target.getClass(), target, node.getMethodName(), args);
    }

    private Object invokeMethod(Class<?> cls, Object target, String methodName, Object[] args) throws Exception {
        // Search through class hierarchy and interfaces for accessible methods
        java.util.List<Class<?>> toSearch = new java.util.ArrayList<>();
        toSearch.add(cls);
        // Add all superclasses and interfaces
        Class<?> c = cls;
        while (c != null) {
            toSearch.add(c);
            for (Class<?> iface : c.getInterfaces()) toSearch.add(iface);
            c = c.getSuperclass();
        }

        for (Class<?> searchCls : toSearch) {
            for (var method : searchCls.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    try {
                        method.setAccessible(true);
                        // Convert SiyoArray back to Java arrays if method expects array parameter
                        Object[] convertedArgs = new Object[args.length];
                        Class<?>[] paramTypes = method.getParameterTypes();
                        for (int ai = 0; ai < args.length; ai++) {
                            if (args[ai] instanceof SiyoArray sa && paramTypes[ai].isArray()) {
                                convertedArgs[ai] = siyoArrayToJavaArray(sa, paramTypes[ai].getComponentType());
                            } else {
                                convertedArgs[ai] = args[ai];
                            }
                        }
                        Object result = method.invoke(target, convertedArgs);
                        if (result instanceof Long) return result; // preserve as Long
                        if (result instanceof Short s) return (int) s;
                        if (result instanceof Byte b) return (int) b;
                        if (result instanceof Float f) return f.doubleValue();
                        if (result instanceof Character c2) return String.valueOf(c2);
                        if (result instanceof Object[] arr) {
                            return new SiyoArray(java.util.Arrays.asList(arr), Object.class);
                        }
                        // Handle primitive arrays (byte[], int[], etc.)
                        if (result != null && result.getClass().isArray()) {
                            int len = java.lang.reflect.Array.getLength(result);
                            java.util.List<Object> elements = new java.util.ArrayList<>();
                            for (int idx = 0; idx < len; idx++) {
                                Object elem = java.lang.reflect.Array.get(result, idx);
                                if (elem instanceof Byte bv) elements.add((int) bv);
                                else if (elem instanceof Short sv) elements.add((int) sv);
                                else if (elem instanceof Float fv) elements.add((double) fv);
                                else if (elem instanceof Character cv) elements.add(String.valueOf(cv));
                                else elements.add(elem);
                            }
                            return new SiyoArray(elements, Integer.class);
                        }
                        return result;
                    } catch (IllegalArgumentException | java.lang.reflect.InaccessibleObjectException e) {
                        continue;
                    }
                }
            }
        }
        throw new Exception("No matching method: " + cls.getName() + "." + methodName + " with " + args.length + " args");
    }

    private Object evaluateClosureCall(BoundClosureCallExpression node) throws Exception {
        // Evaluate the closure expression (should be a SiyoClosure)
        Object closureObj = evaluateExpression(node.getClosure());
        if (!(closureObj instanceof SiyoClosure closure)) {
            throw new Exception("Cannot call non-function value");
        }

        // Evaluate arguments
        Object[] args = new Object[node.getArguments().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = evaluateExpression(node.getArguments().get(i));
        }

        // Create stack frame with captured variables + parameters
        StackFrame frame = new StackFrame(null);

        // Load captured variables
        for (var entry : closure.getCapturedVars().entrySet()) {
            frame.getLocals().put(entry.getKey(), entry.getValue());
        }

        // Bind parameters
        for (int i = 0; i < closure.getParameters().size(); i++) {
            frame.getLocals().put(closure.getParameters().get(i), args[i]);
        }

        // Execute
        _callStack.push(frame);
        evaluateBlock(closure.getBody());
        _callStack.pop();

        Object result = _returnTriggered ? _returnValue : _lastValue;
        _returnTriggered = false;
        _returnValue = null;
        return result;
    }

    private Object evaluateScopeExpression(BoundScopeExpression node) throws Exception {
        // Collect spawn tasks during body evaluation
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        java.util.List<Exception> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        _scopeThreads = threads;
        _scopeErrors = errors;

        evaluateBlock(node.getBody());

        // Wait for all spawned tasks to complete (structured concurrency)
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        _scopeThreads = null;
        _scopeErrors = null;

        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return null;
    }

    private java.util.List<Thread> _scopeThreads = null;
    private java.util.List<Exception> _scopeErrors = null;

    private void evaluateSendStatement(codeanalysis.binding.BoundSendStatement node) throws Exception {
        BoundExpression expr = node.getExpression();
        if (expr instanceof BoundCallExpression callExpr) {
            codeanalysis.FunctionSymbol function = callExpr.getFunction();
            Object[] arguments = new Object[callExpr.getArguments().size()];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = evaluateExpression(callExpr.getArguments().get(i));
            }
            // Actor send: first arg is SiyoActor
            if (arguments.length > 0 && arguments[0] instanceof SiyoActor actor
                    && function.getName().contains(".")
                    && function.getParameters().size() > 0
                    && function.getParameters().get(0).getName().equals("self")) {
                String methodName = function.getName().substring(function.getName().indexOf('.') + 1);
                Object[] methodArgs = new Object[arguments.length - 1];
                System.arraycopy(arguments, 1, methodArgs, 0, methodArgs.length);
                actor.send(methodName, methodArgs);
                return;
            }
        }
        // Fallback: evaluate as expression, discard result
        evaluateExpression(expr);
    }

    private Object evaluateTryExpression(codeanalysis.binding.BoundTryExpression node) throws Exception {
        try {
            BoundBlockStatement tryBlock = codeanalysis.lowering.Lowerer.lower(node.getTryBody());
            evaluateBlock(tryBlock);
            return _lastValue;
        } catch (Exception e) {
            assignVariable(node.getErrorVariable(), e.getMessage() != null ? e.getMessage() : e.toString());
            BoundBlockStatement catchBlock = codeanalysis.lowering.Lowerer.lower(node.getCatchBody());
            evaluateBlock(catchBlock);
            return _lastValue;
        }
    }

    private static long toLong(Object val) {
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return (long) i;
        return Long.parseLong(val.toString());
    }

    private Object evaluateMatchExpression(codeanalysis.binding.BoundMatchExpression node) throws Exception {
        Object target = evaluateExpression(node.getTarget());
        Object defaultResult = null;
        for (var arm : node.getArms()) {
            if (arm.isDefault()) {
                if (!arm.preStatements().isEmpty()) {
                    evaluateBlock(new BoundBlockStatement(new java.util.ArrayList<>(arm.preStatements())));
                }
                defaultResult = evaluateExpression(arm.body());
                continue;
            }
            Object pattern = evaluateExpression(arm.pattern());
            if (java.util.Objects.equals(target, pattern)) {
                if (!arm.preStatements().isEmpty()) {
                    evaluateBlock(new BoundBlockStatement(new java.util.ArrayList<>(arm.preStatements())));
                }
                return evaluateExpression(arm.body());
            }
        }
        return defaultResult;
    }

    private Object evaluateSpawnExpression(BoundSpawnExpression node) throws Exception {
        // Check if this is an actor spawn (single expression returning actor struct)
        if (node.getBody().getStatements().size() == 1
            && node.getBody().getStatements().get(0) instanceof BoundExpressionStatement exprStmt) {
            // Determine actor type from the expression
            String actorTypeName = null;
            if (exprStmt.getExpression() instanceof BoundCallExpression callExpr) {
                String funcName = callExpr.getFunction().getName();
                if (funcName.contains(".")) {
                    String typeName = funcName.substring(0, funcName.indexOf('.'));
                    if (_actorTypeNames.contains(typeName)) {
                        actorTypeName = typeName;
                    }
                }
            }

            if (actorTypeName != null) {
                Object result = evaluateExpression(exprStmt.getExpression());
                if (result instanceof SiyoStruct struct) {
                    return createActor(struct, actorTypeName);
                }
            }
            // Not an actor — fall through to scope spawn
        }

        // Bare spawn (outside scope) is fire-and-forget; inside scope, thread is tracked for joining

        // Capture current variable values for the spawn body
        java.util.Map<VariableSymbol, Object> capturedSnapshot = new java.util.HashMap<>();
        for (VariableSymbol var : node.getCapturedVariables()) {
            Object val = lookupVariable(var);
            capturedSnapshot.put(var, val);
        }

        // Copy current function table reference
        java.util.Map<FunctionSymbol, codeanalysis.binding.BoundBlockStatement> funcsCopy = _functions;
        BoundBlockStatement body = node.getBody();

        Thread thread = Thread.startVirtualThread(() -> {
            try {
                // Each spawn gets its own isolated globals copy
                // Channel/SiyoChannel objects are shared by reference (thread-safe)
                // Use synchronized HashMap (not ConcurrentHashMap which rejects null values)
                java.util.Map<VariableSymbol, Object> isolatedGlobals =
                        java.util.Collections.synchronizedMap(new java.util.HashMap<>(_globals));
                Evaluator taskEval = new Evaluator(body, isolatedGlobals, funcsCopy);

                // Inject captured variables (immutable values + channels)
                StackFrame frame = new StackFrame(null);
                for (var entry : capturedSnapshot.entrySet()) {
                    frame.getLocals().put(entry.getKey(), entry.getValue());
                }
                taskEval._callStack.push(frame);
                taskEval.evaluateBlock(body);
                taskEval._callStack.pop();
            } catch (Exception e) {
                _scopeErrors.add(e);
            }
        });

        if (_scopeThreads != null) {
            _scopeThreads.add(thread);
        }
        // Bare spawn (outside scope) — thread runs detached (fire-and-forget)
        return null;
    }

    private java.util.Set<String> _actorTypeNames = new java.util.HashSet<>();

    public void registerActorType(String name) { _actorTypeNames.add(name); }

    // Actor type detection is now done in evaluateSpawnExpression via call expression name

    private Object createActor(SiyoStruct state, String actorTypeName) {
        SiyoActor actor = new SiyoActor(state, actorTypeName);

        // Start actor event loop on virtual thread
        Thread actorThread = Thread.startVirtualThread(() -> {
            while (!actor.isStopped()) {
                try {
                    SiyoActor.ActorMessage msg = actor.getMailbox().take();
                    if (actor.isStopped() || msg.methodName.equals("__stop__")) break;
                    String qualifiedName = actorTypeName + "." + msg.methodName;

                    // Find the function
                    codeanalysis.binding.BoundBlockStatement body = null;
                    FunctionSymbol func = null;
                    for (var entry : _functions.entrySet()) {
                        if (entry.getKey().getName().equals(qualifiedName)) {
                            func = entry.getKey();
                            body = entry.getValue();
                            break;
                        }
                    }

                    if (body == null) {
                        if (msg.replyChannel != null) {
                            msg.replyChannel.put(new SiyoActor.ActorError("Unknown method: " + msg.methodName));
                        }
                        continue;
                    }

                    // Execute method with actor state as self
                    java.util.Map<VariableSymbol, Object> isolatedGlobals =
                            java.util.Collections.synchronizedMap(new java.util.HashMap<>(_globals));
                    Evaluator actorEval = new Evaluator(body, isolatedGlobals, _functions);
                    StackFrame frame = new StackFrame(func);

                    // Bind self (first param) to actor state
                    frame.getLocals().put(func.getParameters().get(0), state);
                    // Bind remaining params
                    for (int i = 0; i < msg.args.length; i++) {
                        frame.getLocals().put(func.getParameters().get(i + 1), msg.args[i]);
                    }

                    actorEval._callStack.push(frame);
                    actorEval._actorTypeNames = _actorTypeNames; // propagate actor types
                    actorEval.evaluateBlock(body);
                    actorEval._callStack.pop();

                    Object result = actorEval._returnTriggered ? actorEval._returnValue : actorEval._lastValue;
                    actorEval._returnTriggered = false;

                    if (msg.replyChannel != null) {
                        msg.replyChannel.put(result != null ? result : "");
                    }

                    // Check if actor has `run` method and this is the first message
                } catch (Exception e) {
                    // Don't crash the actor — continue processing messages
                }
            }
        });

        actor.setThread(actorThread);

        // If actor has a `run(self)` method, auto-invoke it (fire-and-forget)
        String runMethod = actorTypeName + ".run";
        for (var entry : _functions.entrySet()) {
            if (entry.getKey().getName().equals(runMethod)) {
                actor.send("run", new Object[]{});
                break;
            }
        }

        return actor;
    }

    private Object evaluateLambdaExpression(BoundLambdaExpression node) {
        // Capture current variable values from the active scope
        java.util.Map<VariableSymbol, Object> capturedVars = new java.util.HashMap<>();
        for (VariableSymbol var : node.getCapturedVariables()) {
            // Look up value in call stack or globals
            Object value = lookupVariable(var);
            capturedVars.put(var, value);
        }
        return new SiyoClosure(node.getParameters(), node.getBody(), capturedVars, node.getReturnType());
    }

    private Object siyoArrayToJavaArray(SiyoArray arr, Class<?> componentType) {
        Object javaArr = java.lang.reflect.Array.newInstance(componentType, arr.length());
        for (int i = 0; i < arr.length(); i++) {
            Object elem = arr.get(i);
            if (componentType == byte.class) java.lang.reflect.Array.setByte(javaArr, i, ((Number) elem).byteValue());
            else if (componentType == int.class) java.lang.reflect.Array.setInt(javaArr, i, ((Number) elem).intValue());
            else if (componentType == double.class) java.lang.reflect.Array.setDouble(javaArr, i, ((Number) elem).doubleValue());
            else java.lang.reflect.Array.set(javaArr, i, elem);
        }
        return javaArr;
    }

    private Object evaluateJavaStaticField(BoundJavaStaticFieldExpression node) throws Exception {
        Class<?> cls = Class.forName(node.getClassInfo().getFullName(), true, JavaClasspath.getClassLoader());
        java.lang.reflect.Field field = cls.getField(node.getFieldName());
        return field.get(null);
    }

    private Object evaluateIndexAssignment(BoundIndexAssignmentExpression node) throws Exception {
        Object target = evaluateExpression(node.getTarget());
        int index = (int) evaluateExpression(node.getIndex());
        Object value = evaluateExpression(node.getValue());

        if (target instanceof SiyoArray arr) {
            arr.set(index, value);
        }
        return value;
    }

    private Object evaluateMemberAssignment(BoundMemberAssignmentExpression node) throws Exception {
        Object target = evaluateExpression(node.getTarget());
        Object value = evaluateExpression(node.getValue());

        if (target instanceof SiyoStruct struct) {
            struct.setField(node.getMemberName(), value);
        }
        return value;
    }

    private Object evaluateStructLiteralExpression(BoundStructLiteralExpression node) throws Exception {
        java.util.LinkedHashMap<String, Object> fields = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, BoundExpression> entry : node.getFieldValues().entrySet()) {
            fields.put(entry.getKey(), evaluateExpression(entry.getValue()));
        }
        return new SiyoStruct(node.getStructType(), fields);
    }

    private Object evaluateMapLiteralExpression(codeanalysis.binding.BoundMapLiteralExpression node) throws Exception {
        SiyoMap map = new SiyoMap();
        for (int i = 0; i < node.getKeys().size(); i++) {
            Object key = evaluateExpression(node.getKeys().get(i));
            Object value = evaluateExpression(node.getValues().get(i));
            map.set(key, value);
        }
        return map;
    }

    private Object evaluateArrayLiteralExpression(BoundArrayLiteralExpression node) throws Exception {
        java.util.List<Object> elements = new java.util.ArrayList<>();
        for (BoundExpression element : node.getElements()) {
            elements.add(evaluateExpression(element));
        }
        return new SiyoArray(elements, node.getElementType());
    }

    private Object evaluateIndexExpression(BoundIndexExpression node) throws Exception {
        Object target = evaluateExpression(node.getTarget());
        Object indexValue = evaluateExpression(node.getIndex());
        int index = (int) indexValue;

        if (target instanceof SiyoArray arr) {
            return arr.get(index);
        } else if (target instanceof String str) {
            return String.valueOf(str.charAt(index));
        }
        throw new Exception("Cannot index type: " + target.getClass());
    }

    private Object evaluateMemberAccessExpression(BoundMemberAccessExpression node) throws Exception {
        Object target = evaluateExpression(node.getTarget());
        if (target instanceof SiyoStruct struct) {
            return struct.getField(node.getMemberName());
        }
        throw new Exception("Cannot access member on type: " + target.getClass());
    }

    /**
     * Evaluates a built-in function call.
     *
     * @param function  The built-in function symbol.
     * @param arguments The evaluated arguments.
     * @return The result of the built-in function.
     * @throws Exception if the function is not recognized.
     */
    private Object evaluateBuiltinFunction(FunctionSymbol function, Object[] arguments) throws Exception {
        if (function == BuiltinFunctions.LEN) {
            if (arguments[0] instanceof SiyoArray arr) {
                return arr.length();
            }
            return ((String) arguments[0]).length();
        }
        if (function == BuiltinFunctions.TO_STRING) {
            return String.valueOf(arguments[0]);
        }
        if (function == BuiltinFunctions.PARSE_INT) {
            try {
                return Integer.parseInt((String) arguments[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (function == BuiltinFunctions.PARSE_FLOAT) {
            try {
                return Double.parseDouble((String) arguments[0]);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        if (function == BuiltinFunctions.TO_INT) {
            return ((Double) arguments[0]).intValue();
        }
        if (function == BuiltinFunctions.TO_INT_STR) {
            try { return Integer.parseInt((String) arguments[0]); }
            catch (NumberFormatException e) { return 0; }
        }
        if (function == BuiltinFunctions.PARSE_LONG) {
            return Long.parseLong((String) arguments[0]);
        }
        if (function == BuiltinFunctions.TO_LONG) {
            return ((Integer) arguments[0]).longValue();
        }
        if (function == BuiltinFunctions.TO_FLOAT) {
            return ((Integer) arguments[0]).doubleValue();
        }
        if (function == BuiltinFunctions.TO_DOUBLE) {
            return ((Integer) arguments[0]).doubleValue();
        }
        if (function == BuiltinFunctions.TO_UPPER) {
            return ((String) arguments[0]).toUpperCase();
        }
        if (function == BuiltinFunctions.TO_LOWER) {
            return ((String) arguments[0]).toLowerCase();
        }
        if (function == BuiltinFunctions.PRINT) {
            System.out.print(arguments[0]);
            return null;
        }
        if (function == BuiltinFunctions.RANGE) {
            int start = (int) arguments[0];
            int end = (int) arguments[1];
            java.util.List<Object> elements = new java.util.ArrayList<>();
            for (int i = start; i < end; i++) elements.add(i);
            return new SiyoArray(elements, Integer.class);
        }
        if (function == BuiltinFunctions.PUSH) {
            SiyoArray arr = (SiyoArray) arguments[0];
            arr.getElements().add(arguments[1]);
            return null;
        }
        if (function == BuiltinFunctions.SUBSTRING) {
            return ((String) arguments[0]).substring((int) arguments[1], (int) arguments[2]);
        }
        if (function == BuiltinFunctions.CONTAINS) {
            return ((String) arguments[0]).contains((String) arguments[1]);
        }
        if (function == BuiltinFunctions.ERROR) {
            throw new RuntimeException((String) arguments[0]);
        }
        if (function == BuiltinFunctions.INPUT) {
            System.out.print(arguments[0]);
            return _stdinScanner.nextLine();
        }
        if (function == BuiltinFunctions.PRINTLN) {
            System.out.println(arguments[0]);
            return null;
        }
        if (function == BuiltinFunctions.SORT) {
            SiyoArray arr = (SiyoArray) arguments[0];
            SiyoClosure comparator = (SiyoClosure) arguments[1];
            // Sort using closure as comparator
            arr.sort((a, b) -> {
                try {
                    // Call closure with (a, b) → int
                    StackFrame frame = new StackFrame(null);
                    for (var entry : comparator.getCapturedVars().entrySet()) {
                        frame.getLocals().put(entry.getKey(), entry.getValue());
                    }
                    frame.getLocals().put(comparator.getParameters().get(0), a);
                    frame.getLocals().put(comparator.getParameters().get(1), b);
                    _callStack.push(frame);
                    _returnTriggered = false;
                    evaluateBlock(comparator.getBody());
                    _callStack.pop();
                    Object result = _returnTriggered ? _returnValue : _lastValue;
                    _returnTriggered = false;
                    _returnValue = null;
                    return (result instanceof Integer i) ? i : 0;
                } catch (Exception e) {
                    return 0;
                }
            });
            return null;
        }
        if (function == BuiltinFunctions.MAP_KEYS) {
            SiyoMap map = (SiyoMap) arguments[0];
            return map.keys();
        }
        if (function == BuiltinFunctions.NEW_MAP) {
            return new SiyoMap();
        }
        if (function == BuiltinFunctions.NEW_SET) {
            return new SiyoSet();
        }
        if (function == BuiltinFunctions.CHANNEL) {
            return new SiyoChannel(); // unbuffered (SynchronousQueue)
        }
        if (function == BuiltinFunctions.CHANNEL_BUFFERED) {
            return new SiyoChannel((int) arguments[0]); // buffered
        }
        if (function == BuiltinFunctions.RANDOM) {
            return java.util.concurrent.ThreadLocalRandom.current().nextInt((int) arguments[0]);
        }
        if (function == BuiltinFunctions.CHR) {
            int code = (arguments[0] instanceof Byte b) ? (int) b : (int) arguments[0];
            return Character.toString(code);
        }
        if (function == BuiltinFunctions.ORD) {
            String s = (String) arguments[0];
            return s.isEmpty() ? 0 : (int) s.charAt(0);
        }
        if (function == BuiltinFunctions.INDEX_OF) {
            return ((String) arguments[0]).indexOf((String) arguments[1]);
        }
        if (function == BuiltinFunctions.STARTS_WITH) {
            return ((String) arguments[0]).startsWith((String) arguments[1]);
        }
        if (function == BuiltinFunctions.ENDS_WITH) {
            return ((String) arguments[0]).endsWith((String) arguments[1]);
        }
        if (function == BuiltinFunctions.REPLACE) {
            return ((String) arguments[0]).replace((String) arguments[1], (String) arguments[2]);
        }
        if (function == BuiltinFunctions.TRIM) {
            return ((String) arguments[0]).trim();
        }
        if (function == BuiltinFunctions.REMOVE_AT) {
            SiyoArray arr = (SiyoArray) arguments[0];
            arr.remove((int) arguments[1]);
            return null;
        }
        if (function == BuiltinFunctions.POP) {
            SiyoArray arr = (SiyoArray) arguments[0];
            return arr.remove(arr.length() - 1);
        }
        if (function == BuiltinFunctions.SPLIT) {
            String[] parts = ((String) arguments[0]).split(java.util.regex.Pattern.quote((String) arguments[1]), -1);
            SiyoArray arr = new SiyoArray(java.util.Arrays.asList(parts), String.class);
            return arr;
        }
        if (function == BuiltinFunctions.ACTOR_HANDLE) {
            // Extract __handle__ from the struct's fields map
            if (arguments[0] instanceof SiyoStruct s) {
                Object handle = s.getFieldsMap().get("__handle__");
                if (handle != null) return handle;
            }
            if (arguments[0] instanceof java.util.Map m) {
                Object handle = m.get("__handle__");
                if (handle != null) return handle;
            }
            return arguments[0]; // fallback: return self as-is
        }
        if (function == BuiltinFunctions.CAN_READ) {
            if (arguments[0] instanceof java.io.BufferedReader br) return br.ready();
            if (arguments[0] instanceof java.io.Reader r) return r.ready();
            return false;
        }
        if (function == BuiltinFunctions.HTTP_GET) {
            return SiyoHttp.get((String) arguments[0]);
        }
        if (function == BuiltinFunctions.HTTP_POST) {
            return SiyoHttp.post((String) arguments[0], (String) arguments[1]);
        }
        throw new Exception("Unknown built-in function: " + function.getName());
    }
}

