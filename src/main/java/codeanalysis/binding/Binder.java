package codeanalysis.binding;

import codeanalysis.ModuleRegistry;
import codeanalysis.ModuleSymbol;
import codeanalysis.BuiltinFunctions;
import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.LabelSymbol;
import codeanalysis.ParameterSymbol;
import codeanalysis.SiyoArray;
import codeanalysis.SiyoChannel;
import codeanalysis.SiyoClosure;
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
    private boolean _insideScope = false;
    private boolean _insideSpawn = false;
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
        BoundGlobalScope scope = new BoundGlobalScope(previous, diagnostics, functions, functionBodies, variables, statement);
        scope.setStructTypes(binder._structTypes);
        return scope;
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
            case ImplDeclaration -> bindImplDeclaration((ImplDeclarationSyntax)syntax);
            case ActorDeclaration -> bindActorDeclaration((ActorDeclarationSyntax)syntax);
            case SendStatement -> bindSendStatement((SendStatementSyntax)syntax);
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
            } else if (statementSyntax instanceof ActorDeclarationSyntax actorSyntax) {
                // Actor = struct with isActor flag
                String name = actorSyntax.getIdentifier().getData();
                java.util.LinkedHashMap<String, Class<?>> fields = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, String> fieldTypeNames = new java.util.LinkedHashMap<>();
                for (ParameterSyntax field : actorSyntax.getFields()) {
                    String fn = field.getIdentifier().getData();
                    String tn = field.getTypeToken().getData();
                    Class<?> ft = _typeResolver.lookupType(tn);
                    if (ft == null) ft = Object.class;
                    fields.put(fn, ft);
                    fieldTypeNames.put(fn, tn);
                }
                StructSymbol symbol = new StructSymbol(name, fields, fieldTypeNames);
                symbol.setActor(true);
                _structTypes.put(name, symbol);
            } else if (statementSyntax instanceof ImplDeclarationSyntax implSyntax) {
                _moduleHandler.registerImplDeclaration(implSyntax);
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
        } else if (initializer instanceof BoundIndexExpression indexExpr && indexExpr.getClassType() == SiyoStruct.class) {
            // Track struct type from array index: mut todo = todos[i]
            StructSymbol elemStruct = _typeResolver.resolveStructTypeFromCollection(indexExpr.getTarget());
            if (elemStruct != null) _typeResolver.trackStructType(variableSymbol, elemStruct);
        } else if (initializer instanceof BoundCallExpression callExpr && callExpr.getClassType() == SiyoStruct.class) {
            // Track struct type from function return
            StructSymbol st = _typeResolver.resolveStructType(initializer);
            if (st != null) _typeResolver.trackStructType(variableSymbol, st);
        } else if (initializer instanceof BoundSpawnExpression spawnExpr && spawnExpr.getClassType() == SiyoStruct.class) {
            // Track actor/struct type from spawn expression
            if (spawnExpr.getActorTypeName() != null) {
                StructSymbol st = _structTypes.get(spawnExpr.getActorTypeName());
                if (st != null) _typeResolver.trackStructType(variableSymbol, st);
            }
        } else if (initializer instanceof BoundCallExpression callExpr2 && callExpr2.getClassType() == SiyoArray.class) {
            // Track element type for built-in functions that return arrays
            Class<?> elemType = _typeResolver.resolveArrayElementType(initializer);
            _typeResolver.trackArrayType(variableSymbol, elemType);
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
            case CastExpression -> bindCastExpression((CastExpressionSyntax) syntax);
            case LambdaExpression -> bindLambdaExpression((LambdaExpressionSyntax) syntax);
            case ScopeExpression -> bindScopeExpression((ScopeExpressionSyntax) syntax);
            case SpawnExpression -> bindSpawnExpression((SpawnExpressionSyntax) syntax);
            case MatchExpression -> bindMatchExpression((MatchExpressionSyntax) syntax);
            case TryExpression -> bindTryExpression((TryExpressionSyntax) syntax);
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
        // String interpolation: "hello {name}, {count} items" → "hello " + toString(name) + ", " + toString(count) + " items"
        if (value instanceof String str && str.contains("{")) {
            BoundExpression result = desugarStringInterpolation(str, syntax);
            if (result != null) return result;
        }
        // Clean escaped braces in non-interpolated strings
        if (value instanceof String str && str.contains("\\")) {
            String cleaned = str.replace("\\{", "{").replace("\\}", "}");
            if (!cleaned.equals(str)) return new BoundLiteralExpression(cleaned);
        }
        return new BoundLiteralExpression(value);
    }

    private BoundExpression desugarStringInterpolation(String template, LiteralExpressionSyntax syntax) {
        // Check if there are any real interpolation braces (not escaped \{)
        boolean hasInterpolation = false;
        for (int ci = 0; ci < template.length(); ci++) {
            if (template.charAt(ci) == '{' && (ci == 0 || template.charAt(ci - 1) != '\\')) {
                hasInterpolation = true;
                break;
            }
        }
        if (!hasInterpolation) {
            // Replace escaped braces with literal braces and return plain string
            String cleaned = template.replace("\\{", "{").replace("\\}", "}");
            return new BoundLiteralExpression(cleaned);
        }
        List<BoundExpression> parts = new ArrayList<>();
        int i = 0;
        while (i < template.length()) {
            if (template.charAt(i) == '{' && (i == 0 || template.charAt(i - 1) != '\\')) {
                // Find matching closing brace
                int depth = 1;
                int start = i + 1;
                int j = start;
                while (j < template.length() && depth > 0) {
                    if (template.charAt(j) == '{') depth++;
                    else if (template.charAt(j) == '}') depth--;
                    if (depth > 0) j++;
                }
                if (depth != 0) return null; // unmatched brace — treat as plain string
                String exprSource = template.substring(start, j);
                // Parse and bind the embedded expression
                BoundExpression bound = bindInterpolationExpr(exprSource);
                parts.add(bound);
                i = j + 1;
            } else {
                // Text segment: collect until next unescaped { or end
                int start = i;
                while (i < template.length()) {
                    if (template.charAt(i) == '{' && (i == 0 || template.charAt(i - 1) != '\\')) break;
                    i++;
                }
                String textSeg = template.substring(start, i).replace("\\{", "{").replace("\\}", "}");
                parts.add(new BoundLiteralExpression(textSeg));
            }
        }
        if (parts.isEmpty()) return null;
        // Build chain of string concatenation
        BoundExpression result = parts.get(0);
        if (result.getClassType() != String.class) {
            // Wrap in toString via String + conversion
            result = makeStringConcat(new BoundLiteralExpression(""), result);
        }
        for (int k = 1; k < parts.size(); k++) {
            result = makeStringConcat(result, parts.get(k));
        }
        return result;
    }

    private BoundExpression bindTryExpression(TryExpressionSyntax syntax) {
        BoundStatement tryBody = bindStatement(syntax.getTryBody());
        String errorName = syntax.getErrorVariable().getData();
        VariableSymbol errorVar = new VariableSymbol(errorName, true, String.class);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(errorVar);
        BoundStatement catchBody = bindStatement(syntax.getCatchBody());
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        // Determine result type from last expression in try body, fallback to catch body
        Class<?> resultType = lastExprType(tryBody);
        if (resultType == null) resultType = lastExprType(catchBody);
        if (resultType == null) resultType = Object.class;
        return new BoundTryExpression(tryBody, errorVar, catchBody, resultType);
    }

    /**
     * Channel for-in: for msg in ch { ... } → desugars to:
     *   mut _ch = ch
     *   while true { mut msg = _ch.receive(); if msg == null { break }; body }
     */
    private BoundStatement bindChannelForIn(ForInStatementSyntax syntax, BoundExpression channel, String itemName) {
        int uid = _labelCounter++;
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);

        // Store channel in local
        VariableSymbol chVar = new VariableSymbol("_ch" + uid, true, SiyoChannel.class);
        _scope.tryDeclare(chVar);
        BoundVariableDeclaration chDecl = new BoundVariableDeclaration(chVar, channel);

        // Item variable (Object type — receive returns Object)
        VariableSymbol itemVar = new VariableSymbol(itemName, true, Object.class);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(itemVar);

        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));

        BoundStatement userBody = bindStatement(syntax.getBody());
        _loopStack.pop();

        // Build: msg = _ch.receive()
        codeanalysis.JavaClassInfo chClass = _typeResolver.resolveJavaClassForSiyoType(SiyoChannel.class);
        codeanalysis.JavaMethodSignature recvSig = chClass != null ? chClass.resolveMethod("receive", 0) : null;
        BoundExpression recvCall = new BoundJavaMethodCallExpression(chClass, new BoundVariableExpression(chVar),
                "receive", java.util.List.of(), recvSig, null);
        BoundVariableDeclaration itemDecl = new BoundVariableDeclaration(itemVar, recvCall);

        // Build: if msg == null { break }
        BoundExpression nullCheck = new BoundBinaryExpression(
                new BoundVariableExpression(itemVar),
                BoundBinaryOperator.bind(codeanalysis.syntax.SyntaxType.EqualsEqualsToken, Object.class, Object.class),
                new BoundLiteralExpression(null));
        BoundStatement breakStmt = new BoundConditionalGotoStatement(breakLabel, nullCheck, true);

        // Assemble while body: [itemDecl, nullCheck→break, userBody]
        ArrayList<BoundStatement> whileBody = new ArrayList<>();
        whileBody.add(itemDecl);
        whileBody.add(breakStmt);
        if (userBody instanceof BoundBlockStatement block) {
            whileBody.addAll(block.getStatements());
        } else {
            whileBody.add(userBody);
        }

        BoundWhileStatement whileStmt = new BoundWhileStatement(
                new BoundLiteralExpression(true),
                new BoundBlockStatement(whileBody),
                breakLabel, continueLabel);

        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);

        ArrayList<BoundStatement> outer = new ArrayList<>();
        outer.add(chDecl);
        outer.add(whileStmt);
        return new BoundBlockStatement(outer);
    }

    private Class<?> lastExprType(BoundStatement body) {
        if (body instanceof BoundBlockStatement block && !block.getStatements().isEmpty()) {
            var last = block.getStatements().get(block.getStatements().size() - 1);
            if (last instanceof BoundExpressionStatement exprStmt) {
                return exprStmt.getExpression().getClassType();
            }
        }
        return null;
    }

    private BoundExpression bindMatchExpression(MatchExpressionSyntax syntax) {
        BoundExpression target = bindExpression(syntax.getTarget());
        List<BoundMatchExpression.BoundMatchArm> arms = new ArrayList<>();
        Class<?> resultType = null;
        for (MatchArmSyntax arm : syntax.getArms()) {
            BoundExpression pattern = arm.isDefault() ? null : bindExpression(arm.getPattern());
            BoundExpression body;
            if (arm.getBody() instanceof BlockExpressionSyntax blockExpr) {
                body = bindBlockExpressionBody(blockExpr.getBlock());
            } else {
                body = bindExpression(arm.getBody());
            }
            if (resultType == null) resultType = body.getClassType();
            arms.add(new BoundMatchExpression.BoundMatchArm(pattern, body, arm.isDefault()));
        }
        if (resultType == null) resultType = Object.class;
        return new BoundMatchExpression(target, arms, resultType);
    }

    private BoundExpression bindBlockExpressionBody(StatementSyntax block) {
        // For block bodies in match arms, bind the block and return the last expression
        if (block instanceof BlockStatementSyntax bs && !bs.getStatements().isEmpty()) {
            var stmts = bs.getStatements();
            var last = stmts.get(stmts.size() - 1);
            if (last instanceof ExpressionStatementSyntax exprStmt) {
                return bindExpression(exprStmt.getExpression());
            }
        }
        return new BoundLiteralExpression(0);
    }

    /**
     * Bind an interpolation expression string. Uses fast paths for common patterns
     * (variables, member access, function calls) to avoid expensive SyntaxTree.parse().
     */
    private BoundExpression bindInterpolationExpr(String exprSource) {
        // Fast path 1: simple identifier — {name}
        if (exprSource.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            if (_scope.tryLookup(exprSource)) {
                return new BoundVariableExpression(_scope.lookupVariable(exprSource));
            }
        }
        // Fast path 2: member access — {self.field} or {obj.field}
        if (exprSource.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")) {
            String[] segments = exprSource.split("\\.");
            // Build chain: bind first as variable, then chain member access
            if (_scope.tryLookup(segments[0])) {
                SyntaxToken id = new SyntaxToken(SyntaxType.IdentifierToken, 0, segments[0], null);
                ExpressionSyntax expr = new NameExpressionSyntax(id);
                BoundExpression bound = bindExpression(expr);
                for (int si = 1; si < segments.length; si++) {
                    SyntaxToken dot = new SyntaxToken(SyntaxType.DotToken, 0, ".", null);
                    SyntaxToken member = new SyntaxToken(SyntaxType.IdentifierToken, 0, segments[si], null);
                    expr = new MemberAccessExpressionSyntax(expr, dot, member);
                    bound = bindExpression(expr);
                }
                return bound;
            }
        }
        // Slow path: full parse for complex expressions (arithmetic, function calls, etc.)
        try {
            var tree = SyntaxTree.parse(exprSource);
            var root = tree.getRoot().getStatement();
            ExpressionSyntax exprSyntax = extractExpression(root);
            if (exprSyntax != null) {
                return bindExpression(exprSyntax);
            }
        } catch (Exception e) {
            // parse error
        }
        return null;
    }

    private ExpressionSyntax extractExpression(StatementSyntax root) {
        if (root instanceof ExpressionStatementSyntax exprStmt) {
            return exprStmt.getExpression();
        }
        if (root instanceof BlockStatementSyntax block && !block.getStatements().isEmpty()) {
            var firstStmt = block.getStatements().get(0);
            if (firstStmt instanceof ExpressionStatementSyntax exprStmt) {
                return exprStmt.getExpression();
            }
        }
        return null;
    }

    private BoundExpression makeStringConcat(BoundExpression left, BoundExpression right) {
        BoundBinaryOperator op = BoundBinaryOperator.bind(SyntaxType.PlusToken, left.getClassType(), right.getClassType());
        if (op == null) {
            // Fallback: treat right as Object for String + Object concat
            op = BoundBinaryOperator.bind(SyntaxType.PlusToken, String.class, Object.class);
        }
        if (op == null) {
            op = BoundBinaryOperator.bind(SyntaxType.PlusToken, String.class, String.class);
        }
        return new BoundBinaryExpression(left, op, right);
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

        // Use pre-registered function symbol if available (resolve by arg count for overloads)
        FunctionSymbol function;
        if (_scope.tryLookupFunction(name)) {
            function = _scope.lookupFunction(name, parameters.size());
            if (function == null) function = _scope.lookupFunction(name);
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
            } else if (expression.getClassType() != _currentFunction.getReturnType()
                    && _currentFunction.getReturnType() != Object.class
                    && expression.getClassType() != Object.class) {
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

        // Check if calling a closure variable: f(args)
        if (!_scope.tryLookupFunction(name) && _scope.tryLookup(name)) {
            VariableSymbol var = _scope.lookupVariable(name);
            if (var.getType() == SiyoClosure.class) {
                List<BoundExpression> args = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    args.add(bindExpression(argSyntax));
                }
                return new BoundClosureCallExpression(new BoundVariableExpression(var), args);
            }
        }

        if (!_scope.tryLookupFunction(name)) {
            _diagnostics.reportUndefinedFunction(syntax.getIdentifier().getSpan(), name);
            return new BoundLiteralExpression(0);
        }

        // Bind arguments first to get count for overload resolution
        List<BoundExpression> boundArguments = new ArrayList<>();
        for (ExpressionSyntax argumentSyntax : syntax.getArguments()) {
            BoundExpression boundArgument = bindExpression(argumentSyntax);
            boundArguments.add(boundArgument);
        }

        // Resolve overload by arg count, then fallback to first match
        FunctionSymbol function = _scope.lookupFunction(name, boundArguments.size());
        if (function == null) function = _scope.lookupFunction(name);

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

        // Channel iteration: for msg in ch { ... } → while (true) { msg = ch.receive(); if msg == null break; body }
        if (collection.getClassType() == SiyoChannel.class) {
            return bindChannelForIn(syntax, collection, itemName);
        }

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

        // Actor field access rejection — actor state is private (except from self inside impl methods)
        if (structType != null && structType.isActor()) {
            // Allow self.field inside impl methods (target is "self" variable)
            boolean isSelfAccess = (target instanceof BoundVariableExpression varExpr
                    && varExpr.getVariable().getName().equals("self"));
            if (!isSelfAccess) {
                _diagnostics.reportUndefinedName(syntax.getMember().getSpan(),
                    structType.getName() + "." + memberName + " — actor state is private, use a method");
                return new BoundLiteralExpression(0);
            }
        }

        if (structType != null && structType.hasField(memberName)) {
            memberType = structType.getFieldType(memberName);
            // If field is typed "object" but the type name is a known struct/actor, upgrade to SiyoStruct
            if (memberType == Object.class) {
                String fieldTypeName = structType.getFieldTypeName(memberName);
                if (fieldTypeName != null && _structTypes.containsKey(fieldTypeName)) {
                    memberType = SiyoStruct.class;
                }
            }
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

    private BoundExpression bindScopeExpression(ScopeExpressionSyntax syntax) {
        boolean wasInScope = _insideScope;
        _insideScope = true;
        BoundStatement body = bindStatement(syntax.getBody());
        _insideScope = wasInScope;

        BoundBlockStatement block = body instanceof BoundBlockStatement b
                ? b : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));
        return new BoundScopeExpression(block);
    }

    private BoundExpression bindSpawnExpression(SpawnExpressionSyntax syntax) {
        // Actor spawn: spawn Expr (not block) — doesn't need scope
        if (syntax.getBody() instanceof ExpressionStatementSyntax) {
            BoundStatement body = bindStatement(syntax.getBody());
            BoundBlockStatement block = new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));
            BoundSpawnExpression spawnExpr = new BoundSpawnExpression(block, java.util.Set.of());

            // If body is a struct constructor call, set return type for actor tracking
            if (body instanceof BoundExpressionStatement exprStmt) {
                Class<?> retType = exprStmt.getExpression().getClassType();
                if (retType == SiyoStruct.class) {
                    spawnExpr.setReturnType(SiyoStruct.class);
                    // Store actor struct name for type tracking
                    spawnExpr.setActorTypeName(_typeResolver.resolveStructType(exprStmt.getExpression()) != null
                            ? _typeResolver.resolveStructType(exprStmt.getExpression()).getName() : null);
                }
            }
            return spawnExpr;
        }

        // Block spawn must be inside scope
        if (!_insideScope) {
            _diagnostics.reportSpawnOutsideScope(syntax.getSpawnKeyword().getSpan());
            return new BoundLiteralExpression(0);
        }

        boolean wasInSpawn = _insideSpawn;
        _insideSpawn = true;

        BoundStatement body = bindStatement(syntax.getBody());
        BoundBlockStatement block = body instanceof BoundBlockStatement b
                ? b : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));

        // Detect captured variables and CHECK for mutable captures
        java.util.Set<VariableSymbol> captured = new java.util.LinkedHashSet<>();
        java.util.Set<VariableSymbol> mutableCaptures = new java.util.LinkedHashSet<>();
        BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(block);

        // Compute captures on the LOWERED (flattened) body so synthetic vars are properly detected as locals
        collectSpawnCaptures(loweredBody, captured, mutableCaptures);

        // Report mutable capture errors with helpful messages
        for (VariableSymbol var : mutableCaptures) {
            _diagnostics.reportMutableCaptureInSpawn(syntax.getSpawnKeyword().getSpan(), var.getName());
        }

        _insideSpawn = wasInSpawn;

        return new BoundSpawnExpression(loweredBody, captured);
    }

    private void collectSpawnCaptures(BoundNode node, java.util.Set<VariableSymbol> captured,
                                       java.util.Set<VariableSymbol> mutableCaptures) {
        // First: collect all locally declared variable NAMES in this body
        java.util.Set<String> localVarNames = new java.util.HashSet<>();
        collectDeclaredVarNames(node, localVarNames);

        // Then: find referenced variables that are NOT local → these are captures
        collectCapturedVarsFromBody(node, localVarNames, captured, mutableCaptures);
    }

    private void collectDeclaredVarNames(BoundNode node, java.util.Set<String> names) {
        if (node instanceof BoundVariableDeclaration decl) {
            names.add(decl.getVariable().getName());
            collectDeclaredVarNames(decl.getInitializer(), names);
        }
        if (node instanceof BoundSpawnExpression || node instanceof BoundLambdaExpression) return;
        // Explicit traversal matching collectCapturedVarsFromBody
        if (node instanceof BoundBlockStatement block) {
            for (BoundStatement stmt : block.getStatements()) { collectDeclaredVarNames(stmt, names); }
        } else if (node instanceof BoundExpressionStatement exprStmt) {
            collectDeclaredVarNames(exprStmt.getExpression(), names);
        } else if (node instanceof BoundScopeExpression scopeExpr) {
            collectDeclaredVarNames(scopeExpr.getBody(), names);
        } else {
            for (var it = node.getChildren(); it.hasNext(); ) { collectDeclaredVarNames(it.next(), names); }
        }
    }

    private void collectCapturedVarsFromBody(BoundNode node, java.util.Set<String> localVarNames,
                                              java.util.Set<VariableSymbol> captured,
                                              java.util.Set<VariableSymbol> mutableCaptures) {
        // Variable reference → check if captured
        if (node instanceof BoundVariableExpression varExpr) {
            VariableSymbol var = varExpr.getVariable();
            if (!localVarNames.contains(var.getName())) {
                captured.add(var);
                if (!var.isReadOnly() && var.getType() != SiyoChannel.class
                        && !var.getName().startsWith("_idx") && !var.getName().startsWith("_col")) {
                    mutableCaptures.add(var);
                }
            }
        }
        if (node instanceof BoundAssignmentExpression assignExpr) {
            VariableSymbol var = assignExpr.getVariable();
            if (!localVarNames.contains(var.getName())) {
                captured.add(var);
                if (!var.isReadOnly()) { mutableCaptures.add(var); }
            }
            collectCapturedVarsFromBody(assignExpr.getExpression(), localVarNames, captured, mutableCaptures);
        }
        // Don't recurse into nested spawn/lambda
        if (node instanceof BoundSpawnExpression || node instanceof BoundLambdaExpression) return;

        // Explicit sub-expression traversal (many BoundNode.getChildren() return empty)
        if (node instanceof BoundVariableDeclaration decl) {
            collectCapturedVarsFromBody(decl.getInitializer(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundExpressionStatement exprStmt) {
            collectCapturedVarsFromBody(exprStmt.getExpression(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundIndexExpression indexExpr) {
            collectCapturedVarsFromBody(indexExpr.getTarget(), localVarNames, captured, mutableCaptures);
            collectCapturedVarsFromBody(indexExpr.getIndex(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundCallExpression callExpr) {
            for (BoundExpression arg : callExpr.getArguments()) {
                collectCapturedVarsFromBody(arg, localVarNames, captured, mutableCaptures);
            }
        } else if (node instanceof BoundBinaryExpression binExpr) {
            collectCapturedVarsFromBody(binExpr.getLeft(), localVarNames, captured, mutableCaptures);
            collectCapturedVarsFromBody(binExpr.getRight(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundUnaryExpression unaryExpr) {
            collectCapturedVarsFromBody(unaryExpr.getOperand(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundConditionalGotoStatement condGoto) {
            collectCapturedVarsFromBody(condGoto.getCondition(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundJavaMethodCallExpression javaCall) {
            if (javaCall.getTarget() != null) collectCapturedVarsFromBody(javaCall.getTarget(), localVarNames, captured, mutableCaptures);
            for (BoundExpression arg : javaCall.getArguments()) {
                collectCapturedVarsFromBody(arg, localVarNames, captured, mutableCaptures);
            }
        } else if (node instanceof BoundClosureCallExpression closureCall) {
            collectCapturedVarsFromBody(closureCall.getClosure(), localVarNames, captured, mutableCaptures);
            for (BoundExpression arg : closureCall.getArguments()) {
                collectCapturedVarsFromBody(arg, localVarNames, captured, mutableCaptures);
            }
        } else if (node instanceof BoundIndexAssignmentExpression idxAssign) {
            collectCapturedVarsFromBody(idxAssign.getTarget(), localVarNames, captured, mutableCaptures);
            collectCapturedVarsFromBody(idxAssign.getIndex(), localVarNames, captured, mutableCaptures);
            collectCapturedVarsFromBody(idxAssign.getValue(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundMemberAccessExpression memberExpr) {
            collectCapturedVarsFromBody(memberExpr.getTarget(), localVarNames, captured, mutableCaptures);
        } else if (node instanceof BoundScopeExpression scopeExpr) {
            collectCapturedVarsFromBody(scopeExpr.getBody(), localVarNames, captured, mutableCaptures);
        } else {
            // Fallback: use getChildren() for anything not explicitly handled
            for (var it = node.getChildren(); it.hasNext(); ) {
                collectCapturedVarsFromBody(it.next(), localVarNames, captured, mutableCaptures);
            }
        }
    }

    private BoundExpression bindLambdaExpression(LambdaExpressionSyntax syntax) {
        // Create new scope for lambda body
        BoundScope outerScope = _scope;
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);

        // Bind parameters
        List<ParameterSymbol> parameters = new ArrayList<>();
        for (ParameterSyntax paramSyntax : syntax.getParameters()) {
            String paramName = paramSyntax.getIdentifier().getData();
            String typeName = paramSyntax.getTypeToken().getData();
            Class<?> paramType = _typeResolver.lookupType(typeName);
            if (paramType == null) paramType = Object.class;
            ParameterSymbol param = new ParameterSymbol(paramName, paramSyntax.isMutable(), paramType);
            parameters.add(param);
            _scope.tryDeclare(param);
        }

        // Return type
        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            returnType = _typeResolver.lookupType(syntax.getTypeClause().getIdentifier().getData());
        }

        // Bind body
        BoundStatement body = bindStatement(syntax.getBody());
        BoundBlockStatement blockBody = body instanceof BoundBlockStatement block
                ? block : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));

        // Detect captured variables (variables referenced in body but declared in outer scope)
        java.util.Set<VariableSymbol> captured = new java.util.LinkedHashSet<>();
        collectCapturedVars(blockBody, parameters, captured);

        // Restore scope
        _scope = outerScope;
        _moduleHandler.setScope(_scope);

        // Lower the body
        BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(blockBody);

        return new BoundLambdaExpression(parameters, loweredBody, returnType, captured);
    }

    private void collectCapturedVars(BoundNode node, List<ParameterSymbol> params,
                                      java.util.Set<VariableSymbol> captured) {
        if (node instanceof BoundVariableExpression varExpr) {
            VariableSymbol var = varExpr.getVariable();
            // Not a parameter and not a local → captured from outer scope
            boolean isParam = false;
            for (ParameterSymbol p : params) {
                if (p.getName().equals(var.getName())) { isParam = true; break; }
            }
            if (!isParam && !(var instanceof ParameterSymbol)) {
                captured.add(var);
            }
        }
        if (node instanceof BoundAssignmentExpression assignExpr) {
            VariableSymbol var = assignExpr.getVariable();
            boolean isParam = false;
            for (ParameterSymbol p : params) {
                if (p.getName().equals(var.getName())) { isParam = true; break; }
            }
            if (!isParam) captured.add(var);
        }
        for (var it = node.getChildren(); it.hasNext(); ) {
            collectCapturedVars(it.next(), params, captured);
        }
    }

    private BoundExpression bindCastExpression(CastExpressionSyntax syntax) {
        BoundExpression expr = bindExpression(syntax.getExpression());
        String targetTypeName = syntax.getTypeName().getData();

        // Look up imported Java class
        codeanalysis.JavaClassInfo targetClass = _typeResolver.getJavaClasses().get(targetTypeName);
        if (targetClass == null) {
            // Auto-resolve well-known types (String, Integer, etc.)
            targetClass = _typeResolver.resolveJavaClassForSiyoTypeName(targetTypeName);
        }
        if (targetClass == null) {
            // Check if casting to a struct/actor type — just passthrough for type tracking
            StructSymbol structType = _structTypes.get(targetTypeName);
            if (structType != null) {
                // Cast to struct/actor — no-op at runtime, enables type tracking
                return expr; // TODO: create typed cast expression for struct tracking
            }
            _diagnostics.reportUndefinedName(syntax.getTypeName().getSpan(), targetTypeName);
            return expr;
        }
        return new BoundCastExpression(expr, targetClass);
    }

    private BoundExpression bindMemberCallExpression(MemberCallExpressionSyntax syntax) {
        MemberAccessExpressionSyntax memberAccess = syntax.getMemberAccess();

        // Check if target is a module name
        if (memberAccess.getTarget() instanceof NameExpressionSyntax nameExpr) {
            String targetName = nameExpr.getIdentifierToken().getData();
            String funcName = memberAccess.getMember().getData();
            String qualifiedName = targetName + "." + funcName;

            // Lookup with qualified name, or bare name if self-referencing within a module
            String resolvedName = qualifiedName;
            if (!_scope.tryLookupFunction(qualifiedName)) {
                // Module self-reference: inside db.siyo, db.exec() → resolve as bare exec()
                String currentModule = _moduleHandler.getCurrentModuleName();
                if (currentModule != null && targetName.equals(currentModule) && _scope.tryLookupFunction(funcName)) {
                    resolvedName = funcName;
                }
            }
            if (_scope.tryLookupFunction(resolvedName)) {
                // Bind arguments first for overload resolution
                List<BoundExpression> boundArgs = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    boundArgs.add(bindExpression(argSyntax));
                }

                FunctionSymbol func = _scope.lookupFunction(resolvedName, boundArgs.size());
                if (func == null) func = _scope.lookupFunction(resolvedName);

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
                        ? javaClass.resolveConstructor(boundArgs.size(), getArgTypes(boundArgs))
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

        // Check for struct instance method: u.greet() → User.greet(u)
        if (target.getClassType() == SiyoStruct.class) {
            StructSymbol structType = _typeResolver.resolveStructType(target);
            if (structType != null) {
                String qualifiedName = structType.getName() + "." + methodName;
                if (_scope.tryLookupFunction(qualifiedName)) {
                    FunctionSymbol func = _scope.lookupFunction(qualifiedName);
                    // Desugar: prepend self (target) to args
                    List<BoundExpression> argsWithSelf = new ArrayList<>();
                    argsWithSelf.add(target);
                    argsWithSelf.addAll(boundArgs);
                    return new BoundCallExpression(func, argsWithSelf);
                }
            }
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
                if (targetClassInfo.getFullName().equals("java.lang.Object")) {
                    // Object class — method not found, fall through to dynamic dispatch
                    targetClassInfo = null;
                } else {
                    _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(),
                            targetClassInfo.getSimpleName() + "." + methodName);
                    return new BoundLiteralExpression(0);
                }
            }
            // Resolve generic return type using owner's type bindings
            resolvedReturnType = _typeResolver.resolveMethodReturnType(resolved, targetResolvedType);
        }

        return new BoundJavaMethodCallExpression(targetClassInfo, target, methodName, boundArgs, resolved, resolvedReturnType);
    }

    private BoundStatement bindSendStatement(SendStatementSyntax syntax) {
        BoundExpression expr = bindExpression(syntax.getExpression());
        // Validate: send must target an actor method call
        boolean isActorCall = false;
        if (expr instanceof BoundCallExpression callExpr) {
            FunctionSymbol func = callExpr.getFunction();
            if (func.getName().contains(".")
                    && func.getParameters().size() > 0
                    && func.getParameters().get(0).getName().equals("self")) {
                String typeName = func.getName().substring(0, func.getName().indexOf('.'));
                if (_structTypes.containsKey(typeName) && _structTypes.get(typeName).isActor()) {
                    isActorCall = true;
                }
            }
        }
        // Object-typed actor refs go through BoundJavaMethodCallExpression — allow those too
        if (expr instanceof BoundJavaMethodCallExpression javaCall && javaCall.getTarget() != null
                && javaCall.getResolvedSignature() == null) {
            isActorCall = true; // dynamic dispatch — could be actor at runtime
        }
        if (!isActorCall) {
            _diagnostics.reportSendOnNonActor(syntax.getKeyword().getSpan());
        }
        return new BoundSendStatement(expr);
    }

    private BoundStatement bindActorDeclaration(ActorDeclarationSyntax syntax) {
        // No-op at bind time — actor is registered in first pass
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    private BoundStatement bindImplDeclaration(ImplDeclarationSyntax syntax) {
        String structName = syntax.getTypeName().getData();
        StructSymbol structType = _structTypes.get(structName);

        for (FunctionDeclarationSyntax method : syntax.getMethods()) {
            String qualifiedName = structName + "." + method.getIdentifier().getData();
            if (!_scope.tryLookupFunction(qualifiedName)) continue;
            FunctionSymbol func = _scope.lookupFunction(qualifiedName);

            // Bind the method body in a new scope with parameters
            _scope = new BoundScope(_scope);
            _moduleHandler.setScope(_scope);
            for (ParameterSymbol param : func.getParameters()) {
                _scope.tryDeclare(param);
                if (param.getName().equals("self") && structType != null) {
                    _typeResolver.trackStructType(param, structType);
                }
            }

            _currentFunction = func;
            BoundStatement body = bindStatement(method.getBody());
            _currentFunction = null;

            _scope = _scope.getParent();
            _moduleHandler.setScope(_scope);

            BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(
                    body instanceof BoundBlockStatement block ? block : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body))));
            _functionBodies.put(func, loweredBody);
        }

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
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
