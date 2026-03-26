package codeanalysis.emitting;

import codeanalysis.*;
import codeanalysis.binding.*;
import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emits JVM bytecode from the bound tree using ASM.
 */
public class Emitter {
    private final BoundBlockStatement _statement;
    private final Map<FunctionSymbol, BoundBlockStatement> _functions;
    private final DiagnosticBox _diagnostics = new DiagnosticBox();
    private String _className;

    // Current method state
    private MethodVisitor _mv;
    private final Map<VariableSymbol, Integer> _locals = new HashMap<>();
    private final Map<LabelSymbol, Label> _labels = new HashMap<>();
    private int _nextLocal = 0;

    public Emitter(BoundBlockStatement statement, Map<FunctionSymbol, BoundBlockStatement> functions) {
        _statement = statement;
        _functions = functions;
    }

    public DiagnosticBox getDiagnostics() {
        return _diagnostics;
    }

    /**
     * Emits the bytecode for the program as a Java class file.
     *
     * @param className The name of the generated class.
     * @return The class file bytes.
     */
    public byte[] emit(String className) {
        _className = className;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", null);

        // Generate default constructor
        emitConstructor(cw);

        // Generate user-defined functions as static methods
        for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : _functions.entrySet()) {
            emitFunction(cw, entry.getKey(), entry.getValue(), className);
        }

        // Generate main method with the top-level statements
        emitMainMethod(cw, className);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void emitConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void emitMainMethod(ClassWriter cw, String className) {
        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 1; // slot 0 is args

        emitStatement(_statement);

        _mv.visitInsn(RETURN);
        _mv.visitMaxs(0, 0); // computed by COMPUTE_MAXS
        _mv.visitEnd();
    }

    private void emitFunction(ClassWriter cw, FunctionSymbol function, BoundBlockStatement body, String className) {
        if (BuiltinFunctions.isBuiltin(function)) return;

        String descriptor = getFunctionDescriptor(function);
        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, function.getName(), descriptor, null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 0;

        // Allocate parameter slots
        for (ParameterSymbol param : function.getParameters()) {
            int slot = _nextLocal;
            _locals.put(param, slot);
            _nextLocal += getLocalSize(param.getType());
        }

        // Emit function body, handling implicit return for the last expression
        emitFunctionBody(body, function);

        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
    }

    private void emitFunctionBody(BoundBlockStatement body, FunctionSymbol function) {
        var statements = body.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            BoundStatement stmt = statements.get(i);
            boolean isLast = (i == statements.size() - 1);

            // For the last expression statement in a non-void function, emit as return
            if (isLast && function.getReturnType() != null && stmt instanceof BoundExpressionStatement exprStmt) {
                emitExpression(exprStmt.getExpression());
                _mv.visitInsn(getReturnInsn(function.getReturnType()));
                return;
            }

            emitStatement(stmt);
        }

