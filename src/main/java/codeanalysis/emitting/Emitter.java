package codeanalysis.emitting;

import codeanalysis.*;
import codeanalysis.binding.*;
import codeanalysis.text.SourceText;
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
    private boolean _needsSortHelper = false;

    // Current method state
    private MethodVisitor _mv;
    private final Map<VariableSymbol, Integer> _locals = new HashMap<>();
    private final Map<LabelSymbol, Label> _labels = new HashMap<>();
    private final java.util.Set<VariableSymbol> _globalFields = new java.util.HashSet<>();
    private int _nextLocal = 0;
    private boolean _inMainMethod = false;
    private String _currentActorTypeName = null; // non-null when emitting an actor impl method
    private Class<?> _currentReturnType = null; // return type of the function being emitted
    private boolean _needsScanner = false;
    private boolean _tryCatchImplicitReturn = false;
    private Class<?> _tryCatchReturnType = null;
    private java.util.Set<VariableSymbol> _spawnCapturedVars = null;
    private SourceText _sourceText = null;
    private String _sourceFileName = null;
    private int _lastEmittedLine = -1;
    private boolean _isModuleClass = false;

    // Lambda/closure tracking for bytecode emission
    private final java.util.List<BoundLambdaExpression> _lambdas = new java.util.ArrayList<>();
    private final java.util.List<BoundSpawnExpression> _spawns = new java.util.ArrayList<>();
    private ClassWriter _classWriter; // keep reference for lambda method emission

    public Emitter(BoundBlockStatement statement, Map<FunctionSymbol, BoundBlockStatement> functions) {
        _statement = statement;
        _functions = functions;
    }

    public void setSourceText(SourceText sourceText, String fileName) {
        _sourceText = sourceText;
        _sourceFileName = fileName;
    }

    public void setModuleClass(boolean isModule) {
        _isModuleClass = isModule;
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
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // Safe fallback for frame computation - avoids class loading issues
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Exception | LinkageError e) {
                    return "java/lang/Object";
                }
            }
        };
        _classWriter = cw;
        cw.visit(V21, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", null);
        if (_sourceFileName != null) {
            cw.visitSource(_sourceFileName, null);
        }

        // First pass: collect global variables and lambdas
        collectGlobalVariables(_statement);
        collectLambdasAndSpawns(_statement);
        for (var body : _functions.values()) {
            collectLambdasAndSpawns(body);
        }

        // Generate static fields for global variables (dedup by name)
        java.util.Set<String> emittedFields = new java.util.HashSet<>();
        for (VariableSymbol global : _globalFields) {
            if (emittedFields.add(global.getName())) {
                String desc = getTypeDescriptor(global.getType());
                cw.visitField(ACC_STATIC, global.getName(), desc, null, null).visitEnd();
            }
        }

        // Generate default constructor
        emitConstructor(cw);

        // Generate user-defined functions as static methods
        // Skip module functions — they are emitted in their own module class.
        // A function belongs to a module if it has moduleName set (qualified import)
        // OR if its body is shared with a qualified module function (unqualified cross-ref copy).
        java.util.Set<BoundBlockStatement> moduleBodies = new java.util.HashSet<>();
        for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : _functions.entrySet()) {
            if (entry.getKey().getModuleName() != null) {
                moduleBodies.add(entry.getValue());
            }
        }
        for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : _functions.entrySet()) {
            FunctionSymbol func = entry.getKey();
            if (func.getModuleName() != null) continue;
            if (moduleBodies.contains(entry.getValue())) continue; // unqualified copy of module func
            emitFunction(cw, func, entry.getValue(), className);
        }

        // Generate main method FIRST (collects lambdas/spawns during emission)
        if (_isModuleClass) {
            emitModuleInitializer(cw, className);
        } else {
            emitMainMethod(cw, className);
        }

        // Generate lambda methods (discovered during main emission)
        for (int i = 0; i < _lambdas.size(); i++) {
            emitLambdaMethod(cw, i, _lambdas.get(i));
        }

        // Generate spawn methods (skip actor spawns — they use event loop, not spawn methods)
        for (int i = 0; i < _spawns.size(); i++) {
            if (_spawns.get(i).getActorTypeName() != null) continue;
            emitSpawnMethod(cw, i, _spawns.get(i));
        }

        // Generate closure dispatch method
        if (!_lambdas.isEmpty()) {
            emitClosureDispatch(cw);
        }

        // Generate spawn wrapper methods
        if (!_spawns.isEmpty()) {
            emitSpawnWrapperMethods(cw);
        }

        // Generate actor event loop helper
        if (_needsActorStart) {
            emitActorStartMethod(cw);
        }

        // Emit helper methods if needed
        if (_needsRangeHelper) {
            emitRangeHelper(cw);
        }
        if (_needsSortHelper) {
            emitSortHelper(cw);
        }

        // Generate shared Scanner field if needed (skip for module classes — they have their own <clinit>)
        if (_needsScanner && !_isModuleClass) {
            cw.visitField(ACC_STATIC, "$scanner", "Ljava/util/Scanner;", null, null).visitEnd();
            MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode();
            clinit.visitTypeInsn(NEW, "java/util/Scanner");
            clinit.visitInsn(DUP);
            clinit.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            clinit.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
            clinit.visitFieldInsn(PUTSTATIC, className, "$scanner", "Ljava/util/Scanner;");
            clinit.visitInsn(RETURN);
            clinit.visitMaxs(0, 0);
            clinit.visitEnd();
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

    /**
     * For module classes: emit a <clinit> that initializes module-level variables.
     */
    private void emitModuleInitializer(ClassWriter cw, String className) {
        if (_statement.getStatements().isEmpty() && !_needsScanner) return;

        // Check if there are any variable declarations to initialize
        boolean hasVarDecls = false;
        for (BoundStatement stmt : _statement.getStatements()) {
            if (stmt instanceof BoundVariableDeclaration) {
                hasVarDecls = true;
                break;
            }
        }
        if (!hasVarDecls) return;

        _mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 0;
        _inMainMethod = true; // allow global field access
        _lastEmittedLine = -1;

        for (BoundStatement stmt : _statement.getStatements()) {
            if (stmt instanceof BoundVariableDeclaration) {
                emitStatement(stmt);
            }
        }

        _mv.visitInsn(RETURN);
        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
        _inMainMethod = false;
    }

    private void emitMainMethod(ClassWriter cw, String className) {
        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 1; // slot 0 is args
        _inMainMethod = true;
        _currentActorTypeName = null; // main method is not inside any actor
        _lastEmittedLine = -1;

        // Store program args in SiyoRuntime for os.args() access
        _mv.visitVarInsn(ALOAD, 0);
        _mv.visitFieldInsn(PUTSTATIC, "codeanalysis/SiyoRuntime", "programArgs", "[Ljava/lang/String;");

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
        if (function.getModuleName() != null) return;

        String methodName = function.getName().replace('.', '$');
        String descriptor = getFunctionDescriptor(function);
        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, descriptor, null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 0;
        _inMainMethod = false; // user functions should not access global fields by name
        _lastEmittedLine = -1;

        // Track if this is an actor impl method (self is raw struct, not SiyoActor handle)
        _currentActorTypeName = null;
        if (function.getName().contains(".")) {
            String typeName = function.getName().substring(0, function.getName().indexOf('.'));
            if (_actorTypeNames.contains(typeName)) {
                _currentActorTypeName = typeName;
            }
        }

        // Allocate parameter slots
        for (ParameterSymbol param : function.getParameters()) {
            int slot = _nextLocal;
            _locals.put(param, slot);
            _nextLocal += getLocalSize(param.getType());
        }

        _currentReturnType = function.getReturnType();

        // Emit function body, handling implicit return for the last expression
        emitFunctionBody(body, function);

        try {
            _mv.visitMaxs(0, 0);
        } catch (Exception e) {
            System.err.println("[ASM ERROR] Function " + function.getName() + ": " + e.getMessage());
            throw e;
        }
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
                // Coerce expression type to match declared return type
                Class<?> exprType = exprStmt.getExpression().getClassType();
                Class<?> retType = function.getReturnType();
                if (exprType == Object.class && retType != Object.class) {
                    if (retType == Integer.class) emitUnboxIfNeeded(Integer.class);
                    else if (retType == Long.class) emitUnboxIfNeeded(Long.class);
                    else if (retType == Boolean.class) emitUnboxIfNeeded(Boolean.class);
                    else if (retType == Double.class) emitUnboxIfNeeded(Double.class);
                    else {
                        String desc = getTypeDescriptor(retType);
                        if (desc.startsWith("L") && desc.endsWith(";")) {
                            _mv.visitTypeInsn(CHECKCAST, desc.substring(1, desc.length() - 1));
                        }
                    }
                }
                _mv.visitInsn(getReturnInsn(function.getReturnType()));
                return;
            }

            // For try/catch as last statement in non-void function, enable implicit return in bodies
            if (isLast && function.getReturnType() != null && stmt instanceof BoundTryCatchStatement) {
                _tryCatchImplicitReturn = true;
                _tryCatchReturnType = function.getReturnType();
            }

            emitStatement(stmt);
            _tryCatchImplicitReturn = false;
        }

        // Add default return for all functions (safety net for try/catch paths)
        if (function.getReturnType() == null) {
            _mv.visitInsn(RETURN);
        } else {
            // Push default value and return (unreachable if all paths return, but needed for frame computation)
            if (function.getReturnType() == Integer.class || function.getReturnType() == Boolean.class) {
                _mv.visitInsn(ICONST_0);
                _mv.visitInsn(IRETURN);
            } else if (function.getReturnType() == Double.class) {
                _mv.visitInsn(DCONST_0);
                _mv.visitInsn(DRETURN);
            } else {
                _mv.visitInsn(ACONST_NULL);
                _mv.visitInsn(ARETURN);
            }
        }
    }

    // ========== Statement Emission ==========

    private void emitStatement(BoundStatement node) {
        emitLineNumber(node);
        switch (node.getType()) {
            case BlockStatement -> emitBlockStatement((BoundBlockStatement) node);
            case ExpressionStatement -> emitExpressionStatement((BoundExpressionStatement) node);
            case VariableDeclaration -> emitVariableDeclaration((BoundVariableDeclaration) node);
            case LabelStatement -> emitLabelStatement((BoundLabelStatement) node);
            case GotoStatement -> emitGotoStatement((BoundGotoStatement) node);
            case ConditionalGotoStatement -> emitConditionalGotoStatement((BoundConditionalGotoStatement) node);
            case ReturnStatement -> emitReturnStatement((BoundReturnStatement) node);
            case TryCatchStatement -> emitTryCatchStatement((BoundTryCatchStatement) node);
            case SendStatement -> emitSendStatement((BoundSendStatement) node);
            default -> throw new UnsupportedOperationException("Cannot emit statement: " + node.getType());
        }
    }

    private void emitBlockWithImplicitReturn(BoundBlockStatement block, Class<?> returnType) {
        var stmts = block.getStatements();
        for (int i = 0; i < stmts.size(); i++) {
            BoundStatement stmt = stmts.get(i);
            boolean isLast = (i == stmts.size() - 1);
            if (isLast && returnType != null && stmt instanceof BoundExpressionStatement exprStmt) {
                emitExpression(exprStmt.getExpression());
                _mv.visitInsn(getReturnInsn(returnType));
                return;
            }
            emitStatement(stmt);
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
        // For declarations: use identity check only. A new VariableSymbol that is not
        // the exact global instance is a local (possibly shadowing the global).
        if (!_inIsolatedMethod && _globalFields.contains(node.getVariable())) {
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

        // Defer visitTryCatchBlock — emit AFTER the try body so that nested
        // (inner) handlers appear first in the exception table. The JVM picks
        // the first matching handler, so inner must precede outer.

        // Try body
        _mv.visitLabel(tryStart);
        if (_tryCatchImplicitReturn && node.getTryBody() instanceof BoundBlockStatement tryBlock) {
            emitBlockWithImplicitReturn(tryBlock, _tryCatchReturnType);
        } else if (node.getTryBody() instanceof BoundBlockStatement tryBlock) {
            emitBlockStatement(tryBlock);
        } else {
            emitStatement(node.getTryBody());
        }
        _mv.visitLabel(tryEnd);

        // Now register the handler — after any inner handlers have been registered
        _mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Throwable");

        _mv.visitJumpInsn(GOTO, catchEnd);

        // Catch body - exception on stack
        _mv.visitLabel(catchStart);
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "getMessage", "()Ljava/lang/String;", false);
        int errorSlot = declareLocal(node.getErrorVariable());
        _mv.visitVarInsn(ASTORE, errorSlot);
        if (_tryCatchImplicitReturn && node.getCatchBody() instanceof BoundBlockStatement catchBlock) {
            emitBlockWithImplicitReturn(catchBlock, _tryCatchReturnType);
        } else if (node.getCatchBody() instanceof BoundBlockStatement catchBlock) {
            emitBlockStatement(catchBlock);
        } else {
            emitStatement(node.getCatchBody());
        }
        _mv.visitLabel(catchEnd);
    }

    private void emitReturnStatement(BoundReturnStatement node) {
        if (node.getExpression() != null) {
            emitExpression(node.getExpression());
            Class<?> exprType = node.getExpression().getClassType();
            // Coerce to declared return type if needed
            if (_currentReturnType != null && exprType == Object.class && _currentReturnType != Object.class) {
                if (_currentReturnType == Integer.class) emitUnboxIfNeeded(Integer.class);
                else if (_currentReturnType == Long.class) emitUnboxIfNeeded(Long.class);
                else if (_currentReturnType == Boolean.class) emitUnboxIfNeeded(Boolean.class);
                else if (_currentReturnType == Double.class) emitUnboxIfNeeded(Double.class);
                else {
                    String desc = getTypeDescriptor(_currentReturnType);
                    if (desc.startsWith("L") && desc.endsWith(";")) {
                        _mv.visitTypeInsn(CHECKCAST, desc.substring(1, desc.length() - 1));
                    }
                }
            }
            Class<?> retType = _currentReturnType != null ? _currentReturnType : exprType;
            _mv.visitInsn(getReturnInsn(retType));
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
            case MapLiteralExpression -> emitMapLiteralExpression((BoundMapLiteralExpression) node);
            case IndexExpression -> emitIndexExpression((BoundIndexExpression) node);
            case IndexAssignmentExpression -> emitIndexAssignmentExpression((BoundIndexAssignmentExpression) node);
            case StructLiteralExpression -> emitStructLiteralExpression((BoundStructLiteralExpression) node);
            case MemberAccessExpression -> emitMemberAccessExpression((BoundMemberAccessExpression) node);
            case MemberAssignmentExpression -> emitMemberAssignmentExpression((BoundMemberAssignmentExpression) node);
            case JavaMethodCallExpression -> emitJavaMethodCall((BoundJavaMethodCallExpression) node);
            case JavaStaticFieldExpression -> emitJavaStaticField((BoundJavaStaticFieldExpression) node);
            case CastExpression -> emitCastExpression((BoundCastExpression) node);
            case LambdaExpression -> emitLambdaCreation((BoundLambdaExpression) node);
            case ClosureCallExpression -> emitClosureCall((BoundClosureCallExpression) node);
            case ScopeExpression -> emitScope((BoundScopeExpression) node);
            case SpawnExpression -> emitSpawn((BoundSpawnExpression) node);
            case MatchExpression -> emitMatchExpression((BoundMatchExpression) node);
            case TryExpression -> emitTryExpression((BoundTryExpression) node);
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
        } else if (value instanceof Long l) {
            _mv.visitLdcInsn(l);
        } else if (value instanceof Double d) {
            _mv.visitLdcInsn(d);
        } else if (value instanceof String s) {
            _mv.visitLdcInsn(s);
        } else {
            throw new UnsupportedOperationException("Cannot emit literal: " + value.getClass());
        }
    }

    private void emitVariableExpression(BoundVariableExpression node) {
        emitVariableLoad(node.getVariable());
    }

    private void emitVariableLoad(VariableSymbol var) {
        if (isGlobalField(var)) {
            _mv.visitFieldInsn(GETSTATIC, _className, var.getName(), getTypeDescriptor(var.getType()));
            return;
        }
        int slot = getLocal(var);
        // Spawn captured variables are passed as Object - need ALOAD + unbox
        if (_spawnCapturedVars != null && isCapturedVar(var)) {
            _mv.visitVarInsn(ALOAD, slot);
            if (var.getType() == Integer.class) emitUnboxIfNeeded(Integer.class);
            else if (var.getType() == Long.class) emitUnboxIfNeeded(Long.class);
            else if (var.getType() == Double.class) emitUnboxIfNeeded(Double.class);
            else if (var.getType() == Boolean.class) emitUnboxIfNeeded(Boolean.class);
            else if (var.getType() == String.class) _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            else if (var.getType() == SiyoArray.class) _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoArray");
            else if (var.getType() == SiyoChannel.class) _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoChannel");
            // Other reference types stay as Object
            return;
        }
        emitLoad(var.getType(), slot);
    }

    private boolean isCapturedVar(VariableSymbol var) {
        if (_spawnCapturedVars == null) return false;
        for (VariableSymbol cv : _spawnCapturedVars) {
            if (cv.getName().equals(var.getName())) return true;
        }
        return false;
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
        if (isGlobalField(node.getVariable())) {
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
                // Unbox Object→boolean if operand is Object type (e.g. from invokedynamic)
                if (node.getOperand().getClassType() == Object.class) {
                    emitUnboxIfNeeded(Boolean.class);
                }
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

        // Short-circuit logical operators
        if (node.getOperator().getType() == BoundBinaryOperatorType.LogicalAnd) {
            Label falseLabel = new Label();
            Label endLabel = new Label();
            emitExpression(node.getLeft());
            if (node.getLeft().getClassType() == Object.class) emitUnboxIfNeeded(Boolean.class);
            _mv.visitJumpInsn(IFEQ, falseLabel);
            emitExpression(node.getRight());
            if (node.getRight().getClassType() == Object.class) emitUnboxIfNeeded(Boolean.class);
            _mv.visitJumpInsn(IFEQ, falseLabel);
            _mv.visitInsn(ICONST_1);
            _mv.visitJumpInsn(GOTO, endLabel);
            _mv.visitLabel(falseLabel);
            _mv.visitInsn(ICONST_0);
            _mv.visitLabel(endLabel);
            return;
        }
        if (node.getOperator().getType() == BoundBinaryOperatorType.LogicalOr) {
            Label trueLabel = new Label();
            Label endLabel = new Label();
            emitExpression(node.getLeft());
            if (node.getLeft().getClassType() == Object.class) emitUnboxIfNeeded(Boolean.class);
            _mv.visitJumpInsn(IFNE, trueLabel);
            emitExpression(node.getRight());
            if (node.getRight().getClassType() == Object.class) emitUnboxIfNeeded(Boolean.class);
            _mv.visitJumpInsn(IFNE, trueLabel);
            _mv.visitInsn(ICONST_0);
            _mv.visitJumpInsn(GOTO, endLabel);
            _mv.visitLabel(trueLabel);
            _mv.visitInsn(ICONST_1);
            _mv.visitLabel(endLabel);
            return;
        }

        // Determine result operand type for instruction selection
        Class<?> operandType = type;
        if (type == Object.class) operandType = node.getOperator().getLeftType();
        // For mixed int+long, result type is Long
        Class<?> resultType = node.getOperator().getResultType();
        if (resultType == Long.class) operandType = Long.class;

        emitExpression(node.getLeft());
        if (node.getLeft().getClassType() == Object.class) {
            emitUnboxIfNeeded(node.getOperator().getLeftType());
        }
        // Widen int → long if needed for mixed arithmetic
        if (operandType == Long.class && node.getLeft().getClassType() == Integer.class) {
            _mv.visitInsn(I2L);
        }
        emitExpression(node.getRight());
        if (node.getRight().getClassType() == Object.class) {
            emitUnboxIfNeeded(node.getOperator().getRightType());
        }
        if (operandType == Long.class && node.getRight().getClassType() == Integer.class) {
            _mv.visitInsn(I2L);
        }

        boolean isDouble = operandType == Double.class;
        boolean isLong = operandType == Long.class;
        switch (node.getOperator().getType()) {
            case Addition -> _mv.visitInsn(isDouble ? DADD : isLong ? LADD : IADD);
            case Subtraction -> _mv.visitInsn(isDouble ? DSUB : isLong ? LSUB : ISUB);
            case Multiplication -> _mv.visitInsn(isDouble ? DMUL : isLong ? LMUL : IMUL);
            case Division -> _mv.visitInsn(isDouble ? DDIV : isLong ? LDIV : IDIV);
            case Modulo -> _mv.visitInsn(isDouble ? DREM : isLong ? LREM : IREM);
            case BitwiseAnd -> _mv.visitInsn(IAND);
            case BitwiseOr -> _mv.visitInsn(IOR);
            case BitwiseXor -> _mv.visitInsn(IXOR);
            case LeftShift -> _mv.visitInsn(ISHL);
            case RightShift -> _mv.visitInsn(ISHR);
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

        // Determine comparison mode — use Long if either side is Long
        Class<?> type = leftType;
        if (leftType == Long.class || rightType == Long.class) type = Long.class;

        emitExpression(node.getLeft());
        if (type == Long.class && leftType == Integer.class) _mv.visitInsn(I2L);
        emitExpression(node.getRight());
        if (type == Long.class && rightType == Integer.class) _mv.visitInsn(I2L);

        Label trueLabel = new Label();
        Label endLabel = new Label();

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
        } else if (type == Long.class) {
            _mv.visitInsn(LCMP);
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
        } else if (type == String.class) {
            if (node.getOperator().getType() == BoundBinaryOperatorType.Equals
                    || node.getOperator().getType() == BoundBinaryOperatorType.NotEquals) {
                _mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                if (node.getOperator().getType() == BoundBinaryOperatorType.NotEquals) {
                    _mv.visitInsn(ICONST_1);
                    _mv.visitInsn(IXOR);
                }
                return;
            }
            // String ordering: compareTo → int, then compare to 0
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "compareTo",
                    "(Ljava/lang/String;)I", false);
            int jumpInsn = switch (node.getOperator().getType()) {
                case LessThan -> IFLT;
                case LessOrEqualsThan -> IFLE;
                case GreaterThan -> IFGT;
                case GreaterOrEqualsThen -> IFGE;
                default -> throw new UnsupportedOperationException();
            };
            _mv.visitJumpInsn(jumpInsn, trueLabel);
        } else {
            // Object reference equality
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

    private void emitSendStatement(BoundSendStatement node) {
        BoundExpression expr = node.getExpression();
        // The expression should be a call that would normally use actor.call()
        // We emit it as actor.send() instead (fire-and-forget)
        if (expr instanceof BoundCallExpression callExpr) {
            codeanalysis.FunctionSymbol function = callExpr.getFunction();
            if (function.getName().contains(".")
                    && function.getParameters().size() > 0
                    && function.getParameters().get(0).getName().equals("self")) {
                String typeName = function.getName().substring(0, function.getName().indexOf('.'));
                if (_actorTypeNames.contains(typeName)) {
                    String methodName = function.getName().substring(function.getName().indexOf('.') + 1);
                    emitExpression(callExpr.getArguments().get(0)); // actor handle
                    _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoActor");
                    _mv.visitLdcInsn(methodName);
                    int argCount = callExpr.getArguments().size() - 1;
                    _mv.visitLdcInsn(argCount);
                    _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                    for (int ai = 0; ai < argCount; ai++) {
                        _mv.visitInsn(DUP);
                        _mv.visitLdcInsn(ai);
                        emitExpression(callExpr.getArguments().get(ai + 1));
                        emitBoxIfNeeded(callExpr.getArguments().get(ai + 1).getClassType());
                        _mv.visitInsn(AASTORE);
                    }
                    _mv.visitMethodInsn(INVOKEVIRTUAL, "codeanalysis/SiyoActor", "send",
                            "(Ljava/lang/String;[Ljava/lang/Object;)V", false);
                    return;
                }
            }
        }
        // Also handle BoundJavaMethodCallExpression on Object-typed actor refs (invokedynamic path)
        if (expr instanceof BoundJavaMethodCallExpression javaCall && javaCall.getTarget() != null
                && javaCall.getResolvedSignature() == null) {
            // Object-typed actor ref: emit target, then call SiyoDynamic which auto-detects SiyoActor
            // For send, we need to call actor.send() directly
            emitExpression(javaCall.getTarget());
            // Check if it's SiyoActor at runtime and call send
            _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoActor");
            _mv.visitLdcInsn(javaCall.getMethodName());
            int argCount = javaCall.getArguments().size();
            _mv.visitLdcInsn(argCount);
            _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            for (int ai = 0; ai < argCount; ai++) {
                _mv.visitInsn(DUP);
                _mv.visitLdcInsn(ai);
                emitExpression(javaCall.getArguments().get(ai));
                emitBoxIfNeeded(javaCall.getArguments().get(ai).getClassType());
                _mv.visitInsn(AASTORE);
            }
            _mv.visitMethodInsn(INVOKEVIRTUAL, "codeanalysis/SiyoActor", "send",
                    "(Ljava/lang/String;[Ljava/lang/Object;)V", false);
            return;
        }
        // Fallback: just emit as a regular expression statement (non-actor call)
        emitExpression(expr);
        if (expr.getClassType() != null && expr.getClassType() != void.class) {
            // Long and Double take 2 stack slots
            if (expr.getClassType() == Long.class || expr.getClassType() == Double.class) {
                _mv.visitInsn(POP2);
            } else {
                _mv.visitInsn(POP);
            }
        }
    }

    private void emitTryExpression(BoundTryExpression node) {
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchStart = new Label();
        Label catchEnd = new Label();

        // Try body — emit statements, last expression value stays on stack
        _mv.visitLabel(tryStart);
        emitBlockLastAsValue(node.getTryBody());
        _mv.visitLabel(tryEnd);

        // Register handler AFTER try body so inner handlers appear first
        _mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Throwable");

        _mv.visitJumpInsn(GOTO, catchEnd);

        // Catch body — exception on stack, store error var
        _mv.visitLabel(catchStart);
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "getMessage", "()Ljava/lang/String;", false);
        int errorSlot = declareLocal(node.getErrorVariable());
        _mv.visitVarInsn(ASTORE, errorSlot);
        emitBlockLastAsValue(node.getCatchBody());
        _mv.visitLabel(catchEnd);
    }

    /** Emit a block where all statements are emitted normally except the last
     *  expression statement, which leaves its value on the stack (no POP). */
    private void emitBlockLastAsValue(BoundStatement body) {
        if (body instanceof BoundBlockStatement block) {
            var stmts = block.getStatements();
            for (int i = 0; i < stmts.size(); i++) {
                if (i == stmts.size() - 1 && stmts.get(i) instanceof BoundExpressionStatement exprStmt) {
                    emitExpression(exprStmt.getExpression());
                    return;
                }
                emitStatement(stmts.get(i));
            }
        }
        // Fallback: emit as statement, push null
        emitStatement(body);
        _mv.visitInsn(ACONST_NULL);
    }

    private void emitMatchExpression(BoundMatchExpression node) {
        // Evaluate target once, store in local
        emitExpression(node.getTarget());
        emitBoxIfNeeded(node.getTarget().getClassType());
        int targetSlot = _nextLocal++;
        _mv.visitVarInsn(ASTORE, targetSlot);

        Label endLabel = new Label();
        Label defaultLabel = null;
        BoundMatchExpression.BoundMatchArm defaultArm = null;

        for (var arm : node.getArms()) {
            if (arm.isDefault()) {
                defaultArm = arm;
                continue;
            }
            // Compare: target.equals(pattern)
            _mv.visitVarInsn(ALOAD, targetSlot);
            emitExpression(arm.pattern());
            emitBoxIfNeeded(arm.pattern().getClassType());
            _mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            Label nextArm = new Label();
            _mv.visitJumpInsn(IFEQ, nextArm); // not equal → skip

            // Match: emit pre-statements (lowered) then body, jump to end
            emitMatchArmPreStatements(arm.preStatements());
            emitExpression(arm.body());
            _mv.visitJumpInsn(GOTO, endLabel);
            _mv.visitLabel(nextArm);
        }

        // Default arm or null
        if (defaultArm != null) {
            emitMatchArmPreStatements(defaultArm.preStatements());
            emitExpression(defaultArm.body());
        } else {
            _mv.visitInsn(ACONST_NULL);
        }

        _mv.visitLabel(endLabel);
    }

    private void emitMatchArmPreStatements(java.util.List<BoundStatement> preStatements) {
        if (preStatements.isEmpty()) return;
        // Lower pre-statements (if/while/for → labels + gotos) before emitting
        BoundBlockStatement block = new BoundBlockStatement(new java.util.ArrayList<>(preStatements));
        BoundBlockStatement lowered = codeanalysis.lowering.Lowerer.lower(block);
        for (BoundStatement stmt : lowered.getStatements()) {
            emitStatement(stmt);
        }
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
        } else if (type == Long.class) {
            appendDesc = "(J)Ljava/lang/StringBuilder;";
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

    // ========== Lambda/Spawn Expression Emission ==========

    private void emitLambdaCreation(BoundLambdaExpression node) {
        int index = _lambdas.indexOf(node);
        if (index < 0) { _lambdas.add(node); index = _lambdas.size() - 1; }

        // Create Object[3] = {Integer(lambdaId), Object[]{captured}, String(className)}
        _mv.visitLdcInsn(3);
        _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

        // [0] = lambdaId
        _mv.visitInsn(DUP);
        _mv.visitLdcInsn(0);
        _mv.visitLdcInsn(index);
        _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        _mv.visitInsn(AASTORE);

        // [1] = captured array
        _mv.visitInsn(DUP);
        _mv.visitLdcInsn(1);
        int capturedSize = node.getCapturedVariables().size();
        _mv.visitLdcInsn(capturedSize);
        _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int ci = 0;
        for (VariableSymbol var : node.getCapturedVariables()) {
            _mv.visitInsn(DUP);
            _mv.visitLdcInsn(ci++);
            emitVariableLoad(var);
            emitBoxIfNeeded(var.getType());
            _mv.visitInsn(AASTORE);
        }
        _mv.visitInsn(AASTORE);

        // [2] = origin class name (for cross-module closure dispatch)
        _mv.visitInsn(DUP);
        _mv.visitLdcInsn(2);
        _mv.visitLdcInsn(_className);
        _mv.visitInsn(AASTORE);
        // Stack: Object[3] (the closure representation)
    }

    private void emitClosureCall(BoundClosureCallExpression node) {
        // closure is Object[3] = {Integer(lambdaId), Object[]{captured}, String(className)}
        emitExpression(node.getClosure());
        int closureLocal = _nextLocal++;
        _mv.visitVarInsn(ASTORE, closureLocal);

        // Extract lambdaId
        _mv.visitVarInsn(ALOAD, closureLocal);
        _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        _mv.visitLdcInsn(0);
        _mv.visitInsn(AALOAD);
        _mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        int lambdaIdLocal = _nextLocal++;
        _mv.visitVarInsn(ISTORE, lambdaIdLocal);

        // Extract captured array
        _mv.visitVarInsn(ALOAD, closureLocal);
        _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        _mv.visitLdcInsn(1);
        _mv.visitInsn(AALOAD);
        _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        int capturedLocal = _nextLocal++;
        _mv.visitVarInsn(ASTORE, capturedLocal);

        // Build args array
        int argCount = node.getArguments().size();
        _mv.visitLdcInsn(argCount);
        _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < argCount; i++) {
            _mv.visitInsn(DUP);
            _mv.visitLdcInsn(i);
            emitExpression(node.getArguments().get(i));
            emitBoxIfNeeded(node.getArguments().get(i).getClassType());
            _mv.visitInsn(AASTORE);
        }
        int argsLocal = _nextLocal++;
        _mv.visitVarInsn(ASTORE, argsLocal);

        // Check if closure has origin class info (cross-module dispatch)
        // closure[2] contains the class name string if present
        _mv.visitVarInsn(ALOAD, closureLocal);
        _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        _mv.visitInsn(ARRAYLENGTH);
        _mv.visitLdcInsn(3);
        Label sameClassLabel = new Label();
        _mv.visitJumpInsn(IF_ICMPLT, sameClassLabel);

        // Cross-module: use SiyoRuntime.dispatchClosure
        _mv.visitVarInsn(ALOAD, closureLocal);
        _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        _mv.visitLdcInsn(2);
        _mv.visitInsn(AALOAD);
        _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        _mv.visitVarInsn(ILOAD, lambdaIdLocal);
        _mv.visitVarInsn(ALOAD, capturedLocal);
        _mv.visitVarInsn(ALOAD, argsLocal);
        _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoRuntime", "dispatchClosure",
                "(Ljava/lang/String;I[Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        Label endLabel = new Label();
        _mv.visitJumpInsn(GOTO, endLabel);

        // Same-class: direct static call (fast path)
        _mv.visitLabel(sameClassLabel);
        _mv.visitVarInsn(ILOAD, lambdaIdLocal);
        _mv.visitVarInsn(ALOAD, capturedLocal);
        _mv.visitVarInsn(ALOAD, argsLocal);
        _mv.visitMethodInsn(INVOKESTATIC, _className, "closureDispatch$",
                "(I[Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);

        _mv.visitLabel(endLabel);
    }

    private void emitScope(BoundScopeExpression node) {
        // Create thread list: ArrayList<Thread>
        _mv.visitTypeInsn(NEW, "java/util/ArrayList");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        int threadsLocal = _nextLocal++;
        _mv.visitVarInsn(ASTORE, threadsLocal);

        // Save threads list local for spawn to use
        int savedScopeThreadsLocal = _scopeThreadsLocal;
        _scopeThreadsLocal = threadsLocal;

        // Emit body — lowering happens per-statement to preserve spawn variable mappings
        if (node.getBody() instanceof BoundBlockStatement) {
            BoundBlockStatement block = (BoundBlockStatement) node.getBody();
            for (BoundStatement stmt : block.getStatements()) {
                if (stmt.getType() == BoundNodeType.ForStatement) {
                    // Lower for-in/for loops before emission
                    BoundBlockStatement lowered = codeanalysis.lowering.Lowerer.lower(stmt);
                    emitBlockStatement(lowered);
                } else {
                    emitStatement(stmt);
                }
            }
        } else {
            emitStatement(node.getBody());
        }

        // Join all threads
        _mv.visitVarInsn(ALOAD, threadsLocal);
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        Label loopStart = new Label();
        Label loopEnd = new Label();
        _mv.visitLabel(loopStart);
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        _mv.visitJumpInsn(IFEQ, loopEnd);
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        _mv.visitTypeInsn(CHECKCAST, "java/lang/Thread");
        _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "join", "()V", false);
        _mv.visitJumpInsn(GOTO, loopStart);
        _mv.visitLabel(loopEnd);
        _mv.visitInsn(POP); // pop iterator

        _scopeThreadsLocal = savedScopeThreadsLocal;
    }

    private int _scopeThreadsLocal = -1;

    private void emitSpawn(BoundSpawnExpression node) {
        // Actor spawn: spawn Actor.new() → create SiyoActor
        if (node.getActorTypeName() != null) {
            emitActorSpawn(node);
            return;
        }

        int index = _spawns.indexOf(node);
        if (index < 0) { _spawns.add(node); index = _spawns.size() - 1; }
        // Create Runnable that calls spawn$N with captured vars
        // Use an Object[] to pass captured vars
        int capturedSize = node.getCapturedVariables().size();

        // Build captured array
        _mv.visitLdcInsn(capturedSize);
        _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        int ci = 0;
        for (VariableSymbol var : node.getCapturedVariables()) {
            _mv.visitInsn(DUP);
            _mv.visitLdcInsn(ci++);
            emitVariableLoad(var);
            emitBoxIfNeeded(var.getType());
            _mv.visitInsn(AASTORE);
        }
        int capturedLocal = _nextLocal++;
        _mv.visitVarInsn(ASTORE, capturedLocal);

        // Use INVOKEDYNAMIC + LambdaMetafactory to create Runnable
        // The Runnable will call spawn$N with unpacked captured vars from the array
        // But LambdaMetafactory needs a direct method reference, not runtime dispatch.
        // Simplest correct approach: generate a wrapper static method that takes Object[]
        // and dispatches to the right spawn$N, then use INVOKEDYNAMIC for that.

        // Actually simplest: generate spawn$dispatch$N(Object[]) for each spawn
        // and use INVOKEDYNAMIC to wrap it as Runnable
        _mv.visitVarInsn(ALOAD, capturedLocal);
        // INVOKEDYNAMIC: create Runnable from spawn$wrap$N(Object[])
        org.objectweb.asm.Handle bootstrap = new org.objectweb.asm.Handle(
                H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false);
        org.objectweb.asm.Handle implHandle = new org.objectweb.asm.Handle(
                H_INVOKESTATIC, _className, "spawn$wrap$" + index,
                "([Ljava/lang/Object;)V", false);
        _mv.visitInvokeDynamicInsn("run", "([Ljava/lang/Object;)Ljava/lang/Runnable;",
                bootstrap,
                org.objectweb.asm.Type.getType("()V"),
                implHandle,
                org.objectweb.asm.Type.getType("()V"));
        _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "startVirtualThread",
                "(Ljava/lang/Runnable;)Ljava/lang/Thread;", false);

        // Add to scope's thread list
        if (_scopeThreadsLocal >= 0) {
            _mv.visitInsn(DUP);
            int threadLocal = _nextLocal++;
            _mv.visitVarInsn(ASTORE, threadLocal);
            _mv.visitVarInsn(ALOAD, _scopeThreadsLocal);
            _mv.visitVarInsn(ALOAD, threadLocal);
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
            _mv.visitInsn(POP); // discard boolean
        } else {
            _mv.visitInsn(POP); // discard thread if no scope
        }
    }

    private void emitActorSpawn(BoundSpawnExpression node) {
        // Evaluate the constructor expression (body is a single ExprStmt)
        BoundStatement bodyStmt = node.getBody().getStatements().get(0);
        if (bodyStmt instanceof BoundExpressionStatement exprStmt) {
            emitExpression(exprStmt.getExpression()); // SiyoStruct on stack
        }
        // Stack: SiyoStruct (actor state)
        // Create SiyoActor: new SiyoActor(state, actorTypeName)
        int stateSlot = _nextLocal++;
        _mv.visitVarInsn(ASTORE, stateSlot);
        _mv.visitTypeInsn(NEW, "codeanalysis/SiyoActor");
        _mv.visitInsn(DUP);
        _mv.visitVarInsn(ALOAD, stateSlot);
        _mv.visitTypeInsn(CHECKCAST, "java/util/LinkedHashMap");
        _mv.visitLdcInsn(node.getActorTypeName());
        _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoActor", "<init>",
                "(Ljava/util/LinkedHashMap;Ljava/lang/String;)V", false);
        // Stack: SiyoActor
        // Start event loop: Actor$startEventLoop(actor, className)
        // We need a static helper method that starts the virtual thread
        _mv.visitInsn(DUP);
        _mv.visitLdcInsn(_className);
        _mv.visitMethodInsn(INVOKESTATIC, _className, "$actorStart",
                "(Lcodeanalysis/SiyoActor;Ljava/lang/String;)V", false);
        _needsActorStart = true;
        // SiyoActor remains on stack
    }

    private boolean _needsActorStart = false;
    private final java.util.Set<String> _actorTypeNames = new java.util.HashSet<>();

    public void registerActorType(String name) { _actorTypeNames.add(name); }

    private void emitActorStartMethod(ClassWriter cw) {
        // $actorStart(SiyoActor, String className) → void
        // Delegates to SiyoActor.startEventLoop(actor, className)
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "$actorStart",
                "(Lcodeanalysis/SiyoActor;Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0); // actor
        mv.visitVarInsn(ALOAD, 1); // className
        mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoActor", "startEventLoop",
                "(Lcodeanalysis/SiyoActor;Ljava/lang/String;)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @SuppressWarnings("unused")
    private void emitSpawnWrapperMethods(ClassWriter cw) {
        for (int i = 0; i < _spawns.size(); i++) {
            BoundSpawnExpression spawn = _spawns.get(i);
            if (spawn.getActorTypeName() != null) continue;
            // spawn$wrap$N(Object[] captured) -> void
            // Unpacks captured array and calls spawn$N(captured0, captured1, ...)
            _mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "spawn$wrap$" + i,
                    "([Ljava/lang/Object;)V", null, null);
            _mv.visitCode();

            int ci = 0;
            for (VariableSymbol var : spawn.getCapturedVariables()) {
                _mv.visitVarInsn(ALOAD, 0); // captured array
                _mv.visitLdcInsn(ci++);
                _mv.visitInsn(AALOAD);
            }

            // Build descriptor for spawn$N
            StringBuilder desc = new StringBuilder("(");
            for (int c = 0; c < spawn.getCapturedVariables().size(); c++) {
                desc.append("Ljava/lang/Object;");
            }
            desc.append(")V");

            _mv.visitMethodInsn(INVOKESTATIC, _className, "spawn$" + i, desc.toString(), false);
            _mv.visitInsn(RETURN);
            _mv.visitMaxs(0, 0);
            _mv.visitEnd();
        }
    }

    // ========== Lambda/Spawn Method Emission ==========

    private void collectLambdasAndSpawns(BoundNode node) {
        if (node instanceof BoundLambdaExpression lambda) {
            if (!_lambdas.contains(lambda)) _lambdas.add(lambda);
            collectLambdasAndSpawns(lambda.getBody());
        }
        if (node instanceof BoundSpawnExpression spawn) {
            if (!_spawns.contains(spawn)) _spawns.add(spawn);
            collectLambdasAndSpawns(spawn.getBody());
        }
        for (var it = node.getChildren(); it.hasNext(); ) {
            collectLambdasAndSpawns(it.next());
        }
    }

    private void emitLambdaMethod(ClassWriter cw, int index, BoundLambdaExpression lambda) {
        // Method signature: lambda$N(captured0, captured1, ..., param0, param1, ...) -> Object
        StringBuilder desc = new StringBuilder("(");
        for (VariableSymbol captured : lambda.getCapturedVariables()) {
            desc.append("Ljava/lang/Object;");
        }
        for (ParameterSymbol param : lambda.getParameters()) {
            desc.append(getTypeDescriptor(param.getType()));
        }
        desc.append(")");
        desc.append(lambda.getReturnType() != null ? getTypeDescriptor(lambda.getReturnType()) : "V");

        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "lambda$" + index, desc.toString(), null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 0;
        boolean savedInMain = _inMainMethod;
        _inMainMethod = false;
        _inIsolatedMethod = true;

        // Map captured variables to local slots (passed as Object)
        java.util.Set<VariableSymbol> capturedSet = lambda.getCapturedVariables();
        for (VariableSymbol captured : capturedSet) {
            _locals.put(captured, _nextLocal++);
        }
        // Map parameters to local slots
        for (ParameterSymbol param : lambda.getParameters()) {
            _locals.put(param, _nextLocal);
            _nextLocal += (param.getType() == Double.class || param.getType() == Long.class) ? 2 : 1;
        }

        // Track captured vars so emitVariableLoad uses ALOAD + unbox
        _spawnCapturedVars = capturedSet;

        // Emit body
        FunctionSymbol tempFunc = new FunctionSymbol("lambda$" + index, lambda.getParameters(), lambda.getReturnType());
        emitFunctionBody(lambda.getBody(), tempFunc);
        _spawnCapturedVars = null;
        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
        _inMainMethod = savedInMain;
        _inIsolatedMethod = false;
    }

    private void emitSpawnMethod(ClassWriter cw, int index, BoundSpawnExpression spawn) {
        // spawn$N(captured0, captured1, ...) -> void
        StringBuilder desc = new StringBuilder("(");
        for (VariableSymbol captured : spawn.getCapturedVariables()) {
            desc.append("Ljava/lang/Object;");
        }
        desc.append(")V");

        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "spawn$" + index, desc.toString(), null, null);
        _mv.visitCode();
        _locals.clear();
        _labels.clear();
        _nextLocal = 0;
        boolean savedInMain = _inMainMethod;
        _inMainMethod = false;

        // Captured variables come in as Object parameters - store and unbox on use
        java.util.Set<VariableSymbol> capturedSet = spawn.getCapturedVariables();
        for (VariableSymbol captured : capturedSet) {
            int slot = _nextLocal++;
            _locals.put(captured, slot);
            // Mark as Object for proper load instruction (ALOAD not ILOAD)
            // Create a wrapper variable with Object type for the local slot
        }
        _spawnCapturedVars = capturedSet;
        _inIsolatedMethod = true;

        emitBlockStatement(spawn.getBody());
        _spawnCapturedVars = null;
        _inIsolatedMethod = false;
        _mv.visitInsn(RETURN);
        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
        _inMainMethod = savedInMain;
        _inIsolatedMethod = false;
    }

    private void emitClosureDispatch(ClassWriter cw) {
        // closureDispatch(int lambdaId, Object[] captured, Object[] args) -> Object
        _mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "closureDispatch$",
                "(I[Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        _mv.visitCode();

        Label defaultLabel = new Label();
        Label[] labels = new Label[_lambdas.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = new Label();

        _mv.visitVarInsn(ILOAD, 0); // lambdaId
        _mv.visitTableSwitchInsn(0, _lambdas.size() - 1, defaultLabel, labels);

        for (int i = 0; i < _lambdas.size(); i++) {
            _mv.visitLabel(labels[i]);
            BoundLambdaExpression lambda = _lambdas.get(i);

            // Push captured vars from array
            int argIdx = 0;
            for (VariableSymbol captured : lambda.getCapturedVariables()) {
                _mv.visitVarInsn(ALOAD, 1); // captured array
                _mv.visitLdcInsn(argIdx++);
                _mv.visitInsn(AALOAD);
            }
            // Push params from args array
            int paramIdx = 0;
            for (ParameterSymbol param : lambda.getParameters()) {
                _mv.visitVarInsn(ALOAD, 2); // args array
                _mv.visitLdcInsn(paramIdx++);
                _mv.visitInsn(AALOAD);
                emitUnboxIfNeeded(param.getType());
            }

            // Build descriptor
            StringBuilder desc = new StringBuilder("(");
            for (int c = 0; c < lambda.getCapturedVariables().size(); c++) desc.append("Ljava/lang/Object;");
            for (ParameterSymbol param : lambda.getParameters()) desc.append(getTypeDescriptor(param.getType()));
            desc.append(")");
            desc.append(lambda.getReturnType() != null ? getTypeDescriptor(lambda.getReturnType()) : "V");

            _mv.visitMethodInsn(INVOKESTATIC, _className, "lambda$" + i, desc.toString(), false);

            if (lambda.getReturnType() != null) {
                emitBoxIfNeeded(lambda.getReturnType());
                _mv.visitInsn(ARETURN);
            } else {
                _mv.visitInsn(ACONST_NULL);
                _mv.visitInsn(ARETURN);
            }
        }

        _mv.visitLabel(defaultLabel);
        _mv.visitInsn(ACONST_NULL);
        _mv.visitInsn(ARETURN);
        _mv.visitMaxs(0, 0);
        _mv.visitEnd();
    }

    // ========== Java Interop Emission ==========

    private void emitCastExpression(BoundCastExpression node) {
        emitExpression(node.getExpression());
        _mv.visitTypeInsn(CHECKCAST, node.getTargetClassInfo().getInternalName());
    }

    private void emitJavaStaticField(BoundJavaStaticFieldExpression node) {
        _mv.visitFieldInsn(GETSTATIC, node.getClassInfo().getInternalName(),
                node.getFieldName(), node.getFieldDescriptor());
    }

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
            emitJavaReturnConversion(sig);
            return;
        }

        // Instance method call
        emitExpression(node.getTarget());
        if (sig != null) {
            // Cast Object on stack to the actual Java class
            _mv.visitTypeInsn(CHECKCAST, sig.getOwnerInternalName());
            emitJavaArgs(node.getArguments(), sig);
            _mv.visitMethodInsn(sig.getInvokeOpcode(), sig.getOwnerInternalName(), sig.getName(), sig.getDescriptor(), sig.isInterface());
            emitJavaReturnConversion(sig);
        } else {
            // Unresolved instance method → invokedynamic dispatch
            // Target object already on stack, push args
            for (BoundExpression arg : node.getArguments()) {
                emitExpression(arg);
                emitBoxIfNeeded(arg.getClassType());
            }
            // invokedynamic: SiyoDynamic.bootstrap resolves at runtime
            // CallSite type: (Object target, Object... args) → Object
            StringBuilder desc = new StringBuilder("(Ljava/lang/Object;"); // target
            for (int i = 0; i < node.getArguments().size(); i++) {
                desc.append("Ljava/lang/Object;");
            }
            desc.append(")Ljava/lang/Object;");
            org.objectweb.asm.Handle bootstrap = new org.objectweb.asm.Handle(
                    H_INVOKESTATIC, "codeanalysis/SiyoDynamic", "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false);
            _mv.visitInvokeDynamicInsn(node.getMethodName(), desc.toString(), bootstrap);
        }
    }

    /** Convert Java return types to Siyo types on the stack */
    private void emitJavaReturnConversion(JavaMethodSignature sig) {
        String ret = sig.getReturnDescriptor();
        if (ret.startsWith("[")) {
            // Java array → SiyoArray via static helper
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoArray", "fromJavaArray",
                    "(Ljava/lang/Object;)Lcodeanalysis/SiyoArray;", false);
        }
        else if (ret.equals("J")) {} // long stays as long (native Siyo type now)
        else if (ret.equals("F")) _mv.visitInsn(F2D);      // float → double
        else if (ret.equals("B") || ret.equals("S")) {} // byte/short already widened to int by JVM
        else if (ret.equals("C")) _mv.visitInsn(I2C);      // keep as int (char → int)
    }

    private void emitJavaArgs(java.util.List<BoundExpression> arguments, JavaMethodSignature sig) {
        String[] paramDescs = sig.getParamDescriptors();
        for (int i = 0; i < arguments.size(); i++) {
            emitExpression(arguments.get(i));
            Class<?> argType = arguments.get(i).getClassType();
            String paramDesc = i < paramDescs.length ? paramDescs[i] : "Ljava/lang/Object;";

            if (argType == Integer.class && paramDesc.equals("I")) {
                // int → int
            } else if (argType == Long.class && paramDesc.equals("J")) {
                // long → long
            } else if (argType == Integer.class && paramDesc.equals("J")) {
                _mv.visitInsn(I2L); // int → long
            } else if (argType == Integer.class && paramDesc.equals("D")) {
                _mv.visitInsn(I2D); // int → double
            } else if (argType == Integer.class && paramDesc.equals("F")) {
                _mv.visitInsn(I2F); // int → float
            } else if (argType == Boolean.class && paramDesc.equals("Z")) {
                // bool → bool
            } else if (argType == Double.class && paramDesc.equals("D")) {
                // double → double
            } else if (argType == Double.class && paramDesc.equals("F")) {
                _mv.visitInsn(D2F); // double → float
            } else if (paramDesc.startsWith("L") || paramDesc.startsWith("[")) {
                // Reference type parameter
                if (argType == Integer.class || argType == Long.class || argType == Boolean.class || argType == Double.class) {
                    emitBoxIfNeeded(argType); // box primitive to Object
                }
                // Cast to expected type if not Object
                if (!paramDesc.equals("Ljava/lang/Object;") && !paramDesc.equals("Ljava/lang/String;") && argType == Object.class) {
                    String castType = paramDesc.startsWith("[") ? paramDesc : paramDesc.substring(1, paramDesc.length() - 1);
                    _mv.visitTypeInsn(CHECKCAST, castType);
                }
            }
        }
    }

    // ========== Composite Type Emission ==========

    private void emitArrayLiteralExpression(BoundArrayLiteralExpression node) {
        // Create temp ArrayList with elements
        _mv.visitTypeInsn(NEW, "java/util/ArrayList");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

        for (BoundExpression element : node.getElements()) {
            _mv.visitInsn(DUP);
            emitExpression(element);
            emitBoxIfNeeded(element.getClassType());
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
            _mv.visitInsn(POP);
        }

        // Wrap in SiyoArray for type consistency
        int tempListSlot = _nextLocal++;
        _mv.visitVarInsn(ASTORE, tempListSlot);
        _mv.visitTypeInsn(NEW, "codeanalysis/SiyoArray");
        _mv.visitInsn(DUP);
        _mv.visitVarInsn(ALOAD, tempListSlot);
        _mv.visitLdcInsn(org.objectweb.asm.Type.getType(Object.class));
        _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoArray", "<init>", "(Ljava/util/List;Ljava/lang/Class;)V", false);
        // SiyoArray on stack
    }

    private void emitMapLiteralExpression(BoundMapLiteralExpression node) {
        _mv.visitTypeInsn(NEW, "codeanalysis/SiyoMap");
        _mv.visitInsn(DUP);
        _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoMap", "<init>", "()V", false);

        for (int i = 0; i < node.getKeys().size(); i++) {
            _mv.visitInsn(DUP);
            emitExpression(node.getKeys().get(i));
            emitBoxIfNeeded(node.getKeys().get(i).getClassType());
            emitExpression(node.getValues().get(i));
            emitBoxIfNeeded(node.getValues().get(i).getClassType());
            _mv.visitMethodInsn(INVOKEVIRTUAL, "codeanalysis/SiyoMap", "set",
                    "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        }
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
        if (targetType == Object.class) {
            // Dynamic target (e.g. actor method return) — cast to List at runtime
            _mv.visitTypeInsn(CHECKCAST, "java/util/List");
        }
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
            // read line using shared static Scanner
            _mv.visitFieldInsn(GETSTATIC, _className, "$scanner", "Ljava/util/Scanner;");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
            _needsScanner = true;
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
            Class<?> argType = arg.getClassType();
            if (argType == String.class) {
                _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            } else if (argType == SiyoArray.class) {
                _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
            } else {
                // Object type - runtime dispatch: check if String or List
                int tempSlot = _nextLocal++;
                _mv.visitVarInsn(ASTORE, tempSlot);
                _mv.visitVarInsn(ALOAD, tempSlot);
                _mv.visitTypeInsn(INSTANCEOF, "java/lang/String");
                Label notString = new Label();
                Label done = new Label();
                _mv.visitJumpInsn(IFEQ, notString);
                _mv.visitVarInsn(ALOAD, tempSlot);
                _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                _mv.visitJumpInsn(GOTO, done);
                _mv.visitLabel(notString);
                _mv.visitVarInsn(ALOAD, tempSlot);
                _mv.visitTypeInsn(CHECKCAST, "java/util/List");
                _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
                _mv.visitLabel(done);
            }
            return;
        }

        if (function == BuiltinFunctions.PARSE_INT) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoRuntime", "safeParseInt",
                    "(Ljava/lang/String;)I", false);
            return;
        }
        if (function == BuiltinFunctions.PARSE_FLOAT) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoRuntime", "safeParseDouble",
                    "(Ljava/lang/String;)D", false);
            return;
        }
        if (function == BuiltinFunctions.TO_INT) {
            emitExpression(node.getArguments().get(0));
            _mv.visitInsn(D2I);
            return;
        }
        if (function == BuiltinFunctions.TO_INT_STR) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoRuntime", "safeParseInt",
                    "(Ljava/lang/String;)I", false);
            return;
        }
        if (function == BuiltinFunctions.PARSE_LONG) {
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J", false);
            return;
        }
        if (function == BuiltinFunctions.TO_LONG) {
            emitExpression(node.getArguments().get(0));
            _mv.visitInsn(I2L);
            return;
        }
        if (function == BuiltinFunctions.TO_FLOAT || function == BuiltinFunctions.TO_DOUBLE) {
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
        if (function == BuiltinFunctions.MAP_KEYS) {
            emitCoerceArg(node.getArguments().get(0), SiyoMap.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "codeanalysis/SiyoMap", "keys",
                    "()Lcodeanalysis/SiyoArray;", false);
            return;
        }
        if (function == BuiltinFunctions.NEW_MAP) {
            _mv.visitTypeInsn(NEW, "codeanalysis/SiyoMap");
            _mv.visitInsn(DUP);
            _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoMap", "<init>", "()V", false);
            return;
        }
        if (function == BuiltinFunctions.NEW_SET) {
            _mv.visitTypeInsn(NEW, "codeanalysis/SiyoSet");
            _mv.visitInsn(DUP);
            _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoSet", "<init>", "()V", false);
            return;
        }
        if (function == BuiltinFunctions.CHANNEL) {
            _mv.visitTypeInsn(NEW, "codeanalysis/SiyoChannel");
            _mv.visitInsn(DUP);
            _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoChannel", "<init>", "()V", false);
            return;
        }
        if (function == BuiltinFunctions.CHANNEL_BUFFERED) {
            _mv.visitTypeInsn(NEW, "codeanalysis/SiyoChannel");
            _mv.visitInsn(DUP);
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoChannel", "<init>", "(I)V", false);
            return;
        }
        if (function == BuiltinFunctions.RANDOM) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/util/concurrent/ThreadLocalRandom", "current",
                    "()Ljava/util/concurrent/ThreadLocalRandom;", false);
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ThreadLocalRandom", "nextInt",
                    "(I)I", false);
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
        if (function == BuiltinFunctions.REMOVE_AT) {
            emitExpression(node.getArguments().get(0)); // list
            emitExpression(node.getArguments().get(1)); // index
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "remove", "(I)Ljava/lang/Object;", true);
            _mv.visitInsn(POP); // discard removed element
            return;
        }
        if (function == BuiltinFunctions.POP) {
            emitExpression(node.getArguments().get(0)); // list
            _mv.visitInsn(DUP);
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
            _mv.visitInsn(ICONST_1);
            _mv.visitInsn(ISUB);
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "remove", "(I)Ljava/lang/Object;", true);
            return;
        }
        if (function == BuiltinFunctions.SORT) {
            emitExpression(node.getArguments().get(0)); // list
            emitExpression(node.getArguments().get(1)); // comparator (closure)
            _needsSortHelper = true;
            _mv.visitMethodInsn(INVOKESTATIC, _className, "$sort",
                    "(Ljava/util/List;Ljava/lang/Object;)V", false);
            return;
        }
        if (function == BuiltinFunctions.SUBSTRING) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), Integer.class);
            emitCoerceArg(node.getArguments().get(2), Integer.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.CONTAINS) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            return;
        }

        if (function == BuiltinFunctions.CHR) {
            emitCoerceArg(node.getArguments().get(0), Integer.class);
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "toString", "(I)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.ORD) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitInsn(ICONST_0);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            return;
        }
        if (function == BuiltinFunctions.INDEX_OF) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false);
            return;
        }
        if (function == BuiltinFunctions.STARTS_WITH) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
            return;
        }
        if (function == BuiltinFunctions.ENDS_WITH) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
            return;
        }
        if (function == BuiltinFunctions.REPLACE) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            emitCoerceArg(node.getArguments().get(2), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.TRIM) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.TO_UPPER) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "()Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.TO_LOWER) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.SPLIT) {
            emitCoerceArg(node.getArguments().get(0), String.class);
            emitCoerceArg(node.getArguments().get(1), String.class);
            _mv.visitMethodInsn(INVOKESTATIC, "java/util/regex/Pattern", "quote", "(Ljava/lang/String;)Ljava/lang/String;", false);
            _mv.visitInsn(ICONST_M1);
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;I)[Ljava/lang/String;", false);
            // String[] → List → SiyoArray
            _mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
            int listSlot = _nextLocal++;
            _mv.visitVarInsn(ASTORE, listSlot);
            _mv.visitTypeInsn(NEW, "codeanalysis/SiyoArray");
            _mv.visitInsn(DUP);
            _mv.visitVarInsn(ALOAD, listSlot);
            _mv.visitLdcInsn(org.objectweb.asm.Type.getType(String.class));
            _mv.visitMethodInsn(INVOKESPECIAL, "codeanalysis/SiyoArray", "<init>", "(Ljava/util/List;Ljava/lang/Class;)V", false);
            return;
        }

        if (function == BuiltinFunctions.ACTOR_HANDLE) {
            // Extract __handle__ from the self Map
            emitExpression(node.getArguments().get(0));
            _mv.visitTypeInsn(CHECKCAST, "java/util/Map");
            _mv.visitLdcInsn("__handle__");
            _mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            return;
        }
        if (function == BuiltinFunctions.CAN_READ) {
            emitExpression(node.getArguments().get(0));
            _mv.visitTypeInsn(CHECKCAST, "java/io/Reader");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/Reader", "ready", "()Z", false);
            return;
        }
        if (function == BuiltinFunctions.HTTP_GET) {
            emitExpression(node.getArguments().get(0));
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoHttp", "get",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }
        if (function == BuiltinFunctions.HTTP_POST) {
            emitExpression(node.getArguments().get(0));
            emitExpression(node.getArguments().get(1));
            _mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoHttp", "post",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }

        // Actor method interception: TypeName.method(self, args) → self.call("method", args)
        // Skip when calling own type's methods from within the same actor (self is raw struct)
        if (function.getName().contains(".")
                && function.getParameters().size() > 0
                && function.getParameters().get(0).getName().equals("self")) {
            String typeName = function.getName().substring(0, function.getName().indexOf('.'));
            if (_actorTypeNames.contains(typeName) && !typeName.equals(_currentActorTypeName)) {
                String methodName = function.getName().substring(function.getName().indexOf('.') + 1);
                // Emit: firstArg.call(methodName, [otherArgs])
                emitExpression(node.getArguments().get(0)); // actor handle (SiyoActor on stack)
                _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoActor");
                _mv.visitLdcInsn(methodName);
                // Build args array (without self)
                int argCount = node.getArguments().size() - 1;
                _mv.visitLdcInsn(argCount);
                _mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                for (int ai = 0; ai < argCount; ai++) {
                    _mv.visitInsn(DUP);
                    _mv.visitLdcInsn(ai);
                    emitExpression(node.getArguments().get(ai + 1));
                    emitBoxIfNeeded(node.getArguments().get(ai + 1).getClassType());
                    _mv.visitInsn(AASTORE);
                }
                _mv.visitMethodInsn(INVOKEVIRTUAL, "codeanalysis/SiyoActor", "call",
                        "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                // Result is Object — unbox if needed
                Class<?> retType = function.getReturnType();
                if (retType == Integer.class) emitUnboxIfNeeded(Integer.class);
                else if (retType == Long.class) emitUnboxIfNeeded(Long.class);
                else if (retType == Double.class) emitUnboxIfNeeded(Double.class);
                else if (retType == Boolean.class) emitUnboxIfNeeded(Boolean.class);
                else if (retType == String.class) _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                return;
            }
        }

        // User-defined functions: push args and invoke static method
        for (int ai = 0; ai < node.getArguments().size(); ai++) {
            BoundExpression arg = node.getArguments().get(ai);
            // Type coercion: if arg is Object but param expects a specific type, cast
            if (ai < function.getParameters().size()) {
                Class<?> paramType = function.getParameters().get(ai).getType();
                emitCoerceArg(arg, paramType);
            } else {
                emitExpression(arg);
            }
        }
        String owner = function.getModuleName() != null ? function.getModuleName() : _className;
        String descriptor = getFunctionDescriptor(function);
        String methodName;
        if (function.getModuleName() != null && function.getName().contains(".")) {
            // Module function: "collections.arrayToString" → "arrayToString" (in module's class)
            methodName = function.getName().substring(function.getName().lastIndexOf('.') + 1);
        } else {
            // Local or impl function: "User.greet" → "User$greet"
            methodName = function.getName().replace('.', '$');
        }
        _mv.visitMethodInsn(INVOKESTATIC, owner, methodName, descriptor, false);
    }

    // ========== Helpers ==========

    private void emitLineNumber(BoundNode node) {
        if (_sourceText == null || _mv == null) return;
        int offset = node.getSourceOffset();
        if (offset < 0) return;
        int line = _sourceText.getLineIndex(offset) + 1; // 1-based
        if (line == _lastEmittedLine) return; // avoid duplicate entries
        _lastEmittedLine = line;
        Label lineLabel = new Label();
        _mv.visitLabel(lineLabel);
        _mv.visitLineNumber(line, lineLabel);
    }

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
        } else if (type == Long.class) {
            _mv.visitVarInsn(LLOAD, slot);
        } else if (type == Double.class) {
            _mv.visitVarInsn(DLOAD, slot);
        } else {
            _mv.visitVarInsn(ALOAD, slot);
        }
    }

    private void emitStore(Class<?> type, int slot) {
        if (type == Integer.class || type == Boolean.class) {
            _mv.visitVarInsn(ISTORE, slot);
        } else if (type == Long.class) {
            _mv.visitVarInsn(LSTORE, slot);
        } else if (type == Double.class) {
            _mv.visitVarInsn(DSTORE, slot);
        } else {
            _mv.visitVarInsn(ASTORE, slot);
        }
    }

    private int getReturnInsn(Class<?> type) {
        if (type == Integer.class || type == Boolean.class) return IRETURN;
        if (type == Long.class) return LRETURN;
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

    private boolean _inIsolatedMethod = false; // true in spawn/lambda methods

    private boolean isGlobalField(VariableSymbol var) {
        if (_inIsolatedMethod) return false;
        // If this exact variable is already declared as a local, it shadows the global
        if (_locals.containsKey(var)) return false;
        // Use reference equality first (same VariableSymbol instance)
        if (_globalFields.contains(var)) return true;
        // For main method, also try name-based (lowered vars may have different instances)
        if (_inMainMethod) {
            for (VariableSymbol g : _globalFields) {
                if (g.getName().equals(var.getName())) {
                    // But not if a local with the same name exists (shadowing)
                    for (VariableSymbol local : _locals.keySet()) {
                        if (local.getName().equals(var.getName())) return false;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private int getLocal(VariableSymbol variable) {
        Integer slot = _locals.get(variable);
        if (slot != null) return slot;
        // Name-based fallback (different VariableSymbol instances with same name)
        for (var entry : _locals.entrySet()) {
            if (entry.getKey().getName().equals(variable.getName())) {
                return entry.getValue();
            }
        }
        // Parent spawn propagation: variable is captured by outer spawn,
        // inner spawn needs to access it from the outer spawn method's locals
        if (_spawnCapturedVars != null) {
            for (VariableSymbol cap : _spawnCapturedVars) {
                if (cap.getName().equals(variable.getName())) {
                    // Find the slot by name since identity may differ
                    for (var entry : _locals.entrySet()) {
                        if (entry.getKey().getName().equals(variable.getName())) {
                            _locals.put(variable, entry.getValue()); // cache for next lookup
                            return entry.getValue();
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Variable not declared: " + variable.getName());
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
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        for (BoundStatement stmt : block.getStatements()) {
            if (stmt instanceof BoundVariableDeclaration varDecl) {
                String name = varDecl.getVariable().getName();
                // Don't treat synthetic lowered variables as globals
                if (!name.startsWith("_idx") && !name.startsWith("_col") && !name.startsWith("_ch")) {
                    // Only the first declaration of each name is global (outermost scope).
                    // Subsequent same-name declarations are inner-scope shadows.
                    if (seenNames.add(name)) {
                        _globalFields.add(varDecl.getVariable());
                    }
                }
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

    /**
     * Generates: static void $sort(List list, Object closureObj)
     * Calls closureDispatch$ with the closure's lambdaId and captured vars for each comparison.
     */
    private void emitSortHelper(ClassWriter cw) {
        // $sort(List, Object) where Object is Object[]{lambdaId, captured[]}
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "$sort",
                "(Ljava/util/List;Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        // list.sort((a, b) -> { Object[] closure = (Object[]) closureObj;
        //   int id = (Integer) closure[0]; Object[] cap = (Object[]) closure[1];
        //   return (Integer) closureDispatch$(id, cap, new Object[]{a, b}); })
        mv.visitVarInsn(ALOAD, 0); // list
        mv.visitVarInsn(ALOAD, 1); // closure obj
        // Use invokedynamic or inner class approach - simplest: use SiyoRuntime helper
        // Actually, use lambda metafactory to create Comparator
        // Simplest correct: call a static helper that does the sorting via reflection
        mv.visitMethodInsn(INVOKESTATIC, "codeanalysis/SiyoRuntime", "sortList",
                "(Ljava/util/List;Ljava/lang/Object;)V", false);
        mv.visitInsn(RETURN);
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
        } else if (type == Long.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (type == Double.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
            _mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else if (type == String.class) {
            _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        }
        // Object.class and others: leave as Object on stack
    }

    /**
     * Emit an argument expression and coerce it from Object to the expected type if needed.
     * This is the generalized replacement for the old per-builtin CHECKCAST logic.
     */
    private void emitCoerceArg(BoundExpression arg, Class<?> expectedType) {
        emitExpression(arg);
        Class<?> argType = arg.getClassType();
        if (argType == expectedType || expectedType == Object.class) return;
        if (argType == Object.class) {
            if (expectedType == Integer.class) emitUnboxIfNeeded(Integer.class);
            else if (expectedType == Long.class) emitUnboxIfNeeded(Long.class);
            else if (expectedType == Double.class) emitUnboxIfNeeded(Double.class);
            else if (expectedType == Boolean.class) emitUnboxIfNeeded(Boolean.class);
            else if (expectedType == String.class) _mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            else if (expectedType == SiyoArray.class) _mv.visitTypeInsn(CHECKCAST, "java/util/List");
            else if (expectedType == SiyoMap.class) _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoMap");
            else if (expectedType == SiyoSet.class) _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoSet");
            else if (expectedType == SiyoChannel.class) _mv.visitTypeInsn(CHECKCAST, "codeanalysis/SiyoChannel");
            else if (expectedType == SiyoStruct.class) _mv.visitTypeInsn(CHECKCAST, "java/util/LinkedHashMap");
            else if (expectedType == SiyoClosure.class) _mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        }
    }

    private void emitBoxIfNeeded(Class<?> type) {
        if (type == Integer.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (type == Boolean.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (type == Long.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (type == Double.class) {
            _mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }
    }

    private String getTypeDescriptor(Class<?> type) {
        if (type == Integer.class) return "I";
        if (type == Long.class) return "J";
        if (type == Boolean.class) return "Z";
        if (type == Double.class) return "D";
        if (type == String.class) return "Ljava/lang/String;";
        if (type == SiyoArray.class) return "Ljava/util/List;";
        if (type == SiyoStruct.class) return "Ljava/util/Map;";
        if (type == SiyoMap.class) return "Lcodeanalysis/SiyoMap;";
        if (type == SiyoSet.class) return "Lcodeanalysis/SiyoSet;";
        if (type == SiyoChannel.class) return "Lcodeanalysis/SiyoChannel;";
        return "Ljava/lang/Object;";
    }
}
