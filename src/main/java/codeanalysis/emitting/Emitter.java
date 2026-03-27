package codeanalysis.emitting;

import codeanalysis.*;
import codeanalysis.binding.*;
import codeanalysis.JavaMethodSignature;
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
    private boolean _needsRangeHelper = false;

    // Current method state
    private MethodVisitor _mv;
    private final Map<VariableSymbol, Integer> _locals = new HashMap<>();
    private final Map<LabelSymbol, Label> _labels = new HashMap<>();
    private final java.util.Set<VariableSymbol> _globalFields = new java.util.HashSet<>();
    private int _nextLocal = 0;
    private boolean _inMainMethod = false;

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

        // First pass: collect global variables from main body
        collectGlobalVariables(_statement);

        // Generate static fields for global variables
        for (VariableSymbol global : _globalFields) {
            String desc = getTypeDescriptor(global.getType());
            cw.visitField(ACC_STATIC, global.getName(), desc, null, null).visitEnd();
        }

        // Generate default constructor
        emitConstructor(cw);

        // Generate user-defined functions as static methods
        for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : _functions.entrySet()) {
            emitFunction(cw, entry.getKey(), entry.getValue(), className);
        }

        // Generate main method with the top-level statements
        emitMainMethod(cw, className);

        // Emit helper methods if needed
        if (_needsRangeHelper) {
            emitRangeHelper(cw);
        }

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

        // For main method: print the last expression value if non-void
        var statements = _statement.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            BoundStatement stmt = statements.get(i);
            boolean isLast = (i == statements.size() - 1);

            if (isLast && stmt instanceof BoundExpressionStatement exprStmt
                    && exprStmt.getExpression().getClassType() != null) {
                // Print last expression result
                _mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                emitExpression(exprStmt.getExpression());
                emitBoxIfNeeded(exprStmt.getExpression().getClassType());
                _mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
            } else {
                emitStatement(stmt);
            }
        }

        _mv.visitInsn(RETURN);
        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
    }

    private void emitFunction(ClassWriter cw, FunctionSymbol function, BoundBlockStatement body, String className) {
        if (BuiltinFunctions.isBuiltin(function)) return;
        if (function.getModuleName() != null) return; // imported functions compiled in their own class

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
            case TryCatchStatement -> emitTryCatchStatement((BoundTryCatchStatement) node);
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
        Class<?> exprType = node.getInitializer().getClassType();
        Class<?> varType = node.getVariable().getType();
        if (exprType == Object.class && varType != Object.class) {
            emitUnboxIfNeeded(varType);
        }
        if (_globalFields.contains(node.getVariable())) {
            _mv.visitFieldInsn(PUTSTATIC, _className, node.getVariable().getName(), getTypeDescriptor(varType));
        } else {
            int slot = declareLocal(node.getVariable());
            emitStore(varType, slot);
        }
    }

    private void emitLabelStatement(BoundLabelStatement node) {
        _mv.visitLabel(getLabel(node.getLabel()));
    }

    private void emitGotoStatement(BoundGotoStatement node) {
        _mv.visitJumpInsn(GOTO, getLabel(node.getLabel()));
    }

    private void emitConditionalGotoStatement(BoundConditionalGotoStatement node) {
        emitExpression(node.getCondition());
        if (node.getCondition().getClassType() == Object.class) {
            emitUnboxIfNeeded(Boolean.class);
        }
        if (node.getJumpIfTrue()) {
            _mv.visitJumpInsn(IFNE, getLabel(node.getLabel()));
        } else {
            _mv.visitJumpInsn(IFEQ, getLabel(node.getLabel()));
        }
    }

    private void emitTryCatchStatement(BoundTryCatchStatement node) {
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchStart = new Label();
        Label catchEnd = new Label();

        _mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Exception");

        // Try body
        _mv.visitLabel(tryStart);
        emitStatement(node.getTryBody());
        _mv.visitLabel(tryEnd);
        _mv.visitJumpInsn(GOTO, catchEnd);

        // Catch body
        _mv.visitLabel(catchStart);
        // Exception is on stack, get message
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;", false);
        // Store in error variable
        int errorSlot = declareLocal(node.getErrorVariable());
        _mv.visitVarInsn(ASTORE, errorSlot);
        emitStatement(node.getCatchBody());
        _mv.visitLabel(catchEnd);
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
            case ArrayLiteralExpression -> emitArrayLiteralExpression((BoundArrayLiteralExpression) node);
            case IndexExpression -> emitIndexExpression((BoundIndexExpression) node);
            case IndexAssignmentExpression -> emitIndexAssignmentExpression((BoundIndexAssignmentExpression) node);
            case StructLiteralExpression -> emitStructLiteralExpression((BoundStructLiteralExpression) node);
            case MemberAccessExpression -> emitMemberAccessExpression((BoundMemberAccessExpression) node);
            case MemberAssignmentExpression -> emitMemberAssignmentExpression((BoundMemberAssignmentExpression) node);
            case JavaMethodCallExpression -> emitJavaMethodCall((BoundJavaMethodCallExpression) node);
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
        if (_globalFields.contains(node.getVariable())) {
            _mv.visitFieldInsn(GETSTATIC, _className, node.getVariable().getName(), getTypeDescriptor(node.getVariable().getType()));
            return;
        }
        int slot = getLocal(node.getVariable());
        emitLoad(node.getVariable().getType(), slot);
    }

    private void emitAssignmentExpression(BoundAssignmentExpression node) {
        emitExpression(node.getExpression());
        Class<?> exprType = node.getExpression().getClassType();
        Class<?> varType = node.getVariable().getType();
        if (exprType == Object.class && varType != Object.class) {
            emitUnboxIfNeeded(varType);
        }
        // Duplicate value on stack (assignment is an expression that returns the value)
        if (varType == Double.class || varType == Long.class) {
            _mv.visitInsn(DUP2);
        } else {
            _mv.visitInsn(DUP);
        }
        if (_globalFields.contains(node.getVariable())) {
            _mv.visitFieldInsn(PUTSTATIC, _className, node.getVariable().getName(), getTypeDescriptor(varType));
        } else {
            int slot = getLocal(node.getVariable());
            emitStore(varType, slot);
        }
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

        // String concatenation (String + any or any + String)
        if (node.getOperator().getType() == BoundBinaryOperatorType.Addition && node.getOperator().getResultType() == String.class) {
            emitStringConcat(node);
            return;
        }

        // Comparison operators need special handling
        if (isComparisonOperator(node.getOperator().getType())) {
            emitComparisonExpression(node);
            return;
        }

        emitExpression(node.getLeft());
        if (node.getLeft().getClassType() == Object.class) {
            emitUnboxIfNeeded(node.getOperator().getLeftType());
        }
        emitExpression(node.getRight());
        if (node.getRight().getClassType() == Object.class) {
            emitUnboxIfNeeded(node.getOperator().getRightType());
        }

        // If type is Object (from member access/index), determine actual type from operator
        Class<?> operandType = type;
        if (type == Object.class) {
            operandType = node.getOperator().getLeftType();
        }
        boolean isDouble = operandType == Double.class;
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
        Class<?> leftType = node.getLeft().getClassType();
        Class<?> rightType = node.getRight().getClassType();

        // If either side is Object, box both sides and use Object.equals
        if (leftType == Object.class || rightType == Object.class) {
            emitExpression(node.getLeft());
            emitBoxIfNeeded(leftType);
            emitExpression(node.getRight());
            emitBoxIfNeeded(rightType);

            Label trueLabel = new Label();
            Label endLabel = new Label();

            if (node.getOperator().getType() == BoundBinaryOperatorType.Equals) {
                _mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                return;
            } else if (node.getOperator().getType() == BoundBinaryOperatorType.NotEquals) {
                _mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                _mv.visitInsn(ICONST_1);
                _mv.visitInsn(IXOR); // negate
                return;
            }
            // For <, >, <=, >= with Object types, unbox to int and compare
            // This is a fallback - ideally types should be resolved
            emitExpression(node.getLeft());
            emitUnboxIfNeeded(Integer.class);
            emitExpression(node.getRight());
            emitUnboxIfNeeded(Integer.class);
            int jumpInsn = switch (node.getOperator().getType()) {
                case LessThan -> IF_ICMPLT;
                case LessOrEqualsThan -> IF_ICMPLE;
                case GreaterThan -> IF_ICMPGT;
                case GreaterOrEqualsThen -> IF_ICMPGE;
                default -> throw new UnsupportedOperationException();
            };
            _mv.visitJumpInsn(jumpInsn, trueLabel);
            _mv.visitInsn(ICONST_0);
            _mv.visitJumpInsn(GOTO, endLabel);
            _mv.visitLabel(trueLabel);
            _mv.visitInsn(ICONST_1);
            _mv.visitLabel(endLabel);
            return;
        }

        emitExpression(node.getLeft());
        emitExpression(node.getRight());

        Label trueLabel = new Label();
        Label endLabel = new Label();
        Class<?> type = leftType;

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
        _mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        emitExpression(node.getLeft());
        emitStringAppend(node.getLeft().getClassType());
        emitExpression(node.getRight());
        emitStringAppend(node.getRight().getClassType());
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void emitStringAppend(Class<?> type) {
        String appendDesc;
        if (type == Integer.class || type == Boolean.class) {
            appendDesc = "(I)Ljava/lang/StringBuilder;";
            if (type == Boolean.class) appendDesc = "(Z)Ljava/lang/StringBuilder;";
        } else if (type == Double.class) {
            appendDesc = "(D)Ljava/lang/StringBuilder;";
        } else if (type == String.class) {
            appendDesc = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
        } else {
            // Object - box if needed first
            emitBoxIfNeeded(type);
            appendDesc = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
        }
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", appendDesc, false);
    }

    // ========== Java Interop Emission ==========

    private void emitJavaMethodCall(BoundJavaMethodCallExpression node) {
        JavaMethodSignature sig = node.getResolvedSignature();

        if (sig != null && sig.isConstructor()) {
            String internalName = sig.getOwnerInternalName();
            _mv.visitTypeInsn(NEW, internalName);
            _mv.visitInsn(DUP);
            emitJavaArgs(node.getArguments(), sig);
            _mv.visitMethodInsn(INVOKESPECIAL, internalName, "<init>", sig.getDescriptor(), false);
            return;
        }

        if (sig != null && sig.isStatic()) {
            emitJavaArgs(node.getArguments(), sig);
            _mv.visitMethodInsn(INVOKESTATIC, sig.getOwnerInternalName(), sig.getName(), sig.getDescriptor(), sig.isInterface());
            return;
        }

        // Instance method call
        emitExpression(node.getTarget());
        if (sig != null) {
            // Cast Object on stack to the actual Java class
            _mv.visitTypeInsn(CHECKCAST, sig.getOwnerInternalName());
            emitJavaArgs(node.getArguments(), sig);
            _mv.visitMethodInsn(sig.getInvokeOpcode(), sig.getOwnerInternalName(), sig.getName(), sig.getDescriptor(), sig.isInterface());
        } else {
            // Unresolved instance method - fallback with Object boxing
            for (BoundExpression arg : node.getArguments()) {
                emitExpression(arg);
                emitBoxIfNeeded(arg.getClassType());
            }
            // Build descriptor from argument count (all Object)
            StringBuilder desc = new StringBuilder("(");
            for (int i = 0; i < node.getArguments().size(); i++) desc.append("Ljava/lang/Object;");
            desc.append(")Ljava/lang/Object;");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", node.getMethodName(), desc.toString(), false);
        }
    }

    private void emitJavaArgs(java.util.List<BoundExpression> arguments, JavaMethodSignature sig) {
        String[] paramDescs = sig.getParamDescriptors();
        for (int i = 0; i < arguments.size(); i++) {
            emitExpression(arguments.get(i));
            // Box Siyo primitives to match Java parameter types
            Class<?> argType = arguments.get(i).getClassType();
            String paramDesc = i < paramDescs.length ? paramDescs[i] : "Ljava/lang/Object;";
            // If Java param expects Object but Siyo has primitive, box it
            if (paramDesc.startsWith("L") || paramDesc.startsWith("[")) {
                emitBoxIfNeeded(argType);
            }
        }
    }

    // ========== Composite Type Emission ==========

    private void emitArrayLiteralExpression(BoundArrayLiteralExpression node) {
        // Create ArrayList
        _mv.visitTypeInsn(NEW, "java/util/ArrayList");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

        // Add each element
        for (BoundExpression element : node.getElements()) {
            _mv.visitInsn(DUP); // keep list ref on stack
            emitExpression(element);
            emitBoxIfNeeded(element.getClassType());
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
            _mv.visitInsn(POP); // discard boolean return of add()
        }
        // ArrayList remains on stack
    }

    private void emitIndexExpression(BoundIndexExpression node) {
        Class<?> targetType = node.getTarget().getClassType();

        if (targetType == String.class) {
            // String indexing: s.charAt(i) -> String.valueOf(char)
            emitExpression(node.getTarget());
            emitExpression(node.getIndex());
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
            return;
        }

        // Array indexing: list.get(i) -> unbox
        emitExpression(node.getTarget());
        emitExpression(node.getIndex());
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        emitUnboxIfNeeded(node.getClassType());
    }

    private void emitIndexAssignmentExpression(BoundIndexAssignmentExpression node) {
        // list.set(index, value) -> returns old value
        emitExpression(node.getTarget());
        emitExpression(node.getIndex());
        emitExpression(node.getValue());
        emitBoxIfNeeded(node.getValue().getClassType());
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true);
        // set() returns old value as Object, unbox to match expected type
        emitUnboxIfNeeded(node.getValue().getClassType());
    }

    private void emitStructLiteralExpression(BoundStructLiteralExpression node) {
        // Create LinkedHashMap for struct
        _mv.visitTypeInsn(NEW, "java/util/LinkedHashMap");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);

        // Put each field
        for (var entry : node.getFieldValues().entrySet()) {
            _mv.visitInsn(DUP); // keep map ref
            _mv.visitLdcInsn(entry.getKey()); // field name
            emitExpression(entry.getValue());
            emitBoxIfNeeded(entry.getValue().getClassType());
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            _mv.visitInsn(POP); // discard old value
        }
        // Map remains on stack
    }

    private void emitMemberAccessExpression(BoundMemberAccessExpression node) {
        // map.get("fieldName") -> unbox
        emitExpression(node.getTarget());
        _mv.visitLdcInsn(node.getMemberName());
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        emitUnboxIfNeeded(node.getClassType());
    }

    private void emitMemberAssignmentExpression(BoundMemberAssignmentExpression node) {
        // map.put("fieldName", value) -> we want new value on stack
        emitExpression(node.getTarget());  // map
        _mv.visitLdcInsn(node.getMemberName());  // key
        emitExpression(node.getValue());  // value (once only)
        emitBoxIfNeeded(node.getValue().getClassType());
        // Stack: [map, key, boxedValue] - dup value before put consumes it
        _mv.visitInsn(DUP_X2);  // [boxedValue, map, key, boxedValue]
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        _mv.visitInsn(POP);  // discard old value from put, boxedValue remains
        emitUnboxIfNeeded(node.getValue().getClassType());  // unbox if needed
    }

    private void emitCallExpression(BoundCallExpression node) {
        FunctionSymbol function = node.getFunction();

        // Built-in functions
        if (function == BuiltinFunctions.ERROR) {
            _mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            _mv.visitInsn(DUP);
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
            _mv.visitInsn(ATHROW);
            return;
        }
        if (function == BuiltinFunctions.INPUT) {
            // print prompt
            _mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            // read line
            _mv.visitTypeInsn(NEW, "java/util/Scanner");
            _mv.visitInsn(DUP);
            _mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            _mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
            return;
        }
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
            BoundExpression arg = node.getArguments().get(0);
            emitExpression(arg);
            if (arg.getClassType() == String.class) {
                _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            } else {
                // ArrayList.size()
                _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
            }
            return;
        }

        if (function == BuiltinFunctions.PARSE_INT) {
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            return;
        }
        if (function == BuiltinFunctions.PARSE_FLOAT) {
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
            return;
        }
        if (function == BuiltinFunctions.TO_INT) {
            emitExpression(node.getArguments().get(0));
            _mv.visitInsn(D2I);
            return;
        }
        if (function == BuiltinFunctions.TO_FLOAT) {
            emitExpression(node.getArguments().get(0));
            _mv.visitInsn(I2D);
            return;
        }

        if (function == BuiltinFunctions.RANGE) {
            // Create ArrayList with range values - emit as helper static method call
            // For now, inline: new ArrayList(); for(int i=start;i<end;i++) list.add(i)
            emitExpression(node.getArguments().get(0)); // start
            emitExpression(node.getArguments().get(1)); // end
            _mv.visitMethodInsn(INVOKESTATIC, _className, "$range", "(II)Ljava/util/List;", false);
            _needsRangeHelper = true;
            return;
        }
        if (function == BuiltinFunctions.PUSH) {
            emitExpression(node.getArguments().get(0)); // list
            emitExpression(node.getArguments().get(1)); // value
            emitBoxIfNeeded(node.getArguments().get(1).getClassType());
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
            _mv.visitInsn(POP); // discard boolean
            return;
        }
        if (function == BuiltinFunctions.SUBSTRING) {
            emitExpression(node.getArguments().get(0));
            emitExpression(node.getArguments().get(1));
            emitExpression(node.getArguments().get(2));
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.CONTAINS) {
            emitExpression(node.getArguments().get(0));
            emitExpression(node.getArguments().get(1));
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            return;
        }

        // User-defined functions: push args and invoke static method
        for (BoundExpression arg : node.getArguments()) {
            emitExpression(arg);
        }
        String owner = function.getModuleName() != null ? function.getModuleName() : _className;
        String descriptor = getFunctionDescriptor(function);
        // For imported functions, name is "module.func" - extract just the function name
        String methodName = function.getName();
        if (methodName.contains(".")) {
            methodName = methodName.substring(methodName.lastIndexOf('.') + 1);
        }
        _mv.visitMethodInsn(INVOKESTATIC, owner, methodName, descriptor, false);
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

    private void collectGlobalVariables(BoundBlockStatement block) {
        for (BoundStatement stmt : block.getStatements()) {
            if (stmt instanceof BoundVariableDeclaration varDecl) {
                _globalFields.add(varDecl.getVariable());
            }
        }
    }

    private void emitRangeHelper(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "$range", "(II)Ljava/util/List;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, "java/util/ArrayList");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, 2); // list in slot 2
        mv.visitVarInsn(ILOAD, 0);  // i = start
        mv.visitVarInsn(ISTORE, 3); // i in slot 3
        Label loopStart = new Label();
        Label loopEnd = new Label();
        mv.visitLabel(loopStart);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitVarInsn(ILOAD, 1); // end
        mv.visitJumpInsn(IF_ICMPGE, loopEnd);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(POP);
        mv.visitIincInsn(3, 1); // i++
        mv.visitJumpInsn(GOTO, loopStart);
        mv.visitLabel(loopEnd);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitUnboxIfNeeded(Class<?> type) {
        if (type == Integer.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (type == Boolean.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (type == Double.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else if (type == String.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        }
        // Object.class and others: leave as Object on stack
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
        if (type == SiyoArray.class) return "Ljava/util/List;";
        if (type == SiyoStruct.class) return "Ljava/util/Map;";
        return "Ljava/lang/Object;";
    }
}