        // Add default return if no explicit return was emitted
        if (function.getReturnType() == null) {
            _mv.visitInsn(RETURN);
        }
    }

    // ========== Statement Emission ==========

    private void emitStatement(BoundStatement node) {
        switch (node.getType()) {
            case BlockStatement -> emitBlockStatement((BoundBlockStatement) node);
            case ExpressionStatement -> emitExpressionStatement((BoundExpressionStatement) node);
            case VariableDeclaration -> emitVariableDeclaration((BoundVariableDeclaration) node);
            case LabelStatement -> emitLabelStatement((BoundLabelStatement) node);
            case GotoStatement -> emitGotoStatement((BoundGotoStatement) node);
            case ConditionalGotoStatement -> emitConditionalGotoStatement((BoundConditionalGotoStatement) node);
            case ReturnStatement -> emitReturnStatement((BoundReturnStatement) node);
            default -> throw new UnsupportedOperationException("Cannot emit statement: " + node.getType());
        }
    }

    private void emitBlockStatement(BoundBlockStatement node) {
        for (BoundStatement statement : node.getStatements()) {
            emitStatement(statement);
        }
    }

    private void emitExpressionStatement(BoundExpressionStatement node) {
        Class<?> type = node.getExpression().getClassType();
        // Void calls (println, print) don't push anything on stack
        if (type == null) {
            emitExpression(node.getExpression());
            return;
        }
        emitExpression(node.getExpression());
        // Pop non-void results
        if (type == Double.class || type == Long.class) {
            _mv.visitInsn(POP2);
        } else {
            _mv.visitInsn(POP);
        }
    }

    private void emitVariableDeclaration(BoundVariableDeclaration node) {
        emitExpression(node.getInitializer());
        int slot = declareLocal(node.getVariable());
        emitStore(node.getVariable().getType(), slot);
    }

    private void emitLabelStatement(BoundLabelStatement node) {
        _mv.visitLabel(getLabel(node.getLabel()));
    }

    private void emitGotoStatement(BoundGotoStatement node) {
        _mv.visitJumpInsn(GOTO, getLabel(node.getLabel()));
    }

    private void emitConditionalGotoStatement(BoundConditionalGotoStatement node) {
        emitExpression(node.getCondition());
        if (node.getJumpIfTrue()) {
            _mv.visitJumpInsn(IFNE, getLabel(node.getLabel()));
        } else {
            _mv.visitJumpInsn(IFEQ, getLabel(node.getLabel()));
        }
    }

    private void emitReturnStatement(BoundReturnStatement node) {
        if (node.getExpression() != null) {
            emitExpression(node.getExpression());
            Class<?> type = node.getExpression().getClassType();
            _mv.visitInsn(getReturnInsn(type));
        } else {
            _mv.visitInsn(RETURN);
        }
    }

    // ========== Expression Emission ==========

    private void emitExpression(BoundExpression node) {
        switch (node.getType()) {
            case LiteralExpression -> emitLiteralExpression((BoundLiteralExpression) node);
            case VariableExpression -> emitVariableExpression((BoundVariableExpression) node);
            case AssignmentExpression -> emitAssignmentExpression((BoundAssignmentExpression) node);
            case UnaryExpression -> emitUnaryExpression((BoundUnaryExpression) node);
            case BinaryExpression -> emitBinaryExpression((BoundBinaryExpression) node);
            case CallExpression -> emitCallExpression((BoundCallExpression) node);
            default -> throw new UnsupportedOperationException("Cannot emit expression: " + node.getType());
        }
    }

    private void emitLiteralExpression(BoundLiteralExpression node) {
        Object value = node.getValue();
        if (value == null) {
            _mv.visitInsn(ACONST_NULL);
        } else if (value instanceof Integer i) {
            emitIntConstant(i);
        } else if (value instanceof Boolean b) {
            _mv.visitInsn(b ? ICONST_1 : ICONST_0);
        } else if (value instanceof Double d) {
            _mv.visitLdcInsn(d);
        } else if (value instanceof String s) {
            _mv.visitLdcInsn(s);
        } else {
            throw new UnsupportedOperationException("Cannot emit literal: " + value.getClass());
        }
    }

    private void emitVariableExpression(BoundVariableExpression node) {
        int slot = getLocal(node.getVariable());
        emitLoad(node.getVariable().getType(), slot);
    }

    private void emitAssignmentExpression(BoundAssignmentExpression node) {
        emitExpression(node.getExpression());
        int slot = getLocal(node.getVariable());
        // Duplicate value on stack (assignment is an expression that returns the value)
        Class<?> type = node.getExpression().getClassType();
        if (type == Double.class || type == Long.class) {
            _mv.visitInsn(DUP2);
        } else {
            _mv.visitInsn(DUP);
        }
        emitStore(node.getVariable().getType(), slot);
    }

    private void emitUnaryExpression(BoundUnaryExpression node) {
        emitExpression(node.getOperand());
        switch (node.getOperator().getType()) {
            case Identity -> {} // no-op
            case Negation -> {
                if (node.getOperand().getClassType() == Double.class) {
                    _mv.visitInsn(DNEG);
                } else {
                    _mv.visitInsn(INEG);
                }
            }
            case LogicalNegation -> {
                // !bool: XOR with 1
                _mv.visitInsn(ICONST_1);
                _mv.visitInsn(IXOR);
            }
            case OnesComplement -> {
                // ~int: XOR with -1
                _mv.visitInsn(ICONST_M1);
                _mv.visitInsn(IXOR);
            }
        }
    }

    private void emitBinaryExpression(BoundBinaryExpression node) {
        Class<?> type = node.getLeft().getClassType();

        // String concatenation
        if (node.getOperator().getType() == BoundBinaryOperatorType.Addition && type == String.class) {
            emitStringConcat(node);
            return;
        }

        // Comparison operators need special handling
        if (isComparisonOperator(node.getOperator().getType())) {
            emitComparisonExpression(node);
            return;
        }

        emitExpression(node.getLeft());
        emitExpression(node.getRight());

        boolean isDouble = type == Double.class;
        switch (node.getOperator().getType()) {
            case Addition -> _mv.visitInsn(isDouble ? DADD : IADD);
            case Subtraction -> _mv.visitInsn(isDouble ? DSUB : ISUB);
            case Multiplication -> _mv.visitInsn(isDouble ? DMUL : IMUL);
            case Division -> _mv.visitInsn(isDouble ? DDIV : IDIV);
            case Modulo -> _mv.visitInsn(isDouble ? DREM : IREM);
            case BitwiseAnd -> _mv.visitInsn(IAND);
            case BitwiseOr -> _mv.visitInsn(IOR);
            case BitwiseXor -> _mv.visitInsn(IXOR);
            case LeftShift -> _mv.visitInsn(ISHL);
            case RightShift -> _mv.visitInsn(ISHR);
            case LogicalAnd -> _mv.visitInsn(IAND);
            case LogicalOr -> _mv.visitInsn(IOR);
            default -> throw new UnsupportedOperationException("Cannot emit binary operator: " + node.getOperator().getType());
        }
    }

    private void emitComparisonExpression(BoundBinaryExpression node) {
        emitExpression(node.getLeft());
        emitExpression(node.getRight());

        Label trueLabel = new Label();
        Label endLabel = new Label();
        Class<?> type = node.getLeft().getClassType();

        if (type == Double.class) {
            _mv.visitInsn(DCMPG);
            int jumpInsn = switch (node.getOperator().getType()) {
                case Equals -> IFEQ;
                case NotEquals -> IFNE;
                case LessThan -> IFLT;
                case LessOrEqualsThan -> IFLE;
                case GreaterThan -> IFGT;
                case GreaterOrEqualsThen -> IFGE;
                default -> throw new UnsupportedOperationException();
            };
            _mv.visitJumpInsn(jumpInsn, trueLabel);
        } else if (type == Integer.class || type == Boolean.class) {
            int jumpInsn = switch (node.getOperator().getType()) {
                case Equals -> IF_ICMPEQ;
                case NotEquals -> IF_ICMPNE;
                case LessThan -> IF_ICMPLT;
                case LessOrEqualsThan -> IF_ICMPLE;
                case GreaterThan -> IF_ICMPGT;
                case GreaterOrEqualsThen -> IF_ICMPGE;
                default -> throw new UnsupportedOperationException();
            };
            _mv.visitJumpInsn(jumpInsn, trueLabel);
        } else {
            // Object equality (String, null, etc.)
            int jumpInsn = switch (node.getOperator().getType()) {
                case Equals -> IF_ACMPEQ;
                case NotEquals -> IF_ACMPNE;
                default -> throw new UnsupportedOperationException();
            };
            _mv.visitJumpInsn(jumpInsn, trueLabel);
        }

        _mv.visitInsn(ICONST_0);
        _mv.visitJumpInsn(GOTO, endLabel);
        _mv.visitLabel(trueLabel);
        _mv.visitInsn(ICONST_1);
        _mv.visitLabel(endLabel);
    }

    private void emitStringConcat(BoundBinaryExpression node) {
        // Use StringBuilder for string concatenation
        _mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        emitExpression(node.getLeft());
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        emitExpression(node.getRight());
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void emitCallExpression(BoundCallExpression node) {
        FunctionSymbol function = node.getFunction();

        // Built-in functions
        if (function == BuiltinFunctions.PRINTLN) {
            _mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            emitExpression(node.getArguments().get(0));
            emitBoxIfNeeded(node.getArguments().get(0).getClassType());
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
            return;
        }
        if (function == BuiltinFunctions.PRINT) {
            _mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            emitExpression(node.getArguments().get(0));
            emitBoxIfNeeded(node.getArguments().get(0).getClassType());
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/Object;)V", false);
            return;
        }
        if (function == BuiltinFunctions.TO_STRING) {
            emitExpression(node.getArguments().get(0));
            emitBoxIfNeeded(node.getArguments().get(0).getClassType());
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.LEN) {
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            return;
        }

        // User-defined functions: push args and invoke static method
        for (BoundExpression arg : node.getArguments()) {
            emitExpression(arg);
        }
        String owner = _className;
        String descriptor = getFunctionDescriptor(function);
        _mv.visitMethodInsn(INVOKESTATIC, owner, function.getName(), descriptor, false);
    }

    // ========== Helpers ==========

    private boolean isComparisonOperator(BoundBinaryOperatorType type) {
        return type == BoundBinaryOperatorType.Equals || type == BoundBinaryOperatorType.NotEquals
                || type == BoundBinaryOperatorType.LessThan || type == BoundBinaryOperatorType.LessOrEqualsThan
                || type == BoundBinaryOperatorType.GreaterThan || type == BoundBinaryOperatorType.GreaterOrEqualsThen;
    }

    private void emitIntConstant(int value) {
        if (value >= -1 && value <= 5) {
            _mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            _mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            _mv.visitIntInsn(SIPUSH, value);
        } else {
            _mv.visitLdcInsn(value);
        }
    }

    private void emitLoad(Class<?> type, int slot) {
        if (type == Integer.class || type == Boolean.class) {
            _mv.visitVarInsn(ILOAD, slot);
        } else if (type == Double.class) {
            _mv.visitVarInsn(DLOAD, slot);
        } else {
            _mv.visitVarInsn(ALOAD, slot);
        }
    }

    private void emitStore(Class<?> type, int slot) {
        if (type == Integer.class || type == Boolean.class) {
            _mv.visitVarInsn(ISTORE, slot);
        } else if (type == Double.class) {
            _mv.visitVarInsn(DSTORE, slot);
        } else {
            _mv.visitVarInsn(ASTORE, slot);
        }
    }

    private int getReturnInsn(Class<?> type) {
        if (type == Integer.class || type == Boolean.class) return IRETURN;
        if (type == Double.class) return DRETURN;
        return ARETURN;
    }

    private int getLocalSize(Class<?> type) {
        return (type == Double.class || type == Long.class) ? 2 : 1;
    }

    private int declareLocal(VariableSymbol variable) {
        int slot = _nextLocal;
        _locals.put(variable, slot);
        _nextLocal += getLocalSize(variable.getType());
        return slot;
    }

    private int getLocal(VariableSymbol variable) {
        Integer slot = _locals.get(variable);
        if (slot == null) {
            throw new IllegalStateException("Variable not declared: " + variable.getName());
        }
        return slot;
    }

    private Label getLabel(LabelSymbol symbol) {
        return _labels.computeIfAbsent(symbol, k -> new Label());
    }

    private String getFunctionDescriptor(FunctionSymbol function) {
        StringBuilder sb = new StringBuilder("(");
        for (ParameterSymbol param : function.getParameters()) {
            sb.append(getTypeDescriptor(param.getType()));
        }
        sb.append(")");
        sb.append(function.getReturnType() == null ? "V" : getTypeDescriptor(function.getReturnType()));
        return sb.toString();
    }

    private void emitBoxIfNeeded(Class<?> type) {
        if (type == Integer.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (type == Boolean.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (type == Double.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }
    }

    private String getTypeDescriptor(Class<?> type) {
        if (type == Integer.class) return "I";
        if (type == Boolean.class) return "Z";
        if (type == Double.class) return "D";
        if (type == String.class) return "Ljava/lang/String;";
        return "Ljava/lang/Object;";
    }
}
