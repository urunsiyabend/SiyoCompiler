package codeanalysis.binding;

import codeanalysis.ModuleRegistry;
import codeanalysis.ModuleSymbol;
import codeanalysis.BuiltinFunctions;
import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.LabelSymbol;
import codeanalysis.ParameterSymbol;
import codeanalysis.SiyoArray;
import codeanalysis.SiyoStruct;
import codeanalysis.StructSymbol;
import codeanalysis.VariableSymbol;
import codeanalysis.syntax.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * The `Binder` class is responsible for binding expression syntax to bound expressions.
 * It performs type checking and generates diagnostic messages for any binding errors.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Binder {
    DiagnosticBox _diagnostics = new DiagnosticBox();
    private BoundScope _scope;
    private FunctionSymbol _currentFunction = null;
    private final Map<FunctionSymbol, BoundBlockStatement> _functionBodies = new HashMap<>();
    private final Stack<LoopLabels> _loopStack = new Stack<>();
    private final Map<String, StructSymbol> _structTypes = new HashMap<>();
    private final Map<VariableSymbol, VariableTypeInfo> _typeInfo = new HashMap<>();
    private final Map<String, Map<String, Integer>> _enumTypes = new HashMap<>();
    private final java.util.Set<String> _importedModules = new java.util.HashSet<>();
    private ModuleRegistry _registry;
    private String _filePath;
    private final Map<String, codeanalysis.JavaClassInfo> _javaClasses = new HashMap<>();

    private void trackArrayType(VariableSymbol var, Class<?> elementType) {
        _typeInfo.put(var, VariableTypeInfo.forArray(elementType));
    }
    private void trackArrayType(VariableSymbol var, Class<?> elementType, StructSymbol structType) {
        _typeInfo.put(var, VariableTypeInfo.forArray(elementType, structType));
    }
    private void trackStructType(VariableSymbol var, StructSymbol structType) {
        _typeInfo.put(var, VariableTypeInfo.forStruct(structType));
    }
    private Class<?> getArrayElementType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getArrayElementType() : null;
    }
    private StructSymbol getVarStructType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getStructType() : null;
    }
    private StructSymbol getArrayStructElementType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getArrayElementStructType() : null;
    }
    private int _labelCounter = 0;

    private LabelSymbol generateLabel(String prefix) {
        return new LabelSymbol(prefix + _labelCounter++);
    }

    private record LoopLabels(LabelSymbol breakLabel, LabelSymbol continueLabel) {}

    /**
     * Initializes a new instance of the Binder class with the specified variables.
     *
     * @param parent The parent scope.
     */
    public Binder(BoundScope parent) {
        _scope = new BoundScope(parent);

        for (FunctionSymbol builtin : BuiltinFunctions.getAll()) {
            _scope.tryDeclareFunction(builtin);
        }
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax) {
        return bindGlobalScope(previous, syntax, new ModuleRegistry(), null);
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax, ModuleRegistry registry, String filePath) {
        var parentScope = createParentScopes(previous);
        Binder binder = new Binder(parentScope);
        binder._registry = registry;
        binder._filePath = filePath;
        BoundStatement statement = binder.bindStatement(syntax.getStatement());
        Iterable<FunctionSymbol> functions = binder._scope.getDeclaredFunctions();
        Map<FunctionSymbol, BoundBlockStatement> functionBodies = binder._functionBodies;
        Iterable<VariableSymbol> variables = binder._scope.getDeclaredVariables();
        DiagnosticBox diagnostics = binder._diagnostics;
        return new BoundGlobalScope(previous, diagnostics, functions, functionBodies, variables, statement);
    }

    private static BoundScope createParentScopes(BoundGlobalScope previous) {
        Stack<BoundGlobalScope> scopeStack = new Stack<>();
        while (previous != null) {
            scopeStack.push(previous);
            previous = previous.getPrevious();
        }
        BoundScope parent = null;

        while (scopeStack.size() > 0) {
            previous = scopeStack.pop();
            BoundScope scope = new BoundScope(parent);
            for (FunctionSymbol function : previous.getFunctionSymbols()) {
                scope.tryDeclareFunction(function);
            }
            for (VariableSymbol variable : previous.getVariableSymbols()) {
                scope.tryDeclare(variable);
            }
            parent = scope;
        }
        return parent;
    }

    /**
     * Binds the given expression syntax and returns the corresponding bound statement.
     *
     * @param syntax The statement syntax to bind.
     * @return The bound statement.
     */
    private BoundStatement bindStatement(StatementSyntax syntax) {
        return switch (syntax.getType()) {
            case BlockStatement -> bindBlockStatement((BlockStatementSyntax)syntax);
            case ExpressionStatement -> bindExpressionStatement((ExpressionStatementSyntax)syntax);
            case VariableDeclaration -> bindVariableDeclaration((VariableDeclarationSyntax)syntax);
            case IfStatement -> bindIfStatement((IfStatementSyntax)syntax);
            case WhileStatement -> bindWhileStatement((WhileStatementSyntax)syntax);
            case ForStatement -> bindForStatement((ForStatementSyntax)syntax);
            case FunctionDeclaration -> bindFunctionDeclaration((FunctionDeclarationSyntax)syntax);
            case ReturnStatement -> bindReturnStatement((ReturnStatementSyntax)syntax);
            case ForInStatement -> bindForInStatement((ForInStatementSyntax)syntax);
            case BreakStatement -> bindBreakStatement((BreakStatementSyntax)syntax);
            case ContinueStatement -> bindContinueStatement((ContinueStatementSyntax)syntax);
            case StructDeclaration -> bindStructDeclaration((StructDeclarationSyntax)syntax);
            case EnumDeclaration -> bindEnumDeclaration((EnumDeclarationSyntax)syntax);
            case TryCatchStatement -> bindTryCatchStatement((TryCatchStatementSyntax)syntax);
            case ImportStatement -> bindImportStatement((ImportStatementSyntax)syntax);
            case JavaImportStatement -> bindJavaImportStatement((JavaImportStatementSyntax)syntax);
            default -> throw new RuntimeException("Unexpected syntax type " + syntax.getType());
        };
    }

    /**
     * Binds the block statement syntax and returns the corresponding bound block statement.
     *
     * @param syntax The block statement syntax to bind.
     * @return The bound block statement.
     */
    private BoundStatement bindBlockStatement(BlockStatementSyntax syntax) {
        ArrayList<BoundStatement> statements = new ArrayList<>();
        _scope = new BoundScope(_scope);

        // First pass: process imports, register function/struct/enum declarations
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            if (statementSyntax instanceof ImportStatementSyntax importSyntax) {
                bindImportStatement(importSyntax);
            } else if (statementSyntax instanceof JavaImportStatementSyntax javaImportSyntax) {
                bindJavaImportStatement(javaImportSyntax);
            } else if (statementSyntax instanceof FunctionDeclarationSyntax funcSyntax) {
                registerFunctionDeclaration(funcSyntax);
            } else if (statementSyntax instanceof StructDeclarationSyntax structSyntax) {
                registerStructDeclaration(structSyntax);
            } else if (statementSyntax instanceof EnumDeclarationSyntax enumSyntax) {
                registerEnumDeclaration(enumSyntax);
            }
        }

        // Second pass: bind all statements (function bodies can now reference each other)
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }

        _scope = _scope.getParent();
        return new BoundBlockStatement(statements);
    }

    /**
     * Registers a function declaration in the current scope without binding the body.
     * This enables forward declarations.
     */
    private void registerFunctionDeclaration(FunctionDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();

        // Only skip if already declared in current scope (not parent)
        // This allows shadowing built-in functions
        if (_scope.hasDeclaredFunction(name)) {
            return;
        }

        List<ParameterSymbol> parameters = new ArrayList<>();
        for (ParameterSyntax parameterSyntax : syntax.getParameters()) {
            String parameterName = parameterSyntax.getIdentifier().getData();
            String typeName = parameterSyntax.getTypeToken().getData();
            Class<?> parameterType = lookupType(typeName);
            if (parameterType == null) parameterType = Integer.class;
            parameters.add(new ParameterSymbol(parameterName, parameterType));
        }

        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            returnType = lookupType(syntax.getTypeClause().getIdentifier().getData());
        }

        FunctionSymbol function = new FunctionSymbol(name, parameters, returnType);
        _scope.tryDeclareFunction(function);
    }

    /**
     * Binds the expression statement syntax and returns the corresponding bound expression statement.
     *
     * @param syntax The expression statement syntax to bind.
     * @return The bound expression statement.
     */
    private void registerStructDeclaration(StructDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_structTypes.containsKey(name)) return;

        java.util.LinkedHashMap<String, Class<?>> fields = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> fieldTypeNames = new java.util.LinkedHashMap<>();
        for (ParameterSyntax field : syntax.getFields()) {
            String fieldName = field.getIdentifier().getData();
            String typeName = field.getTypeToken().getData();
            Class<?> fieldType = lookupType(typeName);
            if (fieldType == null) fieldType = Integer.class;
            fields.put(fieldName, fieldType);
            fieldTypeNames.put(fieldName, typeName);
        }
        _structTypes.put(name, new StructSymbol(name, fields, fieldTypeNames));
    }

    private BoundStatement bindExpressionStatement(ExpressionStatementSyntax syntax) {
        BoundExpression expression = bindExpression(syntax.getExpression());
        return new BoundExpressionStatement(expression);
    }

    /**
     * Binds the variable declaration syntax and returns the corresponding bound variable declaration.
     *
     * @param syntax The variable declaration syntax to bind.
     * @return The bound variable declaration.
     */
    private BoundStatement bindVariableDeclaration(VariableDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        var isReadOnly = syntax.getKeyword().getType() == SyntaxType.ImmutableKeyword;
        BoundExpression initializer = bindExpression(syntax.getInitializer());
        VariableSymbol variableSymbol = new VariableSymbol(name, isReadOnly, initializer.getClassType());

        if (!_scope.tryDeclare(variableSymbol)) {
            _diagnostics.reportVariableAlreadyDeclared(syntax.getIdentifier().getSpan(), name);
        }

        // Track element/struct types for type resolution
        if (initializer instanceof BoundArrayLiteralExpression arr) {
            if (!arr.getElements().isEmpty() && arr.getElements().get(0) instanceof BoundStructLiteralExpression structLit) {
                trackArrayType(variableSymbol, arr.getElementType(), structLit.getStructType());
            } else {
                trackArrayType(variableSymbol, arr.getElementType());
            }
        } else if (initializer instanceof BoundStructLiteralExpression structLit) {
            trackStructType(variableSymbol, structLit.getStructType());
        }

        return new BoundVariableDeclaration(variableSymbol, initializer);
    }

    /**
     * Binds the if statement syntax and returns the corresponding bound if statement.
     *
     * @param syntax The if statement syntax to bind.
     * @return The bound if statement.
     */
    private BoundStatement bindIfStatement(IfStatementSyntax syntax) {
        BoundExpression condition = bindExpression(syntax.getCondition(), Boolean.class);
        BoundStatement thenStatement = bindStatement(syntax.getThenStatement());
        BoundStatement elseStatement = syntax.getElseClause() == null ? null : bindStatement(syntax.getElseClause().getElseStatement());
        return new BoundIfStatement(condition, thenStatement, elseStatement);
    }

    /**
     * Binds the while statement syntax and returns the corresponding bound while statement.
     *
     * @param syntax The while statement syntax to bind.
     * @return The bound while statement.
     */
    private BoundStatement bindWhileStatement(WhileStatementSyntax syntax) {
        BoundExpression condition = bindExpression(syntax.getCondition(), Boolean.class);
        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.pop();
        return new BoundWhileStatement(condition, body, breakLabel, continueLabel);
    }

    /**
     * Binds the for statement syntax and returns the corresponding bound for statement.
     *
     * @param syntax The for statement syntax to bind.
     * @return The bound for statement.
     */
    private BoundStatement bindForStatement(ForStatementSyntax syntax) {
        BoundStatement initializer = bindStatement(syntax.getInitializer());
        BoundExpression condition = bindExpression(syntax.getCondition(), Boolean.class);
        BoundExpression iterator = bindExpression(syntax.getIterator());
        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.pop();
        return new BoundForStatement(initializer, condition, iterator, body, breakLabel, continueLabel);
    }

    public BoundExpression bindExpression(ExpressionSyntax syntax, Class<?> expectedType) {
        BoundExpression boundExpression = bindExpression(syntax);
        if (boundExpression.getClassType() != expectedType
                && boundExpression.getClassType() != Object.class) {
            _diagnostics.reportCannotConvert(syntax.getSpan(), boundExpression.getClassType(), expectedType);
        }
        return boundExpression;
    }

    /**
     * Binds the given expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    public BoundExpression bindExpression(ExpressionSyntax syntax) {
        return switch (syntax.getType()) {
            case ParenthesizedExpression -> bindParenthesizedExpression((ParanthesizedExpressionSyntax) syntax);
            case LiteralExpression -> bindLiteralExpression((LiteralExpressionSyntax) syntax);
            case NameExpression -> bindNameExpression((NameExpressionSyntax) syntax);
            case AssignmentExpression -> bindAssignmentExpression((AssignmentExpressionSyntax) syntax);
            case UnaryExpression -> bindUnaryExpression((UnaryExpressionSyntax) syntax);
            case BinaryExpression -> bindBinaryExpression((BinaryExpressionSyntax) syntax);
            case CallExpression -> bindCallExpression((CallExpressionSyntax) syntax);
            case ArrayLiteralExpression -> bindArrayLiteralExpression((ArrayLiteralExpressionSyntax) syntax);
            case IndexExpression -> bindIndexExpression((IndexExpressionSyntax) syntax);
            case MemberAccessExpression -> bindMemberAccessExpression((MemberAccessExpressionSyntax) syntax);
            case StructLiteralExpression -> bindStructLiteralExpression((StructLiteralExpressionSyntax) syntax);
            case CompoundAssignmentExpression -> bindCompoundAssignment((CompoundAssignmentExpressionSyntax) syntax);
            case MemberCallExpression -> bindMemberCallExpression((MemberCallExpressionSyntax) syntax);
            default -> {
                _diagnostics.reportUnexpectedExpression(syntax.getSpan(), syntax.getType());
                yield new BoundLiteralExpression(0);
            }
        };
    }

    /**
     * Gets an iterator over the diagnostic messages generated during binding.
     *
     * @return An iterator over the diagnostic messages.
     */
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Binds the given expression syntax to a bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindLiteralExpression(LiteralExpressionSyntax syntax) {
        Object value = syntax.getValue();
        if (value == null && syntax.getLiteralToken().getType() == SyntaxType.NullKeyword) {
            return new BoundLiteralExpression(null);
        }
        if (value == null) {
            value = 0;
        }
        return new BoundLiteralExpression(value);
    }


    /**
     * Binds unary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound unary expression.
     */
    private BoundExpression bindUnaryExpression(UnaryExpressionSyntax syntax) {
        BoundExpression boundOperand = bindExpression(syntax.getOperand());
        BoundUnaryOperator boundOperator = BoundUnaryOperator.bind(syntax.getOperator().getType(), boundOperand.getClassType());
        if (boundOperator == null) {
            _diagnostics.reportUndefinedUnaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), boundOperand.getClassType());
            return boundOperand;
        }
        return new BoundUnaryExpression(boundOperator, boundOperand);
    }

    /**
     * Binds binary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound binary expression.
     */
    private BoundExpression bindBinaryExpression(BinaryExpressionSyntax syntax) {
        BoundExpression boundLeft = bindExpression(syntax.getLeft());
        BoundExpression boundRight = bindExpression(syntax.getRight());
        Class<?> leftType = boundLeft.getClassType();
        Class<?> rightType = boundRight.getClassType();

        // When one side is Object (e.g., from member access), try matching with the other side's type
        BoundBinaryOperator boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), leftType, rightType);
        if (boundOperator == null && leftType == Object.class && rightType != Object.class) {
            boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), rightType, rightType);
        }
        if (boundOperator == null && rightType == Object.class && leftType != Object.class) {
            boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), leftType, leftType);
        }

        if (boundOperator == null) {
            _diagnostics.reportUndefinedBinaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), leftType, rightType);
            return boundLeft;
        }
        return new BoundBinaryExpression(boundLeft, boundOperator, boundRight);
    }

    /**
     * Binds a parenthesized expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindParenthesizedExpression(ParanthesizedExpressionSyntax syntax) {
        return bindExpression(syntax.getExpression());
    }

    /**
     * Binds a name expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindNameExpression(NameExpressionSyntax syntax) {
        String name = syntax.getIdentifierToken().getData();
        if (name == null || name.equals("")) {
            return new BoundLiteralExpression(0);
        }

        boolean hasVariable = _scope.tryLookup(name);

        if (!hasVariable) {
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return new BoundLiteralExpression(0);
        }
        var variable  = _scope.lookupVariable(name);
        return new BoundVariableExpression(variable);
    }

    /**
     * Binds an assignment expression syntax to a bound expression.
     *
     * @param syntax The assignment expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindAssignmentExpression(AssignmentExpressionSyntax syntax) {
        String name = syntax.getIdentifierToken().getData();
        BoundExpression boundExpression = bindExpression(syntax.getExpressionSyntax());
        VariableSymbol variable;

        if (!_scope.tryLookup(name)) {
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return boundExpression;
        }
        else {
            variable = _scope.lookupVariable(name);
        }

        if (variable.isReadOnly()) {
            _diagnostics.reportCannotAssign(syntax.getEqualsToken().getSpan(), name);
            return boundExpression;
        }

        if (boundExpression.getClassType() != variable.getType()
                && boundExpression.getClassType() != Object.class
                && variable.getType() != Object.class) {
            _diagnostics.reportCannotConvert(syntax.getExpressionSyntax().getSpan(), boundExpression.getClassType(), variable.getType());
            return boundExpression;
        }

        return new BoundAssignmentExpression(variable, boundExpression);
    }

    /**
     * Binds a function declaration syntax to a bound statement.
     *
     * @param syntax The function declaration syntax to bind.
     * @return A bound expression statement (functions are bound but not executed during declaration).
     */
    private BoundStatement bindFunctionDeclaration(FunctionDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();

        // Parse parameters
        List<ParameterSymbol> parameters = new ArrayList<>();
        HashSet<String> seenParameterNames = new HashSet<>();

        for (ParameterSyntax parameterSyntax : syntax.getParameters()) {
            String parameterName = parameterSyntax.getIdentifier().getData();
            String typeName = parameterSyntax.getTypeToken().getData();
            Class<?> parameterType = lookupType(typeName);

            if (parameterType == null) {
                _diagnostics.reportUndefinedType(parameterSyntax.getTypeToken().getSpan(), typeName);
                parameterType = Integer.class; // Default to int for error recovery
            }

            if (!seenParameterNames.add(parameterName)) {
                _diagnostics.reportDuplicateParameter(parameterSyntax.getIdentifier().getSpan(), parameterName);
            } else {
                ParameterSymbol parameter = new ParameterSymbol(parameterName, parameterType);
                parameters.add(parameter);
            }
        }

        // Parse return type
        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            String returnTypeName = syntax.getTypeClause().getIdentifier().getData();
            returnType = lookupType(returnTypeName);
            if (returnType == null) {
                _diagnostics.reportUndefinedType(syntax.getTypeClause().getIdentifier().getSpan(), returnTypeName);
            }
        }

        // Use pre-registered function symbol if available, otherwise create and declare
        FunctionSymbol function;
        if (_scope.tryLookupFunction(name)) {
            function = _scope.lookupFunction(name);
        } else {
            function = new FunctionSymbol(name, parameters, returnType);
            if (!_scope.tryDeclareFunction(function)) {
                _diagnostics.reportFunctionAlreadyDeclared(syntax.getIdentifier().getSpan(), name);
            }
        }

        // Bind the function body in a new scope with parameters
        _scope = new BoundScope(_scope);
        FunctionSymbol previousFunction = _currentFunction;
        _currentFunction = function;

        // Add the function's own parameter symbols to scope (important for consistency with evaluator)
        int paramIdx = 0;
        for (ParameterSymbol parameter : function.getParameters()) {
            _scope.tryDeclare(parameter);
            // Track array element types from parameter type names
            if (paramIdx < syntax.getParameters().getCount()) {
                String typeName = syntax.getParameters().get(paramIdx).getTypeToken().getData();
                Class<?> elemType = lookupElementType(typeName);
                if (elemType != null) {
                    trackArrayType(parameter, elemType);
                }
                // Track struct types from parameter type names
                String baseTypeName = typeName.endsWith("[]") ? typeName.substring(0, typeName.length() - 2) : typeName;
                StructSymbol structSym = _structTypes.get(baseTypeName);
                if (structSym != null && parameter.getType() == SiyoStruct.class) {
                    trackStructType(parameter, structSym);
                }
            }
            paramIdx++;
        }

        // Bind the body statements
        ArrayList<BoundStatement> statements = new ArrayList<>();
        for (StatementSyntax statementSyntax : syntax.getBody().getStatements()) {
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }
        BoundBlockStatement boundBody = new BoundBlockStatement(statements);

        // Store the function body for evaluation
        _functionBodies.put(function, boundBody);

        _currentFunction = previousFunction;
        _scope = _scope.getParent();

        // Return a placeholder statement (function declarations don't produce values)
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /**
     * Binds a return statement syntax to a bound return statement.
     *
     * @param syntax The return statement syntax to bind.
     * @return The bound return statement.
     */
    private BoundStatement bindReturnStatement(ReturnStatementSyntax syntax) {
        BoundExpression expression = syntax.getExpression() == null ? null : bindExpression(syntax.getExpression());

        if (_currentFunction == null) {
            _diagnostics.reportReturnOutsideFunction(syntax.getReturnKeyword().getSpan());
            return new BoundReturnStatement(expression);
        }

        if (_currentFunction.getReturnType() == null) {
            // Void function
            if (expression != null) {
                _diagnostics.reportReturnWithValueInVoidFunction(syntax.getExpression().getSpan());
            }
        } else {
            // Non-void function
            if (expression == null) {
                _diagnostics.reportMissingReturnValue(syntax.getReturnKeyword().getSpan(), _currentFunction.getReturnType());
            } else if (expression.getClassType() != _currentFunction.getReturnType()) {
                _diagnostics.reportReturnTypeMismatch(syntax.getExpression().getSpan(), expression.getClassType(), _currentFunction.getReturnType());
            }
        }

        return new BoundReturnStatement(expression);
    }

    /**
     * Binds a call expression syntax to a bound call expression.
     *
     * @param syntax The call expression syntax to bind.
     * @return The bound call expression.
     */
    private BoundExpression bindCallExpression(CallExpressionSyntax syntax) {
        String name = syntax.getIdentifier().getData();

        if (!_scope.tryLookupFunction(name)) {
            _diagnostics.reportUndefinedFunction(syntax.getIdentifier().getSpan(), name);
            return new BoundLiteralExpression(0);
        }

        FunctionSymbol function = _scope.lookupFunction(name);

        // Bind arguments
        List<BoundExpression> boundArguments = new ArrayList<>();
        for (ExpressionSyntax argumentSyntax : syntax.getArguments()) {
            BoundExpression boundArgument = bindExpression(argumentSyntax);
            boundArguments.add(boundArgument);
        }

        // Check argument count
        if (boundArguments.size() != function.getParameters().size()) {
            _diagnostics.reportWrongArgumentCount(syntax.getSpan(), name, function.getParameters().size(), boundArguments.size());
            return new BoundLiteralExpression(0);
        }

        // Check argument types
        for (int i = 0; i < boundArguments.size(); i++) {
            BoundExpression argument = boundArguments.get(i);
            ParameterSymbol parameter = function.getParameters().get(i);

            // Object.class accepts any type (used by built-in functions like toString)
            if (parameter.getType() != Object.class && argument.getClassType() != Object.class && argument.getClassType() != parameter.getType()) {
                _diagnostics.reportWrongArgumentType(syntax.getArguments().get(i).getSpan(), parameter.getName(), parameter.getType(), argument.getClassType());
            }
        }

        return new BoundCallExpression(function, boundArguments);
    }

    /**
     * Looks up a type by name.
     *
     * @param name The type name.
     * @return The Class representing the type, or null if not found.
     */
    private BoundStatement bindForInStatement(ForInStatementSyntax syntax) {
        BoundExpression collection = bindExpression(syntax.getCollection());
        String itemName = syntax.getItemName().getData();
        int uid = _labelCounter++; // unique id to avoid variable name collisions

        // Create index and collection variables with unique names
        VariableSymbol indexVar = new VariableSymbol("_idx" + uid, false, Integer.class);
        _scope = new BoundScope(_scope);
        _scope.tryDeclare(indexVar);

        BoundVariableDeclaration initializer = new BoundVariableDeclaration(indexVar, new BoundLiteralExpression(0));

        VariableSymbol collectionVar = new VariableSymbol("_col" + uid, true, collection.getClassType());
        _scope.tryDeclare(collectionVar);
        BoundVariableDeclaration collectionDecl = new BoundVariableDeclaration(collectionVar, collection);

        // Condition: _i < len(_col)
        BoundExpression condition = new BoundBinaryExpression(
                new BoundVariableExpression(indexVar),
                BoundBinaryOperator.bind(codeanalysis.syntax.SyntaxType.LessToken, Integer.class, Integer.class),
                new BoundCallExpression(BuiltinFunctions.LEN, java.util.List.of(new BoundVariableExpression(collectionVar)))
        );

        // Iterator: _i = _i + 1
        BoundExpression increment = new BoundAssignmentExpression(indexVar,
                new BoundBinaryExpression(
                        new BoundVariableExpression(indexVar),
                        BoundBinaryOperator.bind(codeanalysis.syntax.SyntaxType.PlusToken, Integer.class, Integer.class),
                        new BoundLiteralExpression(1)
                )
        );

        // Body: { mut item = _col[_i]; original body }
        // Resolve element type from the original collection expression
        Class<?> elementType = resolveArrayElementType(collection);
        StructSymbol structType = resolveStructTypeFromCollection(collection);
        if (structType != null) {
            trackArrayType(collectionVar, elementType, structType);
        } else {
            trackArrayType(collectionVar, elementType);
        }
        VariableSymbol itemVar = new VariableSymbol(itemName, false, elementType);
        _scope = new BoundScope(_scope);
        _scope.tryDeclare(itemVar);
        // Track struct type if element is a struct
        if (elementType == SiyoStruct.class && structType != null) {
            trackStructType(itemVar, structType);
        }

        BoundIndexExpression indexAccess = new BoundIndexExpression(
                new BoundVariableExpression(collectionVar),
                new BoundVariableExpression(indexVar),
                elementType
        );
        BoundVariableDeclaration itemDecl = new BoundVariableDeclaration(itemVar, indexAccess);

        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));

        BoundStatement boundBody = bindStatement(syntax.getBody());

        _loopStack.pop();
        _scope = _scope.getParent();

        ArrayList<BoundStatement> bodyStatements = new ArrayList<>();
        bodyStatements.add(itemDecl);
        if (boundBody instanceof BoundBlockStatement block) {
            bodyStatements.addAll(block.getStatements());
        } else {
            bodyStatements.add(boundBody);
        }
        BoundBlockStatement fullBody = new BoundBlockStatement(bodyStatements);

        BoundForStatement forStmt = new BoundForStatement(initializer, condition, increment, fullBody, breakLabel, continueLabel);

        ArrayList<BoundStatement> outerStatements = new ArrayList<>();
        outerStatements.add(collectionDecl);
        outerStatements.add(forStmt);

        _scope = _scope.getParent();
        return new BoundBlockStatement(outerStatements);
    }

    private BoundStatement bindBreakStatement(BreakStatementSyntax syntax) {
        if (_loopStack.isEmpty()) {
            _diagnostics.reportBreakOutsideLoop(syntax.getKeyword().getSpan());
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }
        return new BoundBreakStatement(_loopStack.peek().breakLabel());
    }

    private BoundStatement bindContinueStatement(ContinueStatementSyntax syntax) {
        if (_loopStack.isEmpty()) {
            _diagnostics.reportContinueOutsideLoop(syntax.getKeyword().getSpan());
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }
        return new BoundContinueStatement(_loopStack.peek().continueLabel());
    }

    private BoundExpression bindArrayLiteralExpression(ArrayLiteralExpressionSyntax syntax) {
        List<BoundExpression> boundElements = new ArrayList<>();
        Class<?> elementType = null;

        for (ExpressionSyntax element : syntax.getElements()) {
            BoundExpression boundElement = bindExpression(element);
            boundElements.add(boundElement);
            if (elementType == null) {
                elementType = boundElement.getClassType();
            } else if (elementType != boundElement.getClassType()) {
                _diagnostics.reportCannotConvert(element.getSpan(), boundElement.getClassType(), elementType);
            }
        }

        if (elementType == null) {
            elementType = Object.class;
        }

        return new BoundArrayLiteralExpression(boundElements, elementType);
    }

    private BoundExpression bindIndexExpression(IndexExpressionSyntax syntax) {
        BoundExpression target = bindExpression(syntax.getTarget());
        BoundExpression index = bindExpression(syntax.getIndex());

        if (index.getClassType() != Integer.class) {
            _diagnostics.reportCannotConvert(syntax.getIndex().getSpan(), index.getClassType(), Integer.class);
        }

        Class<?> resultType;
        if (target.getClassType() == SiyoArray.class) {
            resultType = resolveArrayElementType(target);
        } else if (target.getClassType() == String.class) {
            resultType = String.class;
        } else {
            _diagnostics.reportCannotIndex(syntax.getOpenBracket().getSpan(), target.getClassType());
            return new BoundLiteralExpression(0);
        }

        return new BoundIndexExpression(target, index, resultType);
    }

    private Class<?> resolveArrayElementType(BoundExpression target) {
        if (target instanceof BoundArrayLiteralExpression arr) {
            return arr.getElementType();
        }
        if (target instanceof BoundVariableExpression varExpr) {
            Class<?> elemType = getArrayElementType(varExpr.getVariable());
            if (elemType != null) return elemType;
        }
        if (target instanceof BoundCallExpression callExpr) {
            // range() returns int array
            if (callExpr.getFunction() == BuiltinFunctions.RANGE) {
                return Integer.class;
            }
        }
        if (target instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol structType = resolveStructType(memberExpr.getTarget());
            if (structType != null) {
                String fieldTypeName = structType.getFieldTypeName(memberExpr.getMemberName());
                if (fieldTypeName != null) {
                    Class<?> elemType = lookupElementType(fieldTypeName);
                    if (elemType != null) return elemType;
                }
            }
        }
        return Object.class;
    }

    private BoundExpression bindMemberAccessExpression(MemberAccessExpressionSyntax syntax) {
        // Check for enum access: EnumName.MemberName
        if (syntax.getTarget() instanceof NameExpressionSyntax nameExpr) {
            String typeName = nameExpr.getIdentifierToken().getData();
            Map<String, Integer> enumMembers = _enumTypes.get(typeName);
            if (enumMembers != null) {
                String memberName = syntax.getMember().getData();
                Integer value = enumMembers.get(memberName);
                if (value != null) {
                    return new BoundLiteralExpression(value);
                }
                _diagnostics.reportUndefinedName(syntax.getMember().getSpan(), typeName + "." + memberName);
                return new BoundLiteralExpression(0);
            }
        }

        BoundExpression target = bindExpression(syntax.getTarget());
        String memberName = syntax.getMember().getData();

        if (target.getClassType() != SiyoStruct.class) {
            _diagnostics.reportCannotAccessMember(syntax.getDot().getSpan(), target.getClassType());
            return new BoundLiteralExpression(0);
        }

        // Resolve struct type from variable to get field type
        Class<?> memberType = Object.class;
        StructSymbol structType = resolveStructType(target);
        if (structType != null && structType.hasField(memberName)) {
            memberType = structType.getFieldType(memberName);
        }

        return new BoundMemberAccessExpression(target, memberName, memberType);
    }

    private StructSymbol resolveStructType(BoundExpression target) {
        if (target instanceof BoundVariableExpression varExpr) {
            StructSymbol type = getVarStructType(varExpr.getVariable());
            if (type != null) return type;
        }
        if (target instanceof BoundStructLiteralExpression structLit) {
            return structLit.getStructType();
        }
        return null;
    }

    private BoundExpression bindCompoundAssignment(CompoundAssignmentExpressionSyntax syntax) {
        BoundExpression value = bindExpression(syntax.getValue());

        if (syntax.getTarget() instanceof IndexExpressionSyntax indexSyntax) {
            BoundExpression target = bindExpression(indexSyntax.getTarget());
            BoundExpression index = bindExpression(indexSyntax.getIndex());
            return new BoundIndexAssignmentExpression(target, index, value);
        }

        if (syntax.getTarget() instanceof MemberAccessExpressionSyntax memberSyntax) {
            BoundExpression target = bindExpression(memberSyntax.getTarget());
            String memberName = memberSyntax.getMember().getData();
            return new BoundMemberAssignmentExpression(target, memberName, value);
        }

        _diagnostics.reportCannotAssign(syntax.getEqualsToken().getSpan(), "expression");
        return value;
    }

    private BoundExpression bindStructLiteralExpression(StructLiteralExpressionSyntax syntax) {
        String typeName = syntax.getTypeName().getData();
        StructSymbol structType = _structTypes.get(typeName);

        if (structType == null) {
            _diagnostics.reportUndefinedType(syntax.getTypeName().getSpan(), typeName);
            return new BoundLiteralExpression(0);
        }

        java.util.LinkedHashMap<String, BoundExpression> fieldValues = new java.util.LinkedHashMap<>();
        for (SyntaxNode node : syntax.getFieldAssignments()) {
            FieldAssignmentSyntax field = (FieldAssignmentSyntax) node;
            String fieldName = field.getFieldName().getData();
            BoundExpression value = bindExpression(field.getValue());
            fieldValues.put(fieldName, value);
        }

        return new BoundStructLiteralExpression(structType, fieldValues);
    }

    private BoundStatement bindJavaImportStatement(JavaImportStatementSyntax syntax) {
        String fullClassName = (String) syntax.getClassName().getValue();
        if (fullClassName == null) return new BoundExpressionStatement(new BoundLiteralExpression(0));

        String simpleName = fullClassName.contains(".")
                ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1)
                : fullClassName;

        try {
            Class<?> javaClass = Class.forName(fullClassName);
            _javaClasses.put(simpleName, new codeanalysis.JavaClassInfo(simpleName, fullClassName, javaClass));
        } catch (ClassNotFoundException e) {
            _diagnostics.reportModuleNotFound(syntax.getClassName().getSpan(), fullClassName);
        }

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    public Map<String, codeanalysis.JavaClassInfo> getJavaClasses() { return _javaClasses; }

    private BoundStatement bindImportStatement(ImportStatementSyntax syntax) {
        String moduleName = (String) syntax.getModuleName().getValue();
        if (moduleName == null) {
            moduleName = syntax.getModuleName().getData();
            // Strip quotes if present
            if (moduleName != null && moduleName.startsWith("\"")) {
                moduleName = moduleName.substring(1, moduleName.length() - 1);
            }
        }

        if (moduleName == null || _importedModules.contains(moduleName)) {
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }
        _importedModules.add(moduleName);

        // Resolve file path
        String moduleFilePath = resolveModulePath(moduleName);
        if (moduleFilePath == null) {
            _diagnostics.reportModuleNotFound(syntax.getModuleName().getSpan(), moduleName);
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }

        // Circular import check
        if (_registry != null && _registry.isInProgress(moduleFilePath)) {
            _diagnostics.reportCircularImport(syntax.getModuleName().getSpan(), moduleName);
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }

        // Get or compile the module
        ModuleSymbol module;
        if (_registry != null && _registry.isCompiled(moduleFilePath)) {
            module = _registry.getModule(moduleFilePath);
        } else {
            module = compileModule(moduleName, moduleFilePath);
            if (module == null) {
                return new BoundExpressionStatement(new BoundLiteralExpression(0));
            }
        }

        // Register imported functions with qualified name: "moduleName.funcName"
        // This prevents all name conflicts (with builtins, locals, other modules)
        String className = Character.toUpperCase(moduleName.charAt(0)) + moduleName.substring(1);
        for (FunctionSymbol func : module.getFunctions()) {
            if (BuiltinFunctions.isBuiltin(func)) continue;
            String qualifiedName = moduleName + "." + func.getName();
            FunctionSymbol importedFunc = new FunctionSymbol(
                    qualifiedName, func.getParameters(), func.getReturnType(), className);
            _scope.tryDeclareFunction(importedFunc);
            BoundBlockStatement body = module.getFunctionBodies().get(func);
            if (body != null) {
                _functionBodies.put(importedFunc, body);
            }
        }

        // Register imported structs
        for (var entry : module.getStructs().entrySet()) {
            _structTypes.put(entry.getKey(), entry.getValue());
        }

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    private String resolveModulePath(String moduleName) {
        String basePath = _filePath != null
                ? java.nio.file.Paths.get(_filePath).getParent().toString()
                : System.getProperty("user.dir");
        java.nio.file.Path candidate = java.nio.file.Paths.get(basePath, moduleName + ".siyo");
        if (java.nio.file.Files.exists(candidate)) {
            return candidate.toAbsolutePath().toString();
        }
        return null;
    }

    private ModuleSymbol compileModule(String moduleName, String filePath) {
        try {
            if (_registry != null) _registry.markInProgress(filePath);

            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
            codeanalysis.syntax.SyntaxTree tree = codeanalysis.syntax.SyntaxTree.parse(source);

            // Create a dedicated binder for the module so we can access its struct types
            var parentScope = createParentScopes(null);
            Binder moduleBinder = new Binder(parentScope);
            moduleBinder._registry = _registry;
            moduleBinder._filePath = filePath;
            BoundStatement statement = moduleBinder.bindStatement(tree.getRoot().getStatement());

            if (moduleBinder._diagnostics.size() > 0) {
                _diagnostics.addAll(moduleBinder._diagnostics);
                if (_registry != null) _registry.markComplete(filePath);
                return null;
            }

            String className = Character.toUpperCase(moduleName.charAt(0)) + moduleName.substring(1);

            Map<FunctionSymbol, BoundBlockStatement> bodies = new HashMap<>(moduleBinder._functionBodies);
            List<FunctionSymbol> functions = new ArrayList<>(bodies.keySet());
            Map<String, StructSymbol> structs = new HashMap<>(moduleBinder._structTypes);

            ModuleSymbol module = new ModuleSymbol(moduleName, className, filePath, functions, bodies, structs);
            if (_registry != null) {
                _registry.register(filePath, module);
                _registry.markComplete(filePath);
            }
            return module;
        } catch (Exception e) {
            if (_registry != null) _registry.markComplete(filePath);
            return null;
        }
    }

    private BoundExpression bindMemberCallExpression(MemberCallExpressionSyntax syntax) {
        MemberAccessExpressionSyntax memberAccess = syntax.getMemberAccess();

        // Check if target is a module name
        if (memberAccess.getTarget() instanceof NameExpressionSyntax nameExpr) {
            String targetName = nameExpr.getIdentifierToken().getData();
            String funcName = memberAccess.getMember().getData();
            String qualifiedName = targetName + "." + funcName;

            // Lookup with qualified name
            if (_scope.tryLookupFunction(qualifiedName)) {
                FunctionSymbol func = _scope.lookupFunction(qualifiedName);

                // Bind arguments
                List<BoundExpression> boundArgs = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    boundArgs.add(bindExpression(argSyntax));
                }

                // Type check arguments
                if (boundArgs.size() != func.getParameters().size()) {
                    _diagnostics.reportWrongArgumentCount(memberAccess.getSpan(), qualifiedName, func.getParameters().size(), boundArgs.size());
                    return new BoundLiteralExpression(0);
                }

                for (int i = 0; i < boundArgs.size(); i++) {
                    BoundExpression arg = boundArgs.get(i);
                    ParameterSymbol param = func.getParameters().get(i);
                    if (param.getType() != Object.class && arg.getClassType() != Object.class && arg.getClassType() != param.getType()) {
                        _diagnostics.reportWrongArgumentType(syntax.getArguments().get(i).getSpan(), param.getName(), param.getType(), arg.getClassType());
                    }
                }

                return new BoundCallExpression(func, boundArgs);
            }

            // Check if target is a Java class (static method or constructor)
            codeanalysis.JavaClassInfo javaClass = _javaClasses.get(targetName);
            if (javaClass != null) {
                List<BoundExpression> boundArgs = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    boundArgs.add(bindExpression(argSyntax));
                }
                boolean isConstructor = funcName.equals("new");
                return new BoundJavaMethodCallExpression(javaClass, null, funcName, boundArgs, isConstructor, !isConstructor);
            }

            // Not a module or Java class - fall through to instance method call
        }

        // Instance method call on a variable: obj.method(args)
        BoundExpression target = bindExpression(memberAccess.getTarget());
        String methodName = memberAccess.getMember().getData();
        List<BoundExpression> boundArgs = new ArrayList<>();
        for (ExpressionSyntax argSyntax : syntax.getArguments()) {
            boundArgs.add(bindExpression(argSyntax));
        }
        // For Java objects (Object.class), emit as Java instance method call
        return new BoundJavaMethodCallExpression(null, target, methodName, boundArgs, false, false);
    }

    private BoundStatement bindTryCatchStatement(TryCatchStatementSyntax syntax) {
        BoundStatement tryBody = bindStatement(syntax.getTryBody());
        String errorName = syntax.getErrorVariable().getData();
        VariableSymbol errorVar = new VariableSymbol(errorName, true, String.class);
        _scope = new BoundScope(_scope);
        _scope.tryDeclare(errorVar);
        BoundStatement catchBody = bindStatement(syntax.getCatchBody());
        _scope = _scope.getParent();
        return new BoundTryCatchStatement(tryBody, errorVar, catchBody);
    }

    private void registerEnumDeclaration(EnumDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_enumTypes.containsKey(name)) return;
        Map<String, Integer> members = new HashMap<>();
        int ordinal = 0;
        for (SyntaxToken member : syntax.getMembers()) {
            members.put(member.getData(), ordinal++);
        }
        _enumTypes.put(name, members);
    }

    private BoundStatement bindEnumDeclaration(EnumDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (!_enumTypes.containsKey(name)) {
            registerEnumDeclaration(syntax);
        }
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    private BoundStatement bindStructDeclaration(StructDeclarationSyntax syntax) {
        // Struct already registered in first pass, just validate
        String name = syntax.getIdentifier().getData();
        if (!_structTypes.containsKey(name)) {
            registerStructDeclaration(syntax);
        }
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    public Map<String, StructSymbol> getStructTypes() {
        return _structTypes;
    }

    private Class<?> lookupType(String name) {
        if (name.endsWith("[]")) {
            return SiyoArray.class;
        }
        return switch (name) {
            case "int" -> Integer.class;
            case "bool" -> Boolean.class;
            case "float" -> Double.class;
            case "string" -> String.class;
            default -> _structTypes.containsKey(name) ? SiyoStruct.class : null;
        };
    }

    private StructSymbol resolveStructTypeFromCollection(BoundExpression collection) {
        if (collection instanceof BoundArrayLiteralExpression arr) {
            if (!arr.getElements().isEmpty() && arr.getElements().get(0) instanceof BoundStructLiteralExpression structLit) {
                return structLit.getStructType();
            }
        }
        if (collection instanceof BoundVariableExpression varExpr) {
            StructSymbol structType = getArrayStructElementType(varExpr.getVariable());
            if (structType != null) return structType;
        }
        return null;
    }

    private Class<?> lookupElementType(String typeName) {
        if (typeName.endsWith("[]")) {
            return lookupType(typeName.substring(0, typeName.length() - 2));
        }
        return null;
    }
}
