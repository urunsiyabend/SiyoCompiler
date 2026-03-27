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
    final Map<FunctionSymbol, BoundBlockStatement> _functionBodies = new HashMap<>();
    private final Stack<LoopLabels> _loopStack = new Stack<>();
    private final Map<String, StructSymbol> _structTypes = new HashMap<>();

    private final TypeResolver _typeResolver;
    private final ModuleHandler _moduleHandler;

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
        _typeResolver = new TypeResolver(_structTypes);
        _moduleHandler = new ModuleHandler(_structTypes, _typeResolver, _functionBodies);
        _moduleHandler.setDiagnostics(_diagnostics);
        _moduleHandler.setScope(_scope);

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
        binder._moduleHandler.setRegistry(registry);
        binder._moduleHandler.setFilePath(filePath);
        BoundStatement statement = binder.bindStatement(syntax.getStatement());
        Iterable<FunctionSymbol> functions = binder._scope.getDeclaredFunctions();
        Map<FunctionSymbol, BoundBlockStatement> functionBodies = binder._functionBodies;
        Iterable<VariableSymbol> variables = binder._scope.getDeclaredVariables();
        DiagnosticBox diagnostics = binder._diagnostics;
        return new BoundGlobalScope(previous, diagnostics, functions, functionBodies, variables, statement);
    }

    static BoundScope createParentScopes(BoundGlobalScope previous) {
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
    BoundStatement bindStatement(StatementSyntax syntax) {
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
            case StructDeclaration -> _moduleHandler.bindStructDeclaration((StructDeclarationSyntax)syntax);
            case EnumDeclaration -> _moduleHandler.bindEnumDeclaration((EnumDeclarationSyntax)syntax);
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
        _moduleHandler.setScope(_scope);

        // First pass: process imports, register function/struct/enum declarations
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            if (statementSyntax instanceof ImportStatementSyntax importSyntax) {
                bindImportStatement(importSyntax);
            } else if (statementSyntax instanceof JavaImportStatementSyntax javaImportSyntax) {
                bindJavaImportStatement(javaImportSyntax);
            } else if (statementSyntax instanceof FunctionDeclarationSyntax funcSyntax) {
                _moduleHandler.registerFunctionDeclaration(funcSyntax);
            } else if (statementSyntax instanceof StructDeclarationSyntax structSyntax) {
                _moduleHandler.registerStructDeclaration(structSyntax);
            } else if (statementSyntax instanceof EnumDeclarationSyntax enumSyntax) {
                _moduleHandler.registerEnumDeclaration(enumSyntax);
            }
        }

        // Second pass: bind all statements (function bodies can now reference each other)
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }

        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        return new BoundBlockStatement(statements);
    }

    /**
     * Binds the expression statement syntax and returns the corresponding bound expression statement.
     *
     * @param syntax The expression statement syntax to bind.
     * @return The bound expression statement.
     */
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
                _typeResolver.trackArrayType(variableSymbol, arr.getElementType(), structLit.getStructType());
            } else {
                _typeResolver.trackArrayType(variableSymbol, arr.getElementType());
            }
        } else if (initializer instanceof BoundStructLiteralExpression structLit) {
            _typeResolver.trackStructType(variableSymbol, structLit.getStructType());
        } else if (initializer instanceof BoundJavaMethodCallExpression javaCall && javaCall.getClassInfo() != null) {
            // Track Java class for constructor results: mut file = File.new("x") -> file is File
            if (javaCall.isConstructor()) {
                _typeResolver.trackJavaClassType(variableSymbol, javaCall.getClassInfo());
            } else if (javaCall.getResolvedReturnType() != null) {
                _typeResolver.trackJavaResolvedType(variableSymbol, javaCall.getResolvedReturnType());
            } else if (javaCall.getResolvedSignature() != null) {
                String returnDesc = javaCall.getResolvedSignature().getReturnDescriptor();
                codeanalysis.JavaClassInfo returnClassInfo = _typeResolver.resolveJavaClassFromDescriptor(returnDesc);
                if (returnClassInfo != null) {
                    _typeResolver.trackJavaClassType(variableSymbol, returnClassInfo);
                }
            }
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
            Class<?> parameterType = _typeResolver.lookupType(typeName);

            if (parameterType == null) {
                _diagnostics.reportUndefinedType(parameterSyntax.getTypeToken().getSpan(), typeName);
                parameterType = Integer.class; // Default to int for error recovery
            }

            if (!seenParameterNames.add(parameterName)) {
                _diagnostics.reportDuplicateParameter(parameterSyntax.getIdentifier().getSpan(), parameterName);
            } else {
                ParameterSymbol parameter = new ParameterSymbol(parameterName, parameterSyntax.isMutable(), parameterType);
                parameters.add(parameter);
            }
        }

        // Parse return type
        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            String returnTypeName = syntax.getTypeClause().getIdentifier().getData();
            returnType = _typeResolver.lookupType(returnTypeName);
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
        _moduleHandler.setScope(_scope);
        FunctionSymbol previousFunction = _currentFunction;
        _currentFunction = function;

        // Add the function's own parameter symbols to scope (important for consistency with evaluator)
        int paramIdx = 0;
        for (ParameterSymbol parameter : function.getParameters()) {
            _scope.tryDeclare(parameter);
            // Track array element types from parameter type names
            if (paramIdx < syntax.getParameters().getCount()) {
                String typeName = syntax.getParameters().get(paramIdx).getTypeToken().getData();
                Class<?> elemType = _typeResolver.lookupElementType(typeName);
                if (elemType != null) {
                    _typeResolver.trackArrayType(parameter, elemType);
                }
                // Track struct types from parameter type names
                String baseTypeName = typeName.endsWith("[]") ? typeName.substring(0, typeName.length() - 2) : typeName;
                StructSymbol structSym = _structTypes.get(baseTypeName);
                if (structSym != null && parameter.getType() == SiyoStruct.class) {
                    _typeResolver.trackStructType(parameter, structSym);
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
        _moduleHandler.setScope(_scope);

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

    private BoundStatement bindForInStatement(ForInStatementSyntax syntax) {
        BoundExpression collection = bindExpression(syntax.getCollection());
        String itemName = syntax.getItemName().getData();
        int uid = _labelCounter++; // unique id to avoid variable name collisions

        // Create index and collection variables with unique names
        VariableSymbol indexVar = new VariableSymbol("_idx" + uid, false, Integer.class);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
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
        Class<?> elementType = _typeResolver.resolveArrayElementType(collection);
        StructSymbol structType = _typeResolver.resolveStructTypeFromCollection(collection);
        if (structType != null) {
            _typeResolver.trackArrayType(collectionVar, elementType, structType);
        } else {
            _typeResolver.trackArrayType(collectionVar, elementType);
        }
        VariableSymbol itemVar = new VariableSymbol(itemName, false, elementType);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(itemVar);
        // Track struct type if element is a struct
        if (elementType == SiyoStruct.class && structType != null) {
            _typeResolver.trackStructType(itemVar, structType);
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
        _moduleHandler.setScope(_scope);

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
        _moduleHandler.setScope(_scope);
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
            resultType = _typeResolver.resolveArrayElementType(target);
        } else if (target.getClassType() == String.class) {
            resultType = String.class;
        } else {
            _diagnostics.reportCannotIndex(syntax.getOpenBracket().getSpan(), target.getClassType());
            return new BoundLiteralExpression(0);
        }

        return new BoundIndexExpression(target, index, resultType);
    }

    private BoundExpression bindMemberAccessExpression(MemberAccessExpressionSyntax syntax) {
        // Check for enum access: EnumName.MemberName
        if (syntax.getTarget() instanceof NameExpressionSyntax nameExpr) {
            String typeName = nameExpr.getIdentifierToken().getData();
            Map<String, Integer> enumMembers = _moduleHandler.getEnumTypes().get(typeName);
            if (enumMembers != null) {
                String memberName = syntax.getMember().getData();
                Integer value = enumMembers.get(memberName);
                if (value != null) {
                    return new BoundLiteralExpression(value);
                }
                _diagnostics.reportUndefinedName(syntax.getMember().getSpan(), typeName + "." + memberName);
                return new BoundLiteralExpression(0);
            }

            // Check for Java static field access: ClassName.FIELD
            codeanalysis.JavaClassInfo javaClass = _typeResolver.getJavaClasses().get(typeName);
            if (javaClass != null) {
                String fieldName = syntax.getMember().getData();
                String fieldDesc = javaClass.getMetadata().getStaticFieldDescriptor(fieldName);
                if (fieldDesc != null) {
                    return new BoundJavaStaticFieldExpression(javaClass, fieldName, fieldDesc);
                }
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
        StructSymbol structType = _typeResolver.resolveStructType(target);
        if (structType != null && structType.hasField(memberName)) {
            memberType = structType.getFieldType(memberName);
        }

        return new BoundMemberAccessExpression(target, memberName, memberType);
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
            BoundExpression fieldValue = bindExpression(field.getValue());
            fieldValues.put(fieldName, fieldValue);
        }

        return new BoundStructLiteralExpression(structType, fieldValues);
    }

    private BoundStatement bindImportStatement(ImportStatementSyntax syntax) {
        BoundStatement result = _moduleHandler.bindImportStatement(syntax);
        // Sync scope back in case module handler changed it
        return result;
    }

    private BoundStatement bindJavaImportStatement(JavaImportStatementSyntax syntax) {
        return _moduleHandler.bindJavaImportStatement(syntax);
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
            codeanalysis.JavaClassInfo javaClass = _typeResolver.getJavaClasses().get(targetName);
            if (javaClass != null) {
                List<BoundExpression> boundArgs = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    boundArgs.add(bindExpression(argSyntax));
                }
                boolean isConstructor = funcName.equals("new");

                // Compile-time method resolution
                codeanalysis.JavaMethodSignature resolved = isConstructor
                        ? javaClass.resolveConstructor(boundArgs.size())
                        : javaClass.resolveMethod(funcName, boundArgs.size(), getArgTypes(boundArgs));

                if (resolved == null) {
                    _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(), targetName + "." + funcName);
                    return new BoundLiteralExpression(0);
                }

                codeanalysis.JavaResolvedType returnType = _typeResolver.resolveMethodReturnType(
                        resolved, new codeanalysis.JavaResolvedType(javaClass));
                return new BoundJavaMethodCallExpression(javaClass, null, funcName, boundArgs, resolved, returnType);
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

        // Resolve Java class info from the target expression (with generic type bindings)
        codeanalysis.JavaResolvedType targetResolvedType = _typeResolver.resolveJavaResolvedType(target);
        codeanalysis.JavaClassInfo targetClassInfo = targetResolvedType != null
                ? targetResolvedType.getClassInfo()
                : _typeResolver.resolveJavaClassInfo(target);
        codeanalysis.JavaMethodSignature resolved = null;
        codeanalysis.JavaResolvedType resolvedReturnType = null;
        if (targetClassInfo != null) {
            resolved = targetClassInfo.resolveMethod(methodName, boundArgs.size(), getArgTypes(boundArgs));
            if (resolved == null) {
                _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(),
                        targetClassInfo.getSimpleName() + "." + methodName);
                return new BoundLiteralExpression(0);
            }
            // Resolve generic return type using owner's type bindings
            resolvedReturnType = _typeResolver.resolveMethodReturnType(resolved, targetResolvedType);
        }

        return new BoundJavaMethodCallExpression(targetClassInfo, target, methodName, boundArgs, resolved, resolvedReturnType);
    }

    private BoundStatement bindTryCatchStatement(TryCatchStatementSyntax syntax) {
        BoundStatement tryBody = bindStatement(syntax.getTryBody());
        String errorName = syntax.getErrorVariable().getData();
        VariableSymbol errorVar = new VariableSymbol(errorName, true, String.class);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(errorVar);
        BoundStatement catchBody = bindStatement(syntax.getCatchBody());
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        return new BoundTryCatchStatement(tryBody, errorVar, catchBody);
    }

    private Class<?>[] getArgTypes(List<BoundExpression> args) {
        Class<?>[] types = new Class<?>[args.size()];
        for (int i = 0; i < args.size(); i++) types[i] = args.get(i).getClassType();
        return types;
    }

    // --- Public accessors ---

    public Map<String, StructSymbol> getStructTypes() {
        return _structTypes;
    }

    public Map<String, codeanalysis.JavaClassInfo> getJavaClasses() {
        return _typeResolver.getJavaClasses();
    }

    public ModuleHandler getModuleHandler() {
        return _moduleHandler;
    }

    public TypeResolver getTypeResolver() {
        return _typeResolver;
    }
}
