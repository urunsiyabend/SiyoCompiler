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
import codeanalysis.SiyoMap;
import codeanalysis.SiyoSet;
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
    private boolean _atFileTopLevel = true;
    final Map<FunctionSymbol, BoundBlockStatement> _functionBodies = new HashMap<>();
    private final Stack<LoopLabels> _loopStack = new Stack<>();
    // One generation for this file's type tables: see VersionedMap.
    private final VersionedMap.Version _typeVersion = new VersionedMap.Version();
    private final VersionedMap<String, StructSymbol> _structTypes = new StructTable(_typeVersion);
    private final VersionedMap<String, codeanalysis.UnionSymbol> _unionTypes = new VersionedMap<>(_typeVersion);

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
        _typeResolver = new TypeResolver(_structTypes, _unionTypes, _typeVersion);
        _moduleHandler = new ModuleHandler(_structTypes, _unionTypes, _typeResolver, _functionBodies);
        _moduleHandler.setDiagnostics(_diagnostics);
        _moduleHandler.setScope(_scope);

        for (FunctionSymbol builtin : BuiltinFunctions.getAll()) {
            _scope.tryDeclareFunction(builtin);
        }
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax) {
        return bindGlobalScope(previous, syntax, new ModuleRegistry(), null, false);
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax, ModuleRegistry registry, String filePath) {
        return bindGlobalScope(previous, syntax, registry, filePath, true);
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax, ModuleRegistry registry, String filePath, boolean enforceTopLevel) {
        var parentScope = createParentScopes(previous);
        Binder binder = new Binder(parentScope);
        binder._moduleHandler.setRegistry(registry);
        binder._moduleHandler.setFilePath(filePath);
        binder._moduleHandler.setRoot(true);
        if (!enforceTopLevel) binder._atFileTopLevel = false;
        BoundStatement statement = binder.bindStatement(syntax.getStatement());
        Iterable<FunctionSymbol> functions = binder._scope.getDeclaredFunctions();
        Map<FunctionSymbol, BoundBlockStatement> functionBodies = binder._functionBodies;
        Iterable<VariableSymbol> variables = binder._scope.getDeclaredVariables();
        DiagnosticBox diagnostics = binder._diagnostics;
        BoundGlobalScope scope = new BoundGlobalScope(previous, diagnostics, functions, functionBodies, variables, statement);
        scope.setStructTypes(binder._structTypes);
        scope.setImportedClassNames(binder._moduleHandler.getImportedClassNames());
        return scope;
    }

    /** The diagnostics this binder reports into. */
    public codeanalysis.DiagnosticBox getDiagnostics() {
        return _diagnostics;
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
        // If the root of the file is a non-block statement, `bindBlockStatement`
        // never fires, so perform the top-level check here once.
        if (_atFileTopLevel && syntax.getType() != SyntaxType.BlockStatement) {
            if (!isAllowedAtTopLevel(syntax)) {
                _diagnostics.reportTopLevelNotAllowed(syntax.getSpan());
            }
            // A file of one declaration: what its nested imports bring in is
            // known before its body is bound, as for any other file.
            if (!(syntax instanceof ImportStatementSyntax)) anticipateNestedImports(syntax);
            _atFileTopLevel = false; // recursive calls are nested, not top-level
        }
        BoundStatement result = bindStatementInternal(syntax);
        if (result != null) {
            try { result.setSourceOffset(syntax.getSpan().getStart()); } catch (Exception ignored) {}
        }
        return result;
    }

    private BoundStatement bindStatementInternal(StatementSyntax syntax) {
        // Everything declared outside any function, a method included, is a global.
        if (_currentFunction == null) _fileLevelScope = _scope;
        return switch (syntax.getType()) {
            case BlockStatement -> bindBlockStatement((BlockStatementSyntax)syntax);
            case ExpressionStatement -> bindExpressionStatement((ExpressionStatementSyntax)syntax);
            case VariableDeclaration -> bindVariableDeclaration((VariableDeclarationSyntax)syntax);
            case IfStatement -> bindIfStatement((IfStatementSyntax)syntax);
            case WhileStatement -> bindWhileStatement((WhileStatementSyntax)syntax);
            case DoWhileStatement -> bindDoWhileStatement((DoWhileStatementSyntax)syntax);
            case ForStatement -> bindForStatement((ForStatementSyntax)syntax);
            case FunctionDeclaration -> bindFunctionDeclaration((FunctionDeclarationSyntax)syntax);
            case ReturnStatement -> bindReturnStatement((ReturnStatementSyntax)syntax);
            case ForInStatement -> bindForInStatement((ForInStatementSyntax)syntax);
            case BreakStatement -> bindBreakStatement((BreakStatementSyntax)syntax);
            case ContinueStatement -> bindContinueStatement((ContinueStatementSyntax)syntax);
            case StructDeclaration -> _moduleHandler.bindStructDeclaration((StructDeclarationSyntax)syntax);
            case EnumDeclaration -> _moduleHandler.bindEnumDeclaration((EnumDeclarationSyntax)syntax);
            case TypeDeclaration -> _moduleHandler.bindTypeDeclaration((TypeDeclarationSyntax)syntax);
            case TryCatchStatement -> bindTryCatchStatement((TryCatchStatementSyntax)syntax);
            case ThrowStatement -> bindThrowStatement((ThrowStatementSyntax)syntax);
            case ImplDeclaration -> bindImplDeclaration((ImplDeclarationSyntax)syntax);
            case InterfaceDeclaration -> bindInterfaceDeclaration((InterfaceDeclarationSyntax)syntax);
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
        boolean wasFileTopLevel = _atFileTopLevel;
        _atFileTopLevel = false;

        ArrayList<BoundStatement> statements = new ArrayList<>();
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);

        // Every type this block declares is known by kind before any is
        // registered, so a signature may name one written further down:
        // fn s() -> Later above struct Later returned null.
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            if (statementSyntax instanceof StructDeclarationSyntax structSyntax) {
                _typeResolver.declareAhead(structSyntax.getIdentifier().getData(), SiyoStruct.class);
            } else if (statementSyntax instanceof ActorDeclarationSyntax actorSyntax) {
                _typeResolver.declareAhead(actorSyntax.getIdentifier().getData(), SiyoStruct.class);
            } else if (statementSyntax instanceof TypeDeclarationSyntax typeSyntax) {
                _typeResolver.declareAhead(typeSyntax.getIdentifier().getData(), codeanalysis.SiyoUnion.class);
            } else if (statementSyntax instanceof InterfaceDeclarationSyntax interfaceSyntax) {
                _typeResolver.declareAhead(interfaceSyntax.getIdentifier().getData(), SiyoStruct.class);
            }
        }

        // First pass: process imports, register function/struct/enum declarations
        List<ImplDeclarationSyntax> pendingImplementors = new ArrayList<>();
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            if (wasFileTopLevel) {
                recordDeclarationVisibility(statementSyntax);
            }
            if (statementSyntax instanceof ImportStatementSyntax importSyntax) {
                bindImportStatement(importSyntax);
            } else if (statementSyntax instanceof JavaImportStatementSyntax javaImportSyntax) {
                bindJavaImportStatement(javaImportSyntax);
            } else if (statementSyntax instanceof FunctionDeclarationSyntax funcSyntax) {
                if (_currentFunction != null) _hoistedNested.put(funcSyntax, declareNestedFunction(funcSyntax));
                else _moduleHandler.registerFunctionDeclaration(funcSyntax);
            } else if (statementSyntax instanceof StructDeclarationSyntax structSyntax) {
                _moduleHandler.registerStructDeclaration(structSyntax);
            } else if (statementSyntax instanceof EnumDeclarationSyntax enumSyntax) {
                _moduleHandler.registerEnumDeclaration(enumSyntax);
            } else if (statementSyntax instanceof TypeDeclarationSyntax typeSyntax) {
                _moduleHandler.registerTypeDeclaration(typeSyntax);
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
                symbol.setOriginModule(_moduleHandler.getFilePath());
                _structTypes.put(name, symbol);
            } else if (statementSyntax instanceof InterfaceDeclarationSyntax interfaceSyntax) {
                registerInterfaceDeclaration(interfaceSyntax);
            } else if (statementSyntax instanceof ImplDeclarationSyntax implSyntax) {
                _moduleHandler.registerImplDeclaration(implSyntax);
                if (implSyntax.getInterfaceName() != null) {
                    pendingImplementors.add(implSyntax);
                }
            }
        }

        // What an import inside any function or lambda here will bring in is known
        // before any body is bound (see ModuleHandler.anticipateImport).
        if (wasFileTopLevel) {
            for (StatementSyntax statementSyntax : syntax.getStatements()) {
                if (!(statementSyntax instanceof ImportStatementSyntax)) anticipateNestedImports(statementSyntax);
            }
        }

        // What the names in this file's signatures mean: its tables before any
        // body, and so any import inside a function, is bound.
        if (wasFileTopLevel) _typeResolver.markDeclarationsComplete();

        // Which structs implement which interface is settled before any body is
        // bound. A function that takes an interface may be written above the
        // impl block that satisfies it, and it still has to see it.
        for (ImplDeclarationSyntax implSyntax : pendingImplementors) {
            codeanalysis.InterfaceSymbol declared =
                    _typeResolver.getInterfaceTypes().get(implSyntax.getInterfaceName().getData());
            if (declared != null) {
                declared.addImplementor(implSyntax.getTypeName().getData());
            }
        }

        // Second pass: bind all statements (function bodies can now reference each other).
        // At file top level, only declarations, imports, and variable declarations are allowed.
        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            if (wasFileTopLevel && !isAllowedAtTopLevel(statementSyntax)) {
                _diagnostics.reportTopLevelNotAllowed(statementSyntax.getSpan());
                continue;
            }
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }

        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        return new BoundBlockStatement(statements);
    }

    /**
     * Finds every import nested in a declaration: in a function or method body, a
     * lambda, a block, a match arm. Syntax nodes do not all list every child, so
     * the walk follows each node's fields that hold syntax.
     */
    // The fields of a syntax class that may hold syntax, found once per class.
    private static final ClassValue<List<java.lang.reflect.Field>> SYNTAX_FIELDS = new ClassValue<>() {
        @Override
        protected List<java.lang.reflect.Field> computeValue(Class<?> nodeClass) {
            List<java.lang.reflect.Field> fields = new ArrayList<>();
            for (Class<?> type = nodeClass; type != null && type != Object.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    Class<?> fieldType = field.getType();
                    if (!codeanalysis.syntax.SyntaxNode.class.isAssignableFrom(fieldType)
                            && !Iterable.class.isAssignableFrom(fieldType)
                            && !fieldType.getPackageName().equals("codeanalysis.syntax")) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // A field that cannot be opened holds no import we could see.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private void anticipateNestedImports(Object node) {
        // Found in any order, compiled in the order the binder would have reached
        // them, which is their order in the source: a module runs its top level
        // when it is first compiled, so the order is observable.
        List<ImportStatementSyntax> found = new ArrayList<>();
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Object> pending = new java.util.ArrayDeque<>();
        pending.add(node);
        while (!pending.isEmpty()) {
            Object next = pending.pop();
            if (next == null || !seen.add(next)) continue;
            if (next instanceof ImportStatementSyntax nested) {
                found.add(nested);
                continue;
            }
            if (next instanceof Iterable<?> items) {
                for (Object item : items) {
                    if (item != null) pending.add(item);
                }
                continue;
            }
            if (next instanceof SyntaxToken || !(next instanceof codeanalysis.syntax.SyntaxNode
                    || next.getClass().getPackageName().equals("codeanalysis.syntax"))) {
                continue;
            }
            for (java.lang.reflect.Field field : SYNTAX_FIELDS.get(next.getClass())) {
                try {
                    Object value = field.get(next);
                    if (value != null) pending.add(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // A field that cannot be read holds no import we could see.
                }
            }
        }
        found.sort(java.util.Comparator.comparingInt(nested -> nested.getSpan().getStart()));
        for (ImportStatementSyntax nested : found) _moduleHandler.anticipateImport(nested);
    }

    /**
     * Notes whether a top-level declaration is exported by the file being
     * bound. Only the top level can export: a declaration nested inside a
     * function is reachable from nowhere else regardless.
     *
     * @param statement The top-level statement.
     */
    private void recordDeclarationVisibility(StatementSyntax statement) {
        String name = declaredName(statement);
        if (name != null) {
            _moduleHandler.recordVisibility(name, statement.isPublic());
        }
    }

    /**
     * The name a top-level declaration introduces, or null when the statement
     * declares nothing that a module could export.
     *
     * @param statement The top-level statement.
     * @return The declared name, or null.
     */
    private static String declaredName(StatementSyntax statement) {
        if (statement instanceof FunctionDeclarationSyntax function) {
            return function.getIdentifier().getData();
        }
        if (statement instanceof StructDeclarationSyntax struct) {
            return struct.getIdentifier().getData();
        }
        if (statement instanceof EnumDeclarationSyntax enumeration) {
            return enumeration.getIdentifier().getData();
        }
        if (statement instanceof TypeDeclarationSyntax typeDeclaration) {
            return typeDeclaration.getIdentifier().getData();
        }
        if (statement instanceof ActorDeclarationSyntax actor) {
            return actor.getIdentifier().getData();
        }
        if (statement instanceof InterfaceDeclarationSyntax declaredInterface) {
            return declaredInterface.getIdentifier().getData();
        }
        if (statement instanceof VariableDeclarationSyntax variable) {
            return variable.getIdentifier().getData();
        }
        return null;
    }

    /**
     * Reports a type name that resolved to nothing.
     *
     * <p>A module that declares the type but does not export it is the more
     * likely explanation than a typo, so it is said outright.
     *
     * @param span     The span of the name.
     * @param typeName The name as written.
     */
    private void reportMissingType(codeanalysis.text.TextSpan span, String typeName) {
        String owner = _moduleHandler.privateMemberOwner(typeName);
        if (owner != null) {
            _diagnostics.reportPrivateMember(span, typeName, owner);
            return;
        }
        _diagnostics.reportUndefinedType(span, typeName);
    }

    private static boolean isAllowedAtTopLevel(StatementSyntax stmt) {
        return switch (stmt.getType()) {
            case FunctionDeclaration,
                 StructDeclaration,
                 EnumDeclaration,
                 TypeDeclaration,
                 ActorDeclaration,
                 ImplDeclaration,
                 InterfaceDeclaration,
                 ImportStatement,
                 JavaImportStatement,
                 VariableDeclaration -> true;
            default -> false;
        };
    }

    /**
     * Binds the expression statement syntax and returns the corresponding bound expression statement.
     *
     * @param syntax The expression statement syntax to bind.
     * @return The bound expression statement.
     */
    private BoundStatement bindExpressionStatement(ExpressionStatementSyntax syntax) {
        // A match written as a statement is run for its arms' effects.
        BoundExpression expression = syntax.getExpression() instanceof MatchExpressionSyntax match
                ? bindMatchExpression(match, false)
                : bindExpression(syntax.getExpression());
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
        // The names the variable's type is written with mean what they mean here,
        // before an import inside the initializer can change them.
        long generation = _typeResolver.generation();
        BoundExpression initializer = bindExpressionWithTypeHint(syntax.getInitializer(), syntax.getTypeAnnotation());
        requireValue(initializer, syntax.getInitializer());

        // A declared type is binding, not decoration: it gives the variable its
        // type and the initializer is checked against it. It used to be parsed
        // and discarded, so `imut x: string = 5` compiled and x was an int.
        Class<?> variableType = initializer.getClassType();
        codeanalysis.syntax.SyntaxToken annotation = syntax.getTypeAnnotation();
        if (annotation != null) {
            Class<?> declaredType = _typeResolver.lookupType(annotation.getData());
            if (declaredType != null) {
                variableType = declaredType;
                if (!isAssignableToDeclaredType(initializer, declaredType)) {
                    _diagnostics.reportCannotConvert(syntax.getInitializer().getSpan(),
                            initializer.getClassType(), declaredType);
                }
            }
        }
        // `mut h: long = 0` holds a long from the start; interpreted it stayed an
        // int and later additions wrapped.
        if (annotation != null) initializer = widenLiteral(widenTo(initializer, variableType), annotation.getData());
        if (annotation != null) checkPayloadAgainst(initializer, annotation.getData(), syntax.getInitializer().getSpan());
        VariableSymbol variableSymbol = new VariableSymbol(name, isReadOnly, variableType);
        if (annotation != null) {
            variableSymbol.setDeclaredTypeName(annotation.getData());
            // A struct-derived value under an annotation: the variable holds what
            // the annotation says, and its aliases do too. Reads stay checked.
            if (_typeResolver.isStructDerived(initializer)) _typeResolver.markStructDerived(variableSymbol, initializer);
            if (variableType == SiyoClosure.class) {
                checkAgainstFunctionType(initializer, annotation.getData(),
                        syntax.getInitializer().getSpan());
            }
        } else if (_typeResolver.isStructDerived(initializer) && _typeResolver.declaredTypeNameOf(initializer) != null) {
            // Whatever form a struct-derived value arrives in (an alias, an index,
            // an if or match, a call, a loop), the variable holds its whole type.
            variableSymbol.setDeclaredTypeName(_typeResolver.declaredTypeNameOf(initializer));
            _typeResolver.markStructDerived(variableSymbol, initializer);
        } else if (initializer instanceof BoundLambdaExpression lambda) {
            // With no annotation the lambda's own shape is the declaration, so
            // a later call through the name is still checked.
            variableSymbol.setDeclaredTypeName(signatureNameOf(lambda));
        } else if (initializer instanceof BoundIndexExpression || initializer instanceof BoundCallExpression
                || initializer instanceof BoundMemberAccessExpression
                || initializer instanceof BoundStructLiteralExpression
                || initializer instanceof BoundArrayLiteralExpression
                || initializer instanceof BoundVariableExpression
                || initializer instanceof BoundClosureCallExpression) {
            // imut recs = groups["e"] is whatever groups declares its values to be.
            // A field or a literal is followed only for the struct identity it
            // carries, as in imut its = g.items on a Group<Row>.
            String inherited = _typeResolver.declaredTypeNameOf(initializer);
            boolean followed = !(initializer instanceof BoundMemberAccessExpression)
                    && !(initializer instanceof BoundStructLiteralExpression)
                    && !(initializer instanceof BoundArrayLiteralExpression)
                    && !(initializer instanceof BoundVariableExpression);
            // A plain alias, imut c = b, carries a generic struct's type
            // arguments, and nothing 0.9.0 would not have known.
            boolean alias = initializer instanceof BoundVariableExpression;
            boolean structDerived = annotation == null && _typeResolver.isStructDerived(initializer);
            if (inherited != null && (structDerived || (alias ? _typeResolver.mentionsGenericStruct(inherited)
                    : (followed || namesADeclaredType(inherited))))) {
                variableSymbol.setDeclaredTypeName(inherited);
            }
            // The variable holds what the struct-derived value does, however it is
            // later aliased, filtered or indexed.
            if (structDerived && inherited != null) _typeResolver.markStructDerived(variableSymbol, initializer);
        }

        if (!_scope.tryDeclare(variableSymbol)) {
            _diagnostics.reportVariableAlreadyDeclared(syntax.getIdentifier().getSpan(), name);
        }

        // Track element/struct types for type resolution
        if (initializer instanceof BoundArrayLiteralExpression arr) {
            // The element's struct identity comes from whatever the first element
            // is — a literal, a variable, or a call — not only a struct literal,
            // so `mut routes: Route[] = [r]` keeps Route.
            StructSymbol elementStruct = arr.getElements().isEmpty()
                    ? null
                    : _typeResolver.resolveStructType(arr.getElements().get(0));
            if (elementStruct == null && annotation != null) {
                String elementName = TypeResolver.elementTypeNameOf(annotation.getData());
                if (elementName != null) elementStruct = _structTypes.get(elementName);
            }
            if (elementStruct != null) {
                _typeResolver.trackArrayType(variableSymbol, arr.getElementType(), elementStruct);
            } else {
                _typeResolver.trackArrayType(variableSymbol, arr.getElementType());
            }
        } else if (initializer instanceof BoundIndexExpression indexExpr && indexExpr.getClassType() == SiyoStruct.class) {
            // Track struct type from array index: mut todo = todos[i]
            StructSymbol elemStruct = _typeResolver.resolveStructType(indexExpr);
            if (elemStruct != null) _typeResolver.trackStructType(variableSymbol, elemStruct);
        } else if (initializer instanceof BoundIndexExpression arrayIndex && arrayIndex.getClassType() == SiyoArray.class) {
            // An array held in a container: imut recs = groups["e"]
            Class<?> elemType = _typeResolver.resolveArrayElementType(arrayIndex);
            StructSymbol elemStruct = _typeResolver.resolveStructTypeFromCollection(arrayIndex);
            if (elemStruct != null) _typeResolver.trackArrayType(variableSymbol, elemType, elemStruct);
            else _typeResolver.trackArrayType(variableSymbol, elemType);
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
        } else if (isPopCall(initializer) && initializer.getClassType() == SiyoArray.class) {
            // An array popped from an array of arrays: imut row = pop(rows)
            String elementName = TypeResolver.elementTypeNameOf(_typeResolver.declaredTypeNameOf(initializer));
            Class<?> elemType = elementName != null ? _typeResolver.lookupType(elementName) : null;
            StructSymbol elemStruct = elementName != null ? _structTypes.get(elementName) : null;
            if (elemStruct != null) _typeResolver.trackArrayType(variableSymbol, elemType, elemStruct);
            else _typeResolver.trackArrayType(variableSymbol, elemType != null ? elemType : Object.class);
        } else if (initializer instanceof BoundCallExpression callExpr2 && callExpr2.getClassType() == SiyoArray.class) {
            // Track element type (and struct identity) for functions returning arrays.
            Class<?> elemType = _typeResolver.resolveArrayElementType(initializer);
            StructSymbol elemStruct = _typeResolver.resolveStructTypeFromCollection(initializer);
            if (elemStruct != null) _typeResolver.trackArrayType(variableSymbol, elemType, elemStruct);
            else _typeResolver.trackArrayType(variableSymbol, elemType);
        } else if (initializer instanceof BoundStructLiteralExpression structLit) {
            _typeResolver.trackStructType(variableSymbol, structLit.getStructType());
        } else if (initializer.getClassType() == codeanalysis.SiyoUnion.class) {
            // Track which sum type the value belongs to, so a match over the
            // variable can be checked against the variants it declares.
            codeanalysis.UnionSymbol union = _typeResolver.resolveUnionType(initializer);
            if (union == null && annotation != null) {
                union = _typeResolver.getUnionTypes().get(annotation.getData());
            }
            if (union != null) _typeResolver.trackUnionType(variableSymbol, union);
        } else if (initializer instanceof BoundJavaMethodCallExpression javaArrCall && javaArrCall.getClassType() == SiyoArray.class) {
            // Java methods returning arrays (e.g. File.listFiles()) → track as SiyoArray
            _typeResolver.trackArrayType(variableSymbol, Object.class);
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

        // What the value was inferred from. A collection annotation names it afresh,
        // as 0.9.0 did; a struct one never typed an erased value in 0.9.0, so the
        // value keeps what it was inferred from.
        // A collection annotation over a value only 0.10.0 typed restates that typing.
        if (syntax.getTypeAnnotation() == null || !TypeResolver.namesACollection(syntax.getTypeAnnotation().getData())
                || typedOnlyBy010ForAnnotation(initializer)) {
            variableSymbol.addProvenance(_typeResolver.provenanceOf(initializer));
            shareStorage(variableSymbol, initializer);
        }
        _typeResolver.stamp(variableSymbol, generation, !variableSymbol.getProvenance().isEmpty());
        // A struct literal, or a call typed by its declared return, is typed as
        // 0.9.0 typed it; an alias, an element or field, or a generic or imported
        // call's inferred result of a generic struct is typed only by 0.10.0.
        if (syntax.getTypeAnnotation() == null && typedOnlyBy010(initializer)) {
            markGenericTypeInferred(variableSymbol);
        }
        if (syntax.getTypeAnnotation() == null) markStructTypeInferred(variableSymbol, initializer);
        // 0.9.0 typed a collection annotation over an ordinary call or a literal,
        // not over a variable, an element, an if or match, or a generic or closure
        // call: b: Array<W> = a is typed only by 0.10.0.
        if (syntax.getTypeAnnotation() != null && TypeResolver.namesACollection(syntax.getTypeAnnotation().getData())
                && typedOnlyBy010ForAnnotation(initializer) && variableSymbol.getOriginStructs() != null) {
            String element = TypeResolver.elementTypeNameOf(syntax.getTypeAnnotation().getData());
            if (element == null && syntax.getTypeAnnotation().getData().startsWith("Map<")) {
                List<String> arguments = TypeResolver.typeArgumentsOf(syntax.getTypeAnnotation().getData());
                element = arguments.size() == 2 ? arguments.get(1) : null;
            }
            if (element != null && _structTypes.containsKey(element)) variableSymbol.setGenericTypeInferred(true);
        }
        return new BoundVariableDeclaration(variableSymbol, initializer);
    }

    /**
     * Binds the if statement syntax and returns the corresponding bound if statement.
     *
     * @param syntax The if statement syntax to bind.
     * @return The bound if statement.
     */
    /**
     * Whether an initializer may be stored in a variable of the declared type.
     *
     * <p>An erased {@code object} is accepted — that is the whole point of
     * writing the annotation — as is null for a reference type, and the numeric
     * widenings Siyo already performs elsewhere.
     */
    private boolean isAssignableToDeclaredType(BoundExpression initializer, Class<?> declaredType) {
        Class<?> actual = initializer.getClassType();
        if (actual == null || actual == declaredType || declaredType == Object.class) return true;
        if (actual == Object.class) return true; // narrowing an erased value is the point
        if (initializer instanceof BoundLiteralExpression literal && literal.getValue() == null) {
            return declaredType != Integer.class && declaredType != Long.class
                    && declaredType != Double.class && declaredType != Boolean.class;
        }
        if (declaredType == Long.class && actual == Integer.class) return true;
        if (declaredType == Double.class && (actual == Integer.class || actual == Long.class)) return true;
        // A struct value and a Java object both surface as reference types whose
        // precise identity is tracked separately.
        if (declaredType == SiyoStruct.class && actual == SiyoStruct.class) return true;
        return false;
    }

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
    /**
     * Binds a do-while loop. It is a while loop whose body runs before the
     * condition is first checked, so it is bound as one and marked as such.
     *
     * @param syntax The do-while statement.
     * @return The bound loop.
     */
    private BoundStatement bindDoWhileStatement(DoWhileStatementSyntax syntax) {
        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.pop();
        BoundExpression condition = bindExpression(syntax.getCondition(), Boolean.class);
        return new BoundWhileStatement(condition, body, breakLabel, continueLabel, true);
    }

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
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        BoundStatement initializer = bindStatement(syntax.getInitializer());
        BoundExpression condition = bindExpression(syntax.getCondition(), Boolean.class);
        BoundExpression iterator = bindExpression(syntax.getIterator());
        LabelSymbol breakLabel = generateLabel("break");
        LabelSymbol continueLabel = generateLabel("continue");
        _loopStack.push(new LoopLabels(breakLabel, continueLabel));
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.pop();
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
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
        BoundExpression result = bindExpressionInternal(syntax);
        if (result != null) {
            try { result.setSourceOffset(syntax.getSpan().getStart()); } catch (Exception ignored) {}
            recordStoredProvenance(result);
        }
        return result;
    }

    /**
     * A value stored into a variable, an element or field of one, or passed to a
     * builtin with one first (push, insert, ...), adds what it was inferred from
     * to that variable, which never loses it.
     */
    private void recordStoredProvenance(BoundExpression result) {
        if (result instanceof BoundAssignmentExpression assignment) {
            // The variable now is the value: an alias of what it aliases.
            assignment.getVariable().addProvenance(_typeResolver.provenanceOf(assignment.getExpression()));
            shareStorage(assignment.getVariable(), assignment.getExpression());
        } else if (result instanceof BoundIndexAssignmentExpression store) {
            VariableSymbol root = rootVariable(store.getTarget());
            if (root != null) store(root, store.getValue(), !(store.getTarget() instanceof BoundVariableExpression));
        } else if (result instanceof BoundMemberAssignmentExpression store) {
            VariableSymbol root = rootVariable(store.getTarget());
            if (root != null) store(root, store.getValue(), !(store.getTarget() instanceof BoundVariableExpression));
        } else if (result instanceof BoundCallExpression call && !BuiltinFunctions.isBuiltin(call.getFunction())
                && call.getArguments().size() > 1) {
            // A function may store any argument into another: put(w, p) is w.value = p.
            storeArgumentsIntoEachOther(call.getArguments());
        } else if (result instanceof BoundClosureCallExpression call && !call.getArguments().isEmpty()) {
            List<BoundExpression> values = new ArrayList<>(call.getArguments());
            values.add(call.getClosure());
            storeArgumentsIntoEachOther(values);
        } else if (result instanceof BoundCallExpression call && BuiltinFunctions.isBuiltin(call.getFunction())
                && call.getArguments().size() > 1) {
            VariableSymbol root = rootVariable(call.getArguments().get(0));
            if (root != null) {
                boolean deep = !(call.getArguments().get(0) instanceof BoundVariableExpression);
                for (int i = 1; i < call.getArguments().size(); i++) store(root, call.getArguments().get(i), deep);
            }
        }
    }

    /**
     * A call may store any of its values into a slot of an argument that can
     * hold it: put(w, p) may be w.value = p. A closure called counts as one of
     * its values, since it may store what it captured. An argument whose type
     * holds only numbers and strings, or nothing of the stored value's type,
     * receives nothing.
     */
    private void storeArgumentsIntoEachOther(List<BoundExpression> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            VariableSymbol root = rootVariable(arguments.get(i));
            if (root == null) continue;
            String holder = storageTypeNameOf(arguments.get(i));
            for (int j = 0; j < arguments.size(); j++) {
                if (i == j || !_typeResolver.canHold(holder, storageTypeNameOf(arguments.get(j)))) continue;
                store(root, arguments.get(j), !(arguments.get(i) instanceof BoundVariableExpression));
            }
        }
    }

    /** The type a value is known to have, for what it can hold; null when unknown. */
    private String storageTypeNameOf(BoundExpression value) {
        if (value instanceof BoundLambdaExpression) return null;
        String declared = _typeResolver.declaredTypeNameOf(value);
        if (declared != null) return declared;
        if (value.getClassType() == SiyoStruct.class) {
            StructSymbol struct = _typeResolver.resolveStructType(value);
            if (struct != null) return struct.getName();
        }
        if (value.getClassType() == SiyoArray.class) {
            StructSymbol element = _typeResolver.resolveStructTypeFromCollection(value);
            if (element != null) return "Array<" + element.getName() + ">";
        }
        return null;
    }

    /**
     * A value stored into a variable's storage: the variable now holds what the
     * value was inferred from, and shares storage with what the value shares,
     * so every alias of either sees it.
     */
    private void store(VariableSymbol into, BoundExpression value, boolean deep) {
        java.util.Set<Object> roots = _typeResolver.provenanceOf(value);
        // Into an object it holds: that object may be anything stored into it.
        if (deep) into.addProvenanceToContents(roots);
        else into.addProvenance(roots);
        TypeResolver.Storage storage = _typeResolver.storageOf(value);
        for (VariableSymbol held : storage.same) if (held != into) held.heldIn(into);
        for (VariableSymbol held : storage.element) if (held != into) held.heldIn(into);
        for (VariableSymbol held : storage.held) if (held != into) held.heldIn(into);
    }

    /**
     * A variable now holding a value: the same object as what the value is an
     * alias, element or field of, and the holder of what a literal in it holds.
     */
    private void shareStorage(VariableSymbol variable, BoundExpression value) {
        TypeResolver.Storage storage = _typeResolver.storageOf(value);
        for (VariableSymbol shared : storage.same) if (shared != variable) variable.shareStorageWith(shared);
        for (VariableSymbol container : storage.element) if (container != variable) variable.elementOf(container);
        for (VariableSymbol held : storage.held) if (held != variable) held.heldIn(variable);
    }

    private void markGenericTypeInferred(VariableSymbol variable) {
        String name = variable.getDeclaredTypeName();
        if (name != null && variable.getOriginStructs() != null && !name.startsWith("fn")
                && _typeResolver.mentionsGenericStruct(name)) {
            variable.setGenericTypeInferred(true);
        }
    }

    /**
     * A struct-typed local 0.9.0 did not type at all: one taken from a field
     * (0.9.0 tracked no struct for it), or from a generic or imported call's
     * result. Its struct identity is 0.10.0's, as for a generic struct's alias.
     */
    private void markStructTypeInferred(VariableSymbol variable, BoundExpression initializer) {
        String name = variable.getDeclaredTypeName();
        if (name == null || variable.getOriginStructs() == null || name.startsWith("fn")) return;
        BoundExpression value = initializer instanceof BoundConversionExpression conversion
                ? conversion.getExpression() : initializer;
        boolean onlyBy010 = value instanceof BoundMemberAccessExpression
                || value instanceof BoundCallExpression call && !BuiltinFunctions.isBuiltin(call.getFunction())
                        && (!call.getFunction().getTypeParameters().isEmpty()
                            || call.getFunction().getReturnOriginStructs() != null);
        if (onlyBy010 && _structTypes.containsKey(name)) variable.setGenericTypeInferred(true);
    }

    /** Whether a value's type name comes from 0.10.0's inference rather than from what 0.9.0 typed. */
    private boolean typedOnlyBy010(BoundExpression value) {
        if (value instanceof BoundConversionExpression conversion) return typedOnlyBy010(conversion.getExpression());
        if (value instanceof BoundVariableExpression || value instanceof BoundIndexExpression
                || value instanceof BoundMemberAccessExpression) {
            return true;
        }
        if (!(value instanceof BoundCallExpression call)) return false;
        // A builtin over a literal of anything but struct literals (pop([w]),
        // filter([w], f), pop([mk()])): 0.9.0 typed an element of a literal of
        // struct literals only.
        if (BuiltinFunctions.isBuiltin(call.getFunction())) {
            for (BoundExpression argument : call.getArguments()) {
                if (literalOfNonLiterals(argument)) return true;
            }
            return false;
        }
        return call.getResultTypeName() != null || call.getFunction().getReturnOriginStructs() != null;
    }

    /** Whether a collection annotation over this value types what 0.9.0 did not: see bindVariableDeclaration. */
    private static boolean typedOnlyBy010ForAnnotation(BoundExpression value) {
        if (value instanceof BoundConversionExpression conversion) return typedOnlyBy010ForAnnotation(conversion.getExpression());
        return value instanceof BoundVariableExpression || value instanceof BoundIndexExpression
                || value instanceof BoundMemberAccessExpression || value instanceof BoundMatchExpression
                || value instanceof BoundClosureCallExpression
                || value instanceof BoundCallExpression call && !BuiltinFunctions.isBuiltin(call.getFunction())
                        && !call.getFunction().getTypeParameters().isEmpty();
    }

    private static boolean literalOfNonLiterals(BoundExpression value) {
        List<BoundExpression> elements = value instanceof BoundArrayLiteralExpression array ? array.getElements()
                : value instanceof BoundSetLiteralExpression set ? set.getElements() : null;
        if (elements == null) {
            return value instanceof BoundCallExpression inner && BuiltinFunctions.isBuiltin(inner.getFunction())
                    && inner.getArguments().stream().anyMatch(Binder::literalOfNonLiterals);
        }
        for (BoundExpression element : elements) {
            if (!(element instanceof BoundStructLiteralExpression)) return true;
        }
        return false;
    }

    /** The variable an element or field access starts from: b in b[0].f. */
    private static VariableSymbol rootVariable(BoundExpression target) {
        while (true) {
            if (target instanceof BoundVariableExpression v) return v.getVariable();
            if (target instanceof BoundIndexExpression index) target = index.getTarget();
            else if (target instanceof BoundMemberAccessExpression member) target = member.getTarget();
            else return null;
        }
    }

    /** Reports an expression with no value where one is needed: a void call, or an if with no tail values. */
    private void requireValue(BoundExpression expression, SyntaxNode syntax) {
        if (expression.getClassType() == null) {
            _diagnostics.reportError(syntax.getSpan(), "This expression has no value");
        }
    }

    private static boolean isPopCall(BoundExpression expression) {
        return expression instanceof BoundCallExpression call && call.getFunction() == BuiltinFunctions.POP;
    }

    private BoundExpression bindExpressionWithTypeHint(ExpressionSyntax syntax, codeanalysis.syntax.SyntaxToken typeAnnotation) {
        BoundExpression result = bindExpressionInternal(syntax);
        // An initializer is an expression like any other: a call in it may store.
        if (result != null) recordStoredProvenance(result);
        if (result instanceof BoundArrayLiteralExpression arr && arr.getElements().isEmpty() && typeAnnotation != null) {
            String typeName = typeAnnotation.getData();
            // Both T[] and Array<T> name their element; taking Array<T> itself
            // as the element made ts[0] an array, whatever the array held.
            String elementName = TypeResolver.elementTypeNameOf(typeName);
            Class<?> elemType = _typeResolver.lookupType(elementName != null ? elementName : typeName);
            if (elemType != null) {
                result = new BoundArrayLiteralExpression(new ArrayList<>(), elemType);
            }
        }
        if (result != null) {
            try { result.setSourceOffset(syntax.getSpan().getStart()); } catch (Exception ignored) {}
        }
        return result;
    }

    private BoundExpression bindExpressionInternal(ExpressionSyntax syntax) {
        return switch (syntax.getType()) {
            case ParenthesizedExpression -> bindParenthesizedExpression((ParanthesizedExpressionSyntax) syntax);
            case LiteralExpression -> bindLiteralExpression((LiteralExpressionSyntax) syntax);
            case NameExpression -> bindNameExpression((NameExpressionSyntax) syntax);
            case AssignmentExpression -> bindAssignmentExpression((AssignmentExpressionSyntax) syntax);
            case UnaryExpression -> bindUnaryExpression((UnaryExpressionSyntax) syntax);
            case BinaryExpression -> bindBinaryExpression((BinaryExpressionSyntax) syntax);
            case CallExpression -> bindCallExpression((CallExpressionSyntax) syntax);
            case ArrayLiteralExpression -> bindArrayLiteralExpression((ArrayLiteralExpressionSyntax) syntax);
            case MapLiteralExpression -> bindMapLiteralExpression((codeanalysis.syntax.MapLiteralExpressionSyntax) syntax);
            case SetLiteralExpression -> bindSetLiteralExpression((codeanalysis.syntax.SetLiteralExpressionSyntax) syntax);
            case IndexExpression -> bindIndexExpression((IndexExpressionSyntax) syntax);
            case MemberAccessExpression -> bindMemberAccessExpression((MemberAccessExpressionSyntax) syntax);
            case StructLiteralExpression -> bindStructLiteralExpression((StructLiteralExpressionSyntax) syntax);
            case CompoundAssignmentExpression -> bindCompoundAssignment((CompoundAssignmentExpressionSyntax) syntax);
            case MemberCallExpression -> bindMemberCallExpression((MemberCallExpressionSyntax) syntax);
            case PostfixCallExpression -> bindPostfixCallExpression((codeanalysis.syntax.PostfixCallExpressionSyntax) syntax);
            case CastExpression -> bindCastExpression((CastExpressionSyntax) syntax);
            case LambdaExpression -> bindLambdaExpression((LambdaExpressionSyntax) syntax);
            case ScopeExpression -> bindScopeExpression((ScopeExpressionSyntax) syntax);
            case SpawnExpression -> bindSpawnExpression((SpawnExpressionSyntax) syntax);
            case MatchExpression -> bindMatchExpression((MatchExpressionSyntax) syntax);
            case IfExpression -> bindIfExpression((IfExpressionSyntax) syntax);
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
        // Note: string interpolation is now handled at lexer/parser level.
        // The lexer splits "text {expr} text" into InterpolatedStringStart/Mid/End tokens,
        // and the parser builds a binary + concatenation tree.
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
        VariableSymbol errorVar = declareErrorVariable(
                syntax.getErrorVariable(), syntax.getErrorTypeToken());
        BoundStatement catchBody = bindStatement(syntax.getCatchBody());
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        // Lower both bodies here. A try used as an expression may contain
        // control flow, and the emitter can only take a block's tail value from
        // a flattened body — an `if` inside one used to abort code generation.
        BoundBlockStatement loweredTry = codeanalysis.lowering.Lowerer.lower(tryBody);
        BoundBlockStatement loweredCatch = codeanalysis.lowering.Lowerer.lower(catchBody);

        // Determine result type from last expression in try body, fallback to catch body
        Class<?> resultType = lastExprType(loweredTry);
        if (resultType == null) resultType = lastExprType(loweredCatch);
        if (resultType == null) resultType = Object.class;
        return new BoundTryExpression(loweredTry, errorVar, loweredCatch, resultType);
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

    private boolean isActorHandle(VariableSymbol var) {
        if (var.getType() != SiyoStruct.class) return false;
        // Check if the struct type for this variable is an actor
        StructSymbol structType = _typeResolver.getVarStructType(var);
        return structType != null && structType.isActor();
    }

    private boolean isMutableContainerType(Class<?> type) {
        return type == SiyoArray.class || type == SiyoMap.class
                || type == SiyoSet.class || type == SiyoStruct.class;
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

    private BoundExpression bindIfExpression(IfExpressionSyntax syntax) {
        // Desugar `if cond { A } else { B }` into a match on the boolean condition.
        IfStatementSyntax ifs = syntax.getIfStatement();
        BoundExpression cond = bindExpression(ifs.getCondition(), Boolean.class);

        if (ifs.getElseClause() == null) {
            _diagnostics.reportUnexpectedExpression(ifs.getIfKeyword().getSpan(), SyntaxType.IfExpression);
            return new BoundLiteralExpression(0);
        }

        List<BoundStatement> thenPre = new ArrayList<>();
        BoundExpression thenExpr = bindBlockExpressionBody(ifs.getThenStatement(), thenPre);

        List<BoundStatement> elsePre = new ArrayList<>();
        BoundExpression elseExpr = bindBlockExpressionBody(ifs.getElseClause().getElseStatement(), elsePre);

        // One arm is void and the other not: run for effects, like a match whose
        // arms disagree. Each value is discarded where it is produced.
        boolean mixedArms = !diverges(thenPre, thenExpr) && !diverges(elsePre, elseExpr)
                && (thenExpr.getClassType() == null) != (elseExpr.getClassType() == null);
        if (mixedArms) {
            thenPre.add(new BoundExpressionStatement(thenExpr));
            elsePre.add(new BoundExpressionStatement(elseExpr));
            thenExpr = new BoundUnitExpression();
            elseExpr = new BoundUnitExpression();
        }

        // Neither arm comes back, so what follows is unreachable and needs no
        // real value: `imut v = if a { return 3 } else { return 4 }` is valid.
        if (diverges(thenPre, thenExpr) && diverges(elsePre, elseExpr)) {
            thenExpr = new BoundLiteralExpression(null);
            elseExpr = new BoundLiteralExpression(null);
        }

        // An arm that throws has no value, so the type comes from the other one.
        boolean thenDiverges = diverges(thenPre, thenExpr);
        Class<?> resultType = thenDiverges ? elseExpr.getClassType() : thenExpr.getClassType();
        if ((resultType == null || resultType == Object.class) && !diverges(elsePre, elseExpr)) {
            resultType = elseExpr.getClassType();
        }
        // Both arms void stays void: typing it Object told an enclosing arm that
        // a value was on the stack, and the method failed verification.

        List<BoundMatchExpression.BoundMatchArm> arms = new ArrayList<>();
        arms.add(new BoundMatchExpression.BoundMatchArm(new BoundLiteralExpression(true), thenExpr, false, thenPre));
        arms.add(new BoundMatchExpression.BoundMatchArm(null, elseExpr, true, elsePre));
        BoundMatchExpression result = new BoundMatchExpression(cond, arms, resultType);
        if (mixedArms) _mixedArmMatches.put(result, ifs.getIfKeyword().getSpan());
        return result;
    }

    private static final String MIXED_ARMS =
            "All match arms must return the same type; cannot mix void and value arms";

    /**
     * Matches bound as statements whose arms disagree on a type, with where
     * they first disagree. Such a match is fine run for its effects, and an
     * error if a function turns out to return it.
     */
    /**
     * The value-producing form of a match whose arms give values of different
     * types. It is used only where the match ends a function that returns
     * {@code object}: there an erased value is what was asked for, as in a
     * function turning a sum type into JSON values. Anywhere else the arms
     * still have to agree, or a string would be read back as an int.
     */
    private final Map<BoundMatchExpression, BoundMatchExpression> _erasableMatches = new java.util.IdentityHashMap<>();
    private final Map<BoundMatchExpression, String> _mixedArmMessages = new java.util.IdentityHashMap<>();

    /**
     * A sum type value written where a generic type is declared: its payload
     * must be what the type argument says, where that argument is trusted.
     * imut o: Opt<R> = Some("abc") is reported rather than failing when matched.
     */
    private void checkPayloadAgainst(BoundExpression value, String typeName, codeanalysis.text.TextSpan span) {
        if (!(value instanceof BoundUnionLiteralExpression union)) return;
        codeanalysis.UnionSymbol symbol = union.getUnionType();
        if (symbol.getTypeParameters().isEmpty() || !symbol.getName().equals(TypeResolver.genericBaseName(typeName))) return;
        List<String> parameters = symbol.getTypeParameters();
        List<String> arguments = TypeResolver.typeArgumentsOf(typeName);
        if (arguments.size() != parameters.size()) return;
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            bindings.put(parameters.get(i), _typeResolver.trustedTypeArgument(arguments.get(i)));
        }
        codeanalysis.UnionSymbol.Variant variant = symbol.getVariant(union.getVariantName());
        if (variant == null) return;
        for (int i = 0; i < union.getArguments().size(); i++) {
            Class<?> expected = _typeResolver.lookupType(
                    TypeResolver.substitute(variant.getPayloadTypeNames().get(i), parameters, bindings));
            Class<?> actual = union.getArguments().get(i).getClassType();
            if (expected != null && expected != Object.class && actual != null && actual != Object.class
                    && actual != expected) {
                _diagnostics.reportCannotConvert(span, actual, expected);
            }
        }
    }

    /** A number, a bool or a string: a value whose kind a slot cannot quietly change. */
    private static boolean isScalar(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Double.class
                || type == Boolean.class || type == String.class;
    }

    /** Whether a type name mentions a struct or sum type this file can see. */
    private boolean namesADeclaredType(String typeName) {
        for (String word : typeName.split("[^A-Za-z0-9_]+")) {
            if (!word.isEmpty() && (_structTypes.containsKey(word) || _typeResolver.getUnionTypes().containsKey(word))) {
                return true;
            }
        }
        return false;
    }

    private static String differentArmTypes(Class<?> first, Class<?> other) {
        return "Match arms give different types, " + siyoTypeName(first) + " and " + siyoTypeName(other)
                + "; they may differ only where the match ends a function returning object";
    }

    private final Map<BoundMatchExpression, codeanalysis.text.TextSpan> _mixedArmMatches =
            new java.util.IdentityHashMap<>();

    private BoundExpression bindMatchExpression(MatchExpressionSyntax syntax) {
        return bindMatchExpression(syntax, true);
    }

    /**
     * Binds a match. When its value is not used, its arms are not required to
     * agree on a type: {@code Blank => {}} beside {@code Bad(n, why) =>
     * { push(bad, why) }} used to be rejected for mixing int and void.
     *
     * @param syntax    The match.
     * @param valueUsed Whether the match's value is read.
     */
    private BoundExpression bindMatchExpression(MatchExpressionSyntax syntax, boolean valueUsed) {
        long generation = _typeResolver.generation();
        BoundExpression target = bindExpression(syntax.getTarget());
        codeanalysis.UnionSymbol targetUnion = _typeResolver.resolveUnionType(target);
        List<BoundMatchExpression.BoundMatchArm> arms = new ArrayList<>();
        java.util.Set<String> coveredVariants = new java.util.LinkedHashSet<>();
        boolean hasDefault = false;
        Class<?> resultType = null;
        boolean resultTypeInitialized = false;
        codeanalysis.text.TextSpan mismatch = null;
        boolean everyArmHasAValue = true;
        String mismatchMessage = MIXED_ARMS;
        for (MatchArmSyntax arm : syntax.getArms()) {
            if (arm.isDefault()) hasDefault = true;

            // A variant pattern binds its payload, so the arm body is bound in
            // a scope holding those bindings.
            BoundMatchExpression.BoundVariantPattern variant = null;
            boolean openedScope = false;
            if (arm.isVariantPattern()) {
                variant = bindVariantPattern(arm, targetUnion, _typeResolver.declaredTypeNameOf(target), generation,
                        _typeResolver.provenanceOf(target), _typeResolver.storageOf(target));
                if (variant != null) {
                    coveredVariants.add(variant.variantName());
                    _scope = new BoundScope(_scope);
                    _moduleHandler.setScope(_scope);
                    openedScope = true;
                    for (VariableSymbol binding : variant.bindings()) {
                        if (binding != null) _scope.tryDeclare(binding);
                    }
                }
            }

            BoundExpression pattern = arm.isDefault() || arm.isVariantPattern()
                    ? null
                    : bindExpression(arm.getPattern());
            if (pattern instanceof BoundUnionLiteralExpression unionPattern) {
                coveredVariants.add(unionPattern.getVariantName());
            }

            BoundExpression body;
            List<BoundStatement> preStatements = new ArrayList<>();
            if (arm.getBody() instanceof BlockExpressionSyntax blockExpr) {
                body = bindBlockExpressionBody(blockExpr.getBlock(), preStatements);
            } else {
                body = bindExpression(arm.getBody());
            }

            if (openedScope) {
                _scope = _scope.getParent();
                _moduleHandler.setScope(_scope);
            }

            Class<?> bodyType = body.getClassType();
            if (bodyType == null && !diverges(preStatements, body)) everyArmHasAValue = false;
            if (diverges(preStatements, body)) {
                // An arm that throws or returns never produces a value, so it
                // takes no part in deciding what the match evaluates to.
            } else if (!resultTypeInitialized) {
                // null is a meaningful type here: every arm may be void.
                resultType = bodyType;
                resultTypeInitialized = true;
            } else if (resultType != bodyType) {
                String why = resultType != null && bodyType != null
                        ? differentArmTypes(resultType, bodyType) : MIXED_ARMS;
                if (valueUsed) {
                    _diagnostics.reportError(arm.getBody().getSpan(), why);
                } else if (mismatch == null) {
                    mismatch = arm.getBody().getSpan();
                    mismatchMessage = why;
                }
            }
            arms.add(new BoundMatchExpression.BoundMatchArm(pattern, body, arm.isDefault(),
                    preStatements, variant));
        }

        // A match over a sum type is checked for exhaustiveness: the set of
        // variants is closed, so a missing one is a hole the compiler can see.
        if (targetUnion != null && !hasDefault) {
            List<String> missing = new ArrayList<>();
            for (String variantName : targetUnion.getVariantNames()) {
                if (!coveredVariants.contains(variantName)) missing.add(variantName);
            }
            if (!missing.isEmpty()) {
                _diagnostics.reportNonExhaustiveMatch(syntax.getMatchKeyword().getSpan(),
                        targetUnion.getName(), missing);
            }
        }

        if (mismatch != null) {
            // Run for its effects: each arm's value is discarded where it is
            // produced, and the match itself has none.
            List<BoundMatchExpression.BoundMatchArm> discarded = new ArrayList<>();
            for (var arm : arms) {
                List<BoundStatement> statements = new ArrayList<>(arm.preStatements());
                statements.add(new BoundExpressionStatement(arm.body()));
                discarded.add(new BoundMatchExpression.BoundMatchArm(arm.pattern(), new BoundUnitExpression(),
                        arm.isDefault(), statements, arm.variant()));
            }
            BoundMatchExpression statementMatch = new BoundMatchExpression(target, discarded, null);
            _mixedArmMatches.put(statementMatch, mismatch);
            _mixedArmMessages.put(statementMatch, mismatchMessage);
            if (everyArmHasAValue) {
                _erasableMatches.put(statementMatch, new BoundMatchExpression(target, arms, Object.class));
            }
            return statementMatch;
        }
        if (!resultTypeInitialized) resultType = Object.class;
        return new BoundMatchExpression(target, arms, resultType);
    }

    /**
     * Binds a variant pattern, resolving which sum type it selects from and
     * giving each bound payload slot a variable of the declared payload type.
     *
     * @param arm         The arm carrying the pattern.
     * @param targetUnion The sum type of the matched value, when it is known.
     * @return The bound pattern, or null when the pattern was rejected.
     */
    private BoundMatchExpression.BoundVariantPattern bindVariantPattern(MatchArmSyntax arm,
                                                                       codeanalysis.UnionSymbol targetUnion,
                                                                       String targetTypeName,
                                                                       long generation,
                                                                       java.util.Set<Object> provenance,
                                                                       TypeResolver.Storage storage) {
        String variantName = arm.getVariantName().getData();
        codeanalysis.UnionSymbol union;
        if (arm.getVariantTypeName() != null) {
            String typeName = arm.getVariantTypeName().getData();
            union = _typeResolver.getUnionTypes().get(typeName);
            if (union == null) {
                reportMissingType(arm.getVariantTypeName().getSpan(), typeName);
                return null;
            }
        } else if (targetUnion != null && targetUnion.hasVariant(variantName)) {
            union = targetUnion;
        } else {
            union = resolveVariantOwner(variantName, arm.getVariantName().getSpan());
            if (union == null) {
                _diagnostics.reportUndefinedName(arm.getVariantName().getSpan(), variantName);
                return null;
            }
        }

        codeanalysis.UnionSymbol.Variant variant = union.getVariant(variantName);
        if (variant == null) {
            _diagnostics.reportUndefinedVariant(arm.getVariantName().getSpan(), union.getName(),
                    variantName, union.getVariantNames());
            return null;
        }
        if (targetUnion != null && !targetUnion.getName().equals(union.getName())) {
            _diagnostics.reportError(arm.getVariantName().getSpan(),
                    String.format("Variant '%s.%s' cannot match a value of type '%s'",
                            union.getName(), variantName, targetUnion.getName()));
            return null;
        }

        List<codeanalysis.syntax.SyntaxToken> bindingTokens = arm.getBindings();
        if (bindingTokens.size() != variant.size()) {
            _diagnostics.reportWrongVariantPayloadCount(arm.getVariantName().getSpan(), union.getName(),
                    variantName, variant.size(), bindingTokens.size());
            return null;
        }

        // A generic sum type's payload is read at the type arguments the matched
        // value carries: Some(r) on an Option<Row> binds a Row.
        List<String> typeParameters = union.getTypeParameters();
        Map<String, String> typeArguments = new HashMap<>();
        if (targetTypeName != null && union.getName().equals(TypeResolver.genericBaseName(targetTypeName))) {
            List<String> arguments = TypeResolver.typeArgumentsOf(targetTypeName);
            for (int i = 0; i < arguments.size() && i < typeParameters.size(); i++) {
                typeArguments.put(typeParameters.get(i), _typeResolver.trustedTypeArgument(arguments.get(i)));
            }
        }

        List<VariableSymbol> bindings = new ArrayList<>();
        for (int i = 0; i < bindingTokens.size(); i++) {
            codeanalysis.syntax.SyntaxToken token = bindingTokens.get(i);
            if (token == null) {
                bindings.add(null); // the pattern discarded this slot
                continue;
            }
            String payloadTypeName = TypeResolver.substitute(variant.getPayloadTypeNames().get(i),
                    typeParameters, typeArguments);
            Class<?> payloadType = variant.getPayloadTypes().get(i);
            // The payload keeps 0.9.0's erased class; its name says which struct
            // it is, so Some(r) => r.name on an Option<Row> reads a Row.
            VariableSymbol binding = new VariableSymbol(token.getData(), true, payloadType);
            // The payload's written type is what it holds: T(xs) with T(Array<S>)
            // binds an Array<S>, whose elements are known to be S.
            // Only where it says which struct or type argument the payload holds:
            // a plain float[] payload stays as 0.9.0 read it.
            if (!payloadTypeName.equals("object") && (!typeArguments.isEmpty() || namesADeclaredType(payloadTypeName))) {
                binding.setDeclaredTypeName(payloadTypeName);
                binding.addProvenance(provenance);
                _typeResolver.stamp(binding, generation, !provenance.isEmpty());
                markGenericTypeInferred(binding);
            }
            binding.addProvenance(provenance);
            for (VariableSymbol shared : storage.same) binding.elementOf(shared);
            for (VariableSymbol container : storage.element) binding.elementOf(container);
            for (VariableSymbol held : storage.held) held.heldIn(binding);
            bindings.add(binding);
            StructSymbol payloadStruct = _structTypes.get(payloadTypeName);
            if (payloadStruct != null) _typeResolver.trackStructType(binding, payloadStruct);
            codeanalysis.UnionSymbol payloadUnion = _typeResolver.getUnionTypes().get(payloadTypeName);
            if (payloadUnion != null) _typeResolver.trackUnionType(binding, payloadUnion);
        }

        return new BoundMatchExpression.BoundVariantPattern(union.getName(), variantName, bindings);
    }

    /**
     * Whether a block-bodied arm leaves by throwing or returning rather than by
     * producing a value. Such an arm's body is only the placeholder that
     * {@link #bindBlockExpressionBody} puts after the last statement.
     *
     * <p>Without this {@code Bad(why) => { throw why }} was typed as the
     * placeholder's int and rejected beside a struct arm, and an if expression
     * whose first arm threw took the placeholder's type and failed verification.
     */
    private static boolean diverges(List<BoundStatement> preStatements, BoundExpression body) {
        if (preStatements.isEmpty() || !(body instanceof BoundLiteralExpression)) return false;
        BoundStatement last = preStatements.get(preStatements.size() - 1);
        return last instanceof BoundThrowStatement || last instanceof BoundReturnStatement
                || last instanceof BoundBreakStatement || last instanceof BoundContinueStatement;
    }

    private BoundExpression bindBlockExpressionBody(StatementSyntax block, List<BoundStatement> preStatements) {
        // For block bodies in match arms, bind ALL statements in a new scope.
        // Preceding statements go into preStatements; the last expression is the result.
        if (block instanceof BlockStatementSyntax bs && !bs.getStatements().isEmpty()) {
            _scope = new BoundScope(_scope);
            _moduleHandler.setScope(_scope);
            var stmts = bs.getStatements();
            for (int i = 0; i < stmts.size() - 1; i++) {
                preStatements.add(bindStatement(stmts.get(i)));
            }
            var last = stmts.get(stmts.size() - 1);
            BoundExpression result;
            if (last instanceof ExpressionStatementSyntax exprStmt) {
                result = bindExpression(exprStmt.getExpression());
            } else if (last instanceof IfStatementSyntax ifStmt && ifStmt.getElseClause() != null) {
                // Allow trailing `if/else` to act as the value of the block.
                result = bindIfExpression(new IfExpressionSyntax(ifStmt));
            } else {
                // Last statement is not an expression — bind it as a statement; the block has no value.
                preStatements.add(bindStatement(last));
                result = new BoundUnitExpression();
            }
            _scope = _scope.getParent();
            _moduleHandler.setScope(_scope);
            return result;
        }
        // `else if` is the else block of an if expression, with the same value.
        if (block instanceof IfStatementSyntax elseIf) {
            if (elseIf.getElseClause() != null) return bindIfExpression(new IfExpressionSyntax(elseIf));
            preStatements.add(bindStatement(elseIf));
            return new BoundUnitExpression();
        }
        return new BoundUnitExpression();
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
    private static boolean isEqualityOperator(SyntaxType type) {
        return type == SyntaxType.EqualsEqualsToken || type == SyntaxType.BangEqualsToken;
    }

    private static boolean isNullLiteral(BoundExpression expression) {
        return expression instanceof BoundLiteralExpression literal && literal.getValue() == null;
    }

    /**
     * A slot of a {@code Map<string, int>} compared to null. Its declared type
     * would have the compiled code unbox it first, which fails for the very
     * case being asked about: a missing key. Only the slot's box is compared.
     */
    private static BoundExpression withoutUnboxing(BoundExpression expression) {
        if (expression instanceof BoundIndexExpression slot
                && (slot.getClassType() == Integer.class || slot.getClassType() == Long.class
                    || slot.getClassType() == Double.class || slot.getClassType() == Boolean.class)) {
            return new BoundIndexExpression(slot.getTarget(), slot.getIndex(), Object.class);
        }
        return expression;
    }

    private BoundExpression bindBinaryExpression(BinaryExpressionSyntax syntax) {
        BoundExpression boundLeft = bindExpression(syntax.getLeft());
        BoundExpression boundRight = bindExpression(syntax.getRight());
        if (isEqualityOperator(syntax.getOperator().getType())) {
            if (isNullLiteral(boundRight)) boundLeft = withoutUnboxing(boundLeft);
            if (isNullLiteral(boundLeft)) boundRight = withoutUnboxing(boundRight);
        }
        if (syntax.getOperator().getValue() == codeanalysis.syntax.Parser.INTERPOLATION
                && boundRight.getClassType() == null) {
            requireValue(boundRight, syntax.getRight());
            return boundLeft;
        }
        // "$x" shows x whatever it is. + itself only joins text to a scalar, so
        // interpolating an array or a struct used to report an operator error.
        if (syntax.getOperator().getValue() == codeanalysis.syntax.Parser.INTERPOLATION
                && boundRight.getClassType() != null
                && boundRight.getClassType() != Object.class
                && BoundBinaryOperator.bind(SyntaxType.PlusToken, String.class, boundRight.getClassType()) == null) {
            boundRight = new BoundCallExpression(BuiltinFunctions.TO_STRING, List.of(boundRight));
        }
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

        // Mixed numeric operands meet at the wider of the two types. Enumerating
        // an operator per pair covered int/long partly and int/float not at all,
        // so `1 + 2.5` was rejected and every mix needed a hand conversion.
        if (boundOperator == null) {
            Class<?> common = widerNumericType(leftType, rightType);
            if (common != null) {
                boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), common, common);
                if (boundOperator != null) {
                    boundLeft = widenTo(boundLeft, common);
                    boundRight = widenTo(boundRight, common);
                }
            }
        }

        if (boundOperator == null) {
            _diagnostics.reportUndefinedBinaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), leftType, rightType);
            return boundLeft;
        }
        return new BoundBinaryExpression(boundLeft, boundOperator, boundRight);
    }

    /**
     * The type two numeric operands of different types meet at, or null when
     * they are not both numeric or already agree.
     *
     * <p>The order is int, then long, then float: every value of the narrower
     * type is a value of the wider one, so widening never changes what the
     * program means.</p>
     *
     * @param left  The left operand's type.
     * @param right The right operand's type.
     * @return The common type, or null.
     */
    static Class<?> widerNumericType(Class<?> left, Class<?> right) {
        int leftRank = numericRank(left);
        int rightRank = numericRank(right);
        if (leftRank == 0 || rightRank == 0 || leftRank == rightRank) return null;
        return leftRank > rightRank ? left : right;
    }

    /** Where a type sits in the widening order, or 0 when it is not numeric. */
    private static int numericRank(Class<?> type) {
        if (type == Integer.class) return 1;
        if (type == Long.class) return 2;
        if (type == Double.class) return 3;
        return 0;
    }

    /**
     * Widens an expression to a numeric type, or returns it unchanged when it
     * already has that type or is not a narrower numeric one.
     *
     * @param expression The expression to widen.
     * @param target     The type to widen to.
     * @return The expression, wrapped if a widening is needed.
     */
    static BoundExpression widenTo(BoundExpression expression, Class<?> target) {
        Class<?> from = expression.getClassType();
        if (from == target) return expression;
        if (numericRank(from) == 0 || numericRank(target) == 0) return expression;
        if (numericRank(from) >= numericRank(target)) return expression;
        return new BoundConversionExpression(expression, target);
    }

    /**
     * Widens the elements of an array or map literal to the type it is declared
     * as, so {@code float[]} given {@code [1, 2]} holds floats on both backends.
     *
     * @param expression The expression.
     * @param typeName   The type it is declared as, or null.
     * @return The literal with its elements widened, or the expression unchanged.
     */
    private BoundExpression widenLiteral(BoundExpression expression, String typeName) {
        if (typeName == null) return expression;
        if (expression instanceof BoundArrayLiteralExpression array) {
            String elementName = TypeResolver.elementTypeNameOf(typeName);
            Class<?> elementType = _typeResolver.lookupType(elementName);
            if (elementType == null) return expression;
            List<BoundExpression> elements = new ArrayList<>();
            for (BoundExpression element : array.getElements()) {
                elements.add(widenLiteral(widenTo(element, elementType), elementName));
            }
            boolean numeric = numericRank(array.getElementType()) > 0 && numericRank(elementType) > 0;
            return new BoundArrayLiteralExpression(elements, numeric ? elementType : array.getElementType());
        }
        if (expression instanceof BoundMapLiteralExpression map) {
            List<String> arguments = TypeResolver.typeArgumentsOfMap(typeName);
            if (arguments == null) return expression;
            Class<?> valueType = _typeResolver.lookupType(arguments.get(1));
            if (valueType == null) return expression;
            List<BoundExpression> values = new ArrayList<>();
            for (BoundExpression value : map.getValues()) {
                values.add(widenLiteral(widenTo(value, valueType), arguments.get(1)));
            }
            return new BoundMapLiteralExpression(map.getKeys(), values);
        }
        // A generic struct's field holds what its declared type argument says:
        // value of a Box<float> given 3 holds 3.0, as a float field would.
        if (expression instanceof BoundStructLiteralExpression struct
                && !struct.getStructType().getTypeParameters().isEmpty()
                && struct.getStructType().getName().equals(TypeResolver.genericBaseName(typeName))) {
            StructSymbol symbol = struct.getStructType();
            List<String> parameters = symbol.getTypeParameters();
            List<String> arguments = TypeResolver.typeArgumentsOf(typeName);
            if (arguments.size() != parameters.size()) return expression;
            // A parameter written Box<T> says nothing about this literal's T; only
            // a type written out (Box<float>) is one to widen to and name it by.
            if (TypeResolver.mentionsAny(typeName, _typeResolver.getTypeParameters())) return expression;
            Map<String, String> bindings = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) bindings.put(parameters.get(i), arguments.get(i));
            java.util.LinkedHashMap<String, BoundExpression> values = new java.util.LinkedHashMap<>();
            for (var entry : struct.getFieldValues().entrySet()) {
                String fieldTypeName = TypeResolver.substitute(symbol.getFieldTypeName(entry.getKey()), parameters, bindings);
                Class<?> fieldType = _typeResolver.lookupType(fieldTypeName);
                BoundExpression value = entry.getValue();
                if (fieldType != null) value = widenLiteral(widenTo(value, fieldType), fieldTypeName);
                values.put(entry.getKey(), value);
            }
            return new BoundStructLiteralExpression(symbol, values, typeName);
        }
        return expression;
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
            if (_scope.tryLookupFunction(name)) {
                FunctionSymbol func = _scope.lookupFunction(name);
                if (func != null) {
                    return createFunctionReference(func);
                }
            }
            // A payload-less variant is a value on its own: None, not None().
            codeanalysis.UnionSymbol union = resolveVariantOwner(name, syntax.getIdentifierToken().getSpan());
            if (union != null) {
                return bindUnionConstruction(union, name, List.of(),
                        syntax.getIdentifierToken().getSpan(), List.of());
            }
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return new BoundLiteralExpression(0);
        }
        var variable  = _scope.lookupVariable(name);
        return new BoundVariableExpression(variable);
    }

    /**
     * Resolves which sum type a bare variant name belongs to.
     *
     * @param variantName The variant name as written.
     * @param span        The span to report an ambiguity against.
     * @return The declaring type, or null when no type declares the name.
     */
    private codeanalysis.UnionSymbol resolveVariantOwner(String variantName, codeanalysis.text.TextSpan span) {
        List<codeanalysis.UnionSymbol> owners = _moduleHandler.findUnionsByVariant(variantName);
        if (owners.isEmpty()) return null;
        if (owners.size() > 1) {
            List<String> names = new ArrayList<>();
            for (codeanalysis.UnionSymbol owner : owners) names.add(owner.getName());
            _diagnostics.reportAmbiguousVariant(span, variantName, names);
            return null;
        }
        return owners.get(0);
    }

    /**
     * Binds the construction of a sum type value, checking the variant exists
     * and that its payload is the shape the declaration gave it.
     *
     * @param union        The sum type being constructed.
     * @param variantName  The variant written at the call site.
     * @param payload      The bound payload expressions.
     * @param span         The span to report a shape error against.
     * @param payloadSpans The span of each payload expression, for type errors.
     * @return The bound construction, or a placeholder when it was rejected.
     */
    private BoundExpression bindUnionConstruction(codeanalysis.UnionSymbol union, String variantName,
                                                  List<BoundExpression> payload,
                                                  codeanalysis.text.TextSpan span,
                                                  List<codeanalysis.text.TextSpan> payloadSpans) {
        codeanalysis.UnionSymbol.Variant variant = union.getVariant(variantName);
        if (variant == null) {
            _diagnostics.reportUndefinedVariant(span, union.getName(), variantName, union.getVariantNames());
            return new BoundLiteralExpression(0);
        }
        if (payload.size() != variant.size()) {
            _diagnostics.reportWrongVariantPayloadCount(span, union.getName(), variantName,
                    variant.size(), payload.size());
            return new BoundLiteralExpression(0);
        }
        // A payload widens as a parameter does: I(2) holds a long when I
        // carries one, and F([1, 2]) a float array.
        payload = new ArrayList<>(payload);
        for (int i = 0; i < payload.size(); i++) {
            Class<?> declared = variant.getPayloadTypes().get(i);
            payload.set(i, widenLiteral(widenTo(payload.get(i), declared), variant.getPayloadTypeNames().get(i)));
        }
        for (int i = 0; i < payload.size(); i++) {
            Class<?> declared = variant.getPayloadTypes().get(i);
            Class<?> actual = payload.get(i).getClassType();
            if (declared != Object.class && actual != Object.class && declared != actual) {
                codeanalysis.text.TextSpan valueSpan = i < payloadSpans.size() ? payloadSpans.get(i) : span;
                _diagnostics.reportCannotConvert(valueSpan, actual, declared);
            }
        }
        return new BoundUnionLiteralExpression(union, variantName, payload);
    }

    /**
     * Collects the span of each argument, so a payload type error points at the
     * value that was wrong rather than the whole construction.
     *
     * @param arguments The argument syntax list.
     * @return The spans, in order.
     */
    private List<codeanalysis.text.TextSpan> argumentSpans(SeparatedSyntaxList<ExpressionSyntax> arguments) {
        List<codeanalysis.text.TextSpan> spans = new ArrayList<>();
        for (int i = 0; i < arguments.getCount(); i++) {
            spans.add(arguments.get(i).getSpan());
        }
        return spans;
    }

    /**
     * Checks a value against a declared function type.
     *
     * <p>A signature used to be parsed and discarded, so a callback of the
     * wrong shape was accepted and failed at run time. A lambda written at the
     * site is checked against the declaration, and so is a closure read out of
     * a variable that carries a signature of its own.</p>
     *
     * @param value             The value being assigned or passed.
     * @param declaredTypeName  The type name written at the declaration.
     * @param span              The span to report against.
     */
    private void checkAgainstFunctionType(BoundExpression value, String declaredTypeName,
                                          codeanalysis.text.TextSpan span) {
        codeanalysis.FunctionTypeSignature declared =
                codeanalysis.FunctionTypeSignature.parse(declaredTypeName);
        if (declared == null) return; // a bare `fn` accepts any closure

        List<Class<?>> actualParameterTypes;
        Class<?> actualReturnType;
        if (value instanceof BoundLambdaExpression lambda) {
            actualParameterTypes = new ArrayList<>();
            for (ParameterSymbol parameter : lambda.getParameters()) {
                actualParameterTypes.add(parameter.getType());
            }
            actualReturnType = lambda.getReturnType();
        } else if (value instanceof BoundVariableExpression varExpr) {
            codeanalysis.FunctionTypeSignature actual =
                    codeanalysis.FunctionTypeSignature.parse(varExpr.getVariable().getDeclaredTypeName());
            if (actual == null) return; // nothing known about the closure's shape
            actualParameterTypes = new ArrayList<>();
            for (String typeName : actual.getParameterTypeNames()) {
                actualParameterTypes.add(_typeResolver.lookupType(typeName));
            }
            actualReturnType = actual.getReturnTypeName() == null
                    ? null
                    : _typeResolver.lookupType(actual.getReturnTypeName());
        } else {
            return; // an erased closure: its shape is only known at run time
        }

        if (actualParameterTypes.size() != declared.getParameterCount()) {
            _diagnostics.reportFunctionTypeMismatch(span, declared.toString(),
                    String.format("one taking %d argument%s", actualParameterTypes.size(),
                            actualParameterTypes.size() == 1 ? "" : "s"));
            return;
        }

        for (int i = 0; i < actualParameterTypes.size(); i++) {
            Class<?> declaredParameter = _typeResolver.lookupType(declared.getParameterTypeNames().get(i));
            Class<?> actualParameter = actualParameterTypes.get(i);
            if (declaredParameter == null || actualParameter == null) continue;
            if (declaredParameter == Object.class || actualParameter == Object.class) continue;
            if (declaredParameter != actualParameter) {
                _diagnostics.reportCannotConvert(span, actualParameter, declaredParameter);
                return;
            }
        }

        if (declared.getReturnTypeName() != null && actualReturnType != null) {
            Class<?> declaredReturn = _typeResolver.lookupType(declared.getReturnTypeName());
            if (declaredReturn != null && declaredReturn != Object.class && actualReturnType != Object.class
                    && declaredReturn != actualReturnType) {
                _diagnostics.reportCannotConvert(span, actualReturnType, declaredReturn);
            }
        }
    }

    /**
     * Builds the type name for a lambda's own shape, so a name bound to it
     * carries a signature even when the declaration wrote no annotation.
     *
     * @param lambda The lambda.
     * @return The encoded function type name.
     */
    private String signatureNameOf(BoundLambdaExpression lambda) {
        List<String> parameterTypeNames = new ArrayList<>();
        for (ParameterSymbol parameter : lambda.getParameters()) {
            String declared = parameter.getDeclaredTypeName();
            parameterTypeNames.add(declared != null ? declared : siyoTypeName(parameter.getType()));
        }
        String returnTypeName = lambda.getReturnType() == null ? null : siyoTypeName(lambda.getReturnType());
        // A generic struct's arguments are kept in the signature, so a call
        // through the name knows them: fn(object) -> Box<Array<float>>.
        if (_typeResolver.mentionsGenericStruct(lambda.getReturnTypeName())) returnTypeName = lambda.getReturnTypeName();
        return codeanalysis.FunctionTypeSignature.encode(parameterTypeNames, returnTypeName);
    }

    /**
     * The Siyo name of a type, for building a signature out of bound types.
     *
     * @param type The Java class backing a Siyo type.
     * @return The Siyo type name.
     */
    private static String siyoTypeName(Class<?> type) {
        if (type == Integer.class) return "int";
        if (type == Long.class) return "long";
        if (type == Boolean.class) return "bool";
        if (type == Double.class) return "float";
        if (type == String.class) return "string";
        if (type == SiyoMap.class) return "map";
        if (type == SiyoSet.class) return "set";
        if (type == SiyoChannel.class) return "channel";
        if (type == SiyoClosure.class) return "fn";
        return "object";
    }

    /**
     * The signature a closure-valued expression is known to have, taken from
     * the declaration it came through.
     *
     * @param callee The expression holding the closure.
     * @return The signature, or null when nothing is known about its shape.
     */
    private codeanalysis.FunctionTypeSignature signatureOf(BoundExpression callee) {
        if (callee instanceof BoundVariableExpression varExpr) {
            return codeanalysis.FunctionTypeSignature.parse(varExpr.getVariable().getDeclaredTypeName());
        }
        return null;
    }

    /**
     * Binds a call through a closure-valued name, checking the call against the
     * signature the closure was declared with and giving the call the declared
     * return type instead of an erased one.
     *
     * @param callee    The expression holding the closure.
     * @param arguments The bound arguments.
     * @param span      The span to report against.
     * @return The bound call.
     */
    private BoundExpression bindClosureCall(BoundExpression callee, List<BoundExpression> arguments,
                                            codeanalysis.text.TextSpan span) {
        codeanalysis.FunctionTypeSignature signature = signatureOf(callee);
        if (signature == null) {
            return new BoundClosureCallExpression(callee, arguments);
        }

        if (arguments.size() != signature.getParameterCount()) {
            _diagnostics.reportWrongArgumentCount(span, signature.toString(),
                    signature.getParameterCount(), arguments.size());
            return new BoundClosureCallExpression(callee, arguments);
        }

        for (int i = 0; i < arguments.size(); i++) {
            Class<?> declared = _typeResolver.lookupType(signature.getParameterTypeNames().get(i));
            Class<?> actual = arguments.get(i).getClassType();
            if (declared == null || declared == Object.class || actual == Object.class) continue;
            if (declared != actual) {
                _diagnostics.reportCannotConvert(span, actual, declared);
                break;
            }
        }

        Class<?> resultType = signature.getReturnTypeName() == null
                ? Object.class
                : _typeResolver.lookupType(signature.getReturnTypeName());
        return new BoundClosureCallExpression(callee, arguments,
                resultType == null ? Object.class : resultType);
    }

    private BoundExpression createFunctionReference(FunctionSymbol func) {
        List<ParameterSymbol> params = func.getParameters();
        List<BoundExpression> argExprs = new ArrayList<>();
        for (ParameterSymbol p : params) {
            argExprs.add(new BoundVariableExpression(p));
        }
        BoundCallExpression call = new BoundCallExpression(func, argExprs);
        BoundStatement bodyStmt;
        if (func.getReturnType() == void.class || func.getReturnType() == null) {
            bodyStmt = new BoundExpressionStatement(call);
        } else {
            bodyStmt = new BoundReturnStatement(call);
        }
        ArrayList<BoundStatement> stmts = new ArrayList<>();
        stmts.add(bodyStmt);
        BoundBlockStatement body = new BoundBlockStatement(stmts);
        return new BoundLambdaExpression(params, body, func.getReturnType(), func.getCapturedLocals());
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
    /** Numbers nested functions across modules, so no two symbols compare equal. */
    private static final java.util.concurrent.atomic.AtomicInteger NESTED_FUNCTIONS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Nested functions a block has declared ahead of binding them, so they can call each other. */
    private final Map<FunctionDeclarationSyntax, FunctionSymbol> _hoistedNested = new java.util.IdentityHashMap<>();

    /**
     * Declares a function nested in another. It is its own function, whatever else
     * shares its name: symbols compare by name, so two of them used to share one
     * body, and a nested helper replaced a top-level one. It is called by its
     * written name in the scope it is declared in, and known by a unique one
     * everywhere else.
     */
    private FunctionSymbol declareNestedFunction(FunctionDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        FunctionSymbol function = _moduleHandler.functionSymbolFor(syntax,
                name + FunctionSymbol.NESTED_MARK + NESTED_FUNCTIONS.getAndIncrement());
        function.setDeclarationSpan(syntax.getIdentifier().getSpan());
        if (!_scope.tryDeclareFunction(name, function)) {
            _diagnostics.reportFunctionAlreadyDeclared(syntax.getIdentifier().getSpan(), name);
        }
        return function;
    }

    private BoundStatement bindFunctionDeclaration(FunctionDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        // A type parameter stands for a type throughout the declaration, so it
        // resolves in the signature and in the body alike.
        _typeResolver.getTypeParameters().addAll(syntax.getTypeParameters());

        // Parse parameters
        List<ParameterSymbol> parameters = new ArrayList<>();
        HashSet<String> seenParameterNames = new HashSet<>();

        for (ParameterSyntax parameterSyntax : syntax.getParameters()) {
            String parameterName = parameterSyntax.getIdentifier().getData();
            String typeName = parameterSyntax.getTypeToken().getData();
            Class<?> parameterType = _typeResolver.lookupType(typeName);

            if (parameterType == null) {
                reportMissingType(parameterSyntax.getTypeToken().getSpan(), typeName);
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
                reportMissingType(syntax.getTypeClause().getIdentifier().getSpan(), returnTypeName);
            }
        }

        FunctionSymbol function;
        if (_currentFunction != null) {
            function = _hoistedNested.remove(syntax);
            if (function == null) function = declareNestedFunction(syntax);
        } else if (_scope.tryLookupFunction(name)) {
            function = _scope.lookupFunction(name, parameters.size());
            if (function == null) function = _scope.lookupFunction(name);
        } else {
            function = new FunctionSymbol(name, parameters, returnType);
            function.setTypeParameters(syntax.getTypeParameters());
            if (syntax.getTypeClause() != null) {
                function.setDeclaredReturnTypeName(syntax.getTypeClause().getIdentifier().getData());
            }
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
                    String elementName = typeName.substring(0, typeName.length() - 2);
                    StructSymbol elementStruct = _structTypes.get(elementName);
                    if (elementStruct != null) _typeResolver.trackArrayType(parameter, elemType, elementStruct);
                    else _typeResolver.trackArrayType(parameter, elemType);
                }
                // Track struct types from parameter type names
                String baseTypeName = TypeResolver.erasedTypeName(
                        typeName.endsWith("[]") ? typeName.substring(0, typeName.length() - 2) : typeName);
                StructSymbol structSym = _structTypes.get(baseTypeName);
                if (structSym != null && parameter.getType() == SiyoStruct.class) {
                    _typeResolver.trackStructType(parameter, structSym);
                }
                // Track the sum type a parameter holds, so a match over it is
                // checked for exhaustiveness the way a local is.
                codeanalysis.UnionSymbol unionSym = _typeResolver.getUnionTypes().get(baseTypeName);
                if (unionSym != null && parameter.getType() == codeanalysis.SiyoUnion.class) {
                    _typeResolver.trackUnionType(parameter, unionSym);
                }
                // A parameter declared as an interface holds some struct that
                // implements it; the call site is what discovers which.
                codeanalysis.InterfaceSymbol interfaceSym = _typeResolver.getInterfaceTypes().get(baseTypeName);
                if (interfaceSym != null) {
                    _typeResolver.trackInterfaceType(parameter, interfaceSym);
                }
                parameter.setDeclaredTypeName(typeName);
                codeanalysis.JavaClassInfo javaType = _typeResolver.getJavaClasses().get(typeName);
                if (javaType != null) _typeResolver.trackJavaClassType(parameter, javaType);
            }
            paramIdx++;
        }

        // Bind the body statements
        ArrayList<BoundStatement> statements = new ArrayList<>();
        for (StatementSyntax statementSyntax : syntax.getBody().getStatements()) {
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }
        BoundBlockStatement boundBody = applyImplicitReturn(
                new BoundBlockStatement(statements), function.getReturnType(), function.getDeclaredReturnTypeName());
        if (previousFunction != null) collectFunctionCaptures(function, boundBody);

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
            } else {
                // A narrower number is a value of the declared wider type, so
                // `fn f() -> float { return 3 }` widens instead of mismatching.
                expression = widenLiteral(widenTo(expression, _currentFunction.getReturnType()),
                        _currentFunction.getDeclaredReturnTypeName());
                if (expression.getClassType() != _currentFunction.getReturnType()
                        && _currentFunction.getReturnType() != Object.class
                        && expression.getClassType() != Object.class) {
                    _diagnostics.reportReturnTypeMismatch(syntax.getExpression().getSpan(), expression.getClassType(), _currentFunction.getReturnType());
                }
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

        // A variant of a sum type is constructed by writing it: Ok(5).
        if (!_scope.tryLookupFunction(name) && !_scope.tryLookup(name)) {
            codeanalysis.UnionSymbol union = resolveVariantOwner(name, syntax.getIdentifier().getSpan());
            if (union != null) {
                List<BoundExpression> payload = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    payload.add(bindExpression(argSyntax));
                }
                return bindUnionConstruction(union, name, payload, syntax.getSpan(),
                        argumentSpans(syntax.getArguments()));
            }
        }

        // Calling a value: f(args). A closure held in a variable is callable
        // whether its static type says `fn` or was erased to `object` — the
        // erased case is what a closure read out of a map or a struct field
        // looks like, and it used to be reported as a missing function.
        if (!_scope.tryLookupFunction(name) && _scope.tryLookup(name)) {
            VariableSymbol var = _scope.lookupVariable(name);
            if (var.getType() == SiyoClosure.class || var.getType() == Object.class) {
                List<BoundExpression> args = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    args.add(bindExpression(argSyntax));
                }
                return bindClosureCall(new BoundVariableExpression(var), args, syntax.getSpan());
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

        // Resolve overload: try type-aware first, then arg count, then first match
        java.util.List<Class<?>> argTypes = new java.util.ArrayList<>();
        for (BoundExpression arg : boundArguments) argTypes.add(arg.getClassType());
        FunctionSymbol function = _scope.lookupFunction(name, argTypes);
        if (function == null) function = _scope.lookupFunction(name, boundArguments.size());
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
            if (argument.getClassType() == null) {
                requireValue(argument, syntax.getArguments().get(i));
                continue;
            }
            argument = widenLiteral(argument, parameter.getDeclaredTypeName());
            boundArguments.set(i, argument);

            // A narrower number is a value of the wider parameter's type, so it
            // is widened rather than rejected: half(5) reaches half(x: float).
            BoundExpression widened = widenTo(argument, parameter.getType());
            if (widened != argument) {
                boundArguments.set(i, widened);
                continue;
            }

            // Object.class accepts any type (used by built-in functions like toString)
            if (parameter.getType() != Object.class && argument.getClassType() != Object.class && argument.getClassType() != parameter.getType()) {
                _diagnostics.reportWrongArgumentType(syntax.getArguments().get(i).getSpan(), parameter.getName(), parameter.getType(), argument.getClassType());
            } else if (parameter.getType() == SiyoClosure.class) {
                checkAgainstFunctionType(argument, parameter.getDeclaredTypeName(),
                        syntax.getArguments().get(i).getSpan());
            }
        }

        // push widens to what the array holds, as an assignment to a slot does.
        if (function == BuiltinFunctions.PUSH && boundArguments.size() == 2) {
            boundArguments.set(1, widenTo(boundArguments.get(1), slotType(boundArguments.get(0))));
            // An array declared to hold one kind of value, by an annotation or a
            // generic result, cannot be given another: compiled, the element
            // failed when read back. An array only inferred from a literal is
            // left as 0.9.0 left it.
            Class<?> holds = slotType(boundArguments.get(0));
            Class<?> given = boundArguments.get(1).getClassType();
            boolean declared = _typeResolver.declaredTypeNameOf(boundArguments.get(0)) != null;
            if (declared && isScalar(holds) && isScalar(given) && holds != given) {
                _diagnostics.reportError(syntax.getArguments().get(1).getSpan(),
                        "push: the array holds " + siyoTypeName(holds) + ", not " + siyoTypeName(given));
            }
        }

        // pop is a builtin, so no signature says what it returns: the array does.
        if (function == BuiltinFunctions.POP && !boundArguments.isEmpty()) {
            Class<?> elementType = _typeResolver.resolveArrayElementType(boundArguments.get(0));
            return new BoundCallExpression(function, boundArguments,
                    elementType == Object.class ? null : elementType);
        }

        GenericResult generic = inferGenericResultType(function, boundArguments);
        return genericCall(function, boundArguments, generic);
    }

    /**
     * The type a call to a generic function produces, worked out from the
     * arguments it was given.
     *
     * <p>A generic function is compiled once with its type parameters erased,
     * so {@code identity(5)} would otherwise have an erased type and need a
     * conversion at every use. Matching each parameter's written type against
     * the argument it received says what the parameters stand for here.
     *
     * @param function  The function being called.
     * @param arguments The bound arguments.
     * @return The call's type, or null when the declared return type stands.
     */
    private GenericResult inferGenericResultType(FunctionSymbol function, List<BoundExpression> arguments) {
        if (function.getTypeParameters().isEmpty()) return GenericResult.NONE;
        String returnTypeName = function.getDeclaredReturnTypeName();
        if (returnTypeName == null) return GenericResult.NONE;
        List<String> typeParameters = function.getTypeParameters();
        if (!TypeResolver.mentionsAny(returnTypeName, typeParameters)) return GenericResult.NONE;

        // The written result type, with each parameter bound to what its
        // arguments say and trusted only as a struct, sum type or interface:
        // keep(rows, p) is an Array<Row>, wrap(1) an Array<object> as in 0.9.0.
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < arguments.size() && i < function.getParameters().size(); i++) {
            TypeResolver.unify(function.getParameters().get(i).getDeclaredTypeName(),
                    typeNameOf(arguments.get(i)), typeParameters, bindings);
        }
        String resultTypeName = _typeResolver.substituteResult(returnTypeName, typeParameters, bindings);

        // The result's own class is what 0.9.0 gave it, so identity(5) + 1 still
        // adds; a struct or sum type result is also known by name.
        Class<?> resultType = legacyGenericResultType(function, arguments);
        // A bare T, or a container written over the parameters (Array<T>,
        // Map<K, V>), bound through a generic struct argument: g(b: Box<T>) -> T
        // given a Box<Array<float>>, or h(b: Box<Array<T>>) -> Array<T>. The
        // struct's argument is known and its reads checked on both backends.
        String returnBase = TypeResolver.genericBaseName(TypeResolver.canonicalTypeName(returnTypeName));
        boolean overParameters = typeParameters.contains(returnTypeName)
                || (("Array".equals(returnBase) || "Map".equals(returnBase) || "Set".equals(returnBase))
                    && TypeResolver.typeArgumentsOf(TypeResolver.canonicalTypeName(returnTypeName)).stream()
                        .allMatch(argument -> typeParameters.contains(argument) || !TypeResolver.mentionsAny(argument, typeParameters)));
        if (resultType == null || !typeParameters.contains(returnTypeName)) {
            Map<String, String> throughStructs = new HashMap<>();
            for (int i = 0; i < arguments.size() && i < function.getParameters().size(); i++) {
                String parameterTypeName = function.getParameters().get(i).getDeclaredTypeName();
                if (!_typeResolver.mentionsGenericStruct(parameterTypeName)) continue;
                TypeResolver.unify(parameterTypeName, typeNameOf(arguments.get(i)), typeParameters, throughStructs);
            }
            boolean allBound = overParameters;
            for (String parameter : typeParameters) {
                if (!TypeResolver.mentionsAny(returnTypeName, List.of(parameter))) continue;
                String bound = throughStructs.get(parameter);
                if (bound == null || bound.equals("object") || !bindings.getOrDefault(parameter, bound).equals(bound)) allBound = false;
            }
            if (allBound && !throughStructs.isEmpty()) {
                String name = TypeResolver.substitute(returnTypeName, typeParameters, throughStructs);
                Class<?> boundType = _typeResolver.lookupType(name);
                if (boundType != null && boundType != Object.class) return new GenericResult(boundType, name, true);
            }
        }
        Class<?> named = _typeResolver.lookupType(resultTypeName);
        if (resultType == null && (named == SiyoStruct.class || named == codeanalysis.SiyoUnion.class
                || named == SiyoArray.class || named == SiyoMap.class || named == SiyoSet.class)) {
            resultType = named;
        }
        return new GenericResult(resultType, resultTypeName.equals("object") ? null : resultTypeName);
    }

    /**
     * A generic call's class exactly as 0.9.0 inferred it: from a parameter
     * written as the bare type parameter, or as an array of it, read off the
     * argument's own class or tracked element class.
     */
    private Class<?> legacyGenericResultType(FunctionSymbol function, List<BoundExpression> arguments) {
        Map<String, Class<?>> bindings = new HashMap<>();
        for (int i = 0; i < arguments.size() && i < function.getParameters().size(); i++) {
            String parameterTypeName = function.getParameters().get(i).getDeclaredTypeName();
            if (parameterTypeName == null) continue;
            Class<?> argumentType = arguments.get(i).getClassType();
            if (function.getTypeParameters().contains(parameterTypeName)) {
                bindings.putIfAbsent(parameterTypeName, argumentType);
                continue;
            }
            String elementName = TypeResolver.elementTypeNameOf(parameterTypeName);
            if (elementName != null && function.getTypeParameters().contains(elementName)) {
                Class<?> elementType = _typeResolver.legacyArrayElementType(arguments.get(i));
                if (elementType != null) bindings.putIfAbsent(elementName, elementType);
            }
        }
        String returnTypeName = function.getDeclaredReturnTypeName();
        if (function.getTypeParameters().contains(returnTypeName)) {
            Class<?> bound = bindings.get(returnTypeName);
            return bound == null || bound == Object.class ? null : bound;
        }
        String returnElementName = TypeResolver.elementTypeNameOf(returnTypeName);
        if (returnElementName != null && function.getTypeParameters().contains(returnElementName)) {
            return SiyoArray.class;
        }
        return null;
    }

    private static boolean isUnboxed(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Double.class || type == Boolean.class;
    }

    /** What a generic call produces: its type, and that type written out. */
    private record GenericResult(Class<?> type, String typeName, boolean throughStruct) {
        GenericResult(Class<?> type, String typeName) {
            this(type, typeName, false);
        }

        static final GenericResult NONE = new GenericResult(null, null);
    }

    /** A generic call, its result typed and, when bound through a generic struct, recorded as such. */
    private BoundCallExpression genericCall(FunctionSymbol function, List<BoundExpression> arguments, GenericResult generic) {
        BoundCallExpression call = new BoundCallExpression(function, arguments, generic.type(), generic.typeName());
        if (generic.throughStruct()) _typeResolver.markBoundThroughStruct(call);
        return call;
    }

    /**
     * The type of an expression written out as a type name, with its type
     * arguments when they are known, so it can be matched against a generic
     * declaration: {@code Array<Row>}, {@code int}, {@code fn(Row)->bool}.
     *
     * @param expr The expression.
     * @return The type name, or null when nothing is known about it.
     */
    private String typeNameOf(BoundExpression expr) {
        String name = inferredTypeNameOf(expr);
        // A value whose inferred roots meet a contested method is not named by a
        // struct that contest puts at risk; an unrelated struct it holds still is.
        if (name != null && !_typeResolver.uncontestedName(name)
                && !_typeResolver.contestFree(_typeResolver.provenanceOf(expr))) {
            return null;
        }
        return name;
    }

    private String inferredTypeNameOf(BoundExpression expr) {
        String declared = _typeResolver.declaredTypeNameOf(expr);
        if (declared != null) return declared;
        if (expr instanceof BoundLambdaExpression lambda) return signatureNameOf(lambda);
        // An array literal of one element kind, named element by element so a
        // nested literal is whole: [[1.5]] is an Array<Array<float>> and
        // [{"k": 1.5}] an Array<Map<string,float>>. Only inference reads this.
        if (expr instanceof BoundArrayLiteralExpression array && !array.getElements().isEmpty()
                && _typeResolver.declaredTypeNameOf(array) == null) {
            String element = null;
            for (int i = 0; i < array.getElements().size(); i++) {
                String one = typeNameOf(array.getElements().get(i));
                element = i == 0 ? one : TypeResolver.joinTypeNames(element, one);
                if (element == null) break;
            }
            if (element != null && !element.equals("object")) return "Array<" + element + ">";
        }
        // A set literal of one element kind, as an array literal is named.
        if (expr instanceof BoundSetLiteralExpression set && !set.getElements().isEmpty()) {
            String element = null;
            for (int i = 0; i < set.getElements().size(); i++) {
                String one = typeNameOf(set.getElements().get(i));
                element = i == 0 ? one : TypeResolver.joinTypeNames(element, one);
                if (element == null) break;
            }
            if (element != null && !element.equals("object")) return "Set<" + element + ">";
        }
        // A map literal of one key kind and one value kind, number kinds joined
        // at the wider: Box { value: {"x": 1.5} } is a Box<Map<string,float>>.
        if (expr instanceof BoundMapLiteralExpression map && !map.getKeys().isEmpty()) {
            String key = null, value = null;
            for (int i = 0; i < map.getKeys().size(); i++) {
                String k = typeNameOf(map.getKeys().get(i));
                String v = typeNameOf(map.getValues().get(i));
                key = i == 0 ? k : TypeResolver.joinTypeNames(key, k);
                value = i == 0 ? v : TypeResolver.joinTypeNames(value, v);
                if (key == null || value == null) return "map";
            }
            return "Map<" + key + "," + value + ">";
        }
        // What follows names a value by 0.9.0's routes, for inference alone. A
        // value whose inferred identity no longer holds is not named by them
        // either, so a generic call over it stays erased as in 0.9.0.
        if (!_typeResolver.identityHolds(_typeResolver.provenanceOf(expr))) return null;
        if (expr instanceof BoundCallExpression call && !_typeResolver.returnOriginHolds(call.getFunction())) {
            return null;
        }
        Class<?> type = expr.getClassType();
        if (type == SiyoStruct.class) {
            StructSymbol struct = _typeResolver.resolveStructType(expr);
            return struct != null ? struct.getName() : null;
        }
        if (type == codeanalysis.SiyoUnion.class) {
            codeanalysis.UnionSymbol union = _typeResolver.resolveUnionType(expr);
            return union != null ? union.getName() : null;
        }
        if (type == SiyoArray.class) {
            StructSymbol struct = _typeResolver.resolveStructTypeFromCollection(expr);
            if (struct != null) return "Array<" + struct.getName() + ">";
            Class<?> elementType = _typeResolver.resolveArrayElementType(expr);
            return elementType == null || elementType == Object.class
                    ? null : "Array<" + siyoTypeName(elementType) + ">";
        }
        if (type == null || type == Object.class) return null;
        return siyoTypeName(type);
    }

    private BoundStatement bindForInStatement(ForInStatementSyntax syntax) {
        long generation = _typeResolver.generation();
        BoundExpression collection = bindExpression(syntax.getCollection());
        BoundExpression originalCollection = collection;
        String itemName = syntax.getItemName().getData();

        // Channel iteration: for msg in ch { ... } → while (true) { msg = ch.receive(); if msg == null break; body }
        if (collection.getClassType() == SiyoChannel.class) {
            return bindChannelForIn(syntax, collection, itemName);
        }

        // Map iteration: for key in map { ... } → for key in map.keys() { ... }
        // Set iteration: for item in set { ... } → for item in set.values() { ... }
        // The written type says what the loop variable is: a Map<K, V> walks Ks
        // and a Set<T> walks Ts. An untyped map or set walks erased values.
        String walkedTypeName = null;
        if (collection.getClassType() == SiyoMap.class) {
            walkedTypeName = TypeResolver.mapKeyTypeNameOf(_typeResolver.declaredTypeNameOf(collection));
            collection = new BoundCallExpression(BuiltinFunctions.MAP_KEYS,
                    java.util.List.of(collection));
        } else if (collection.getClassType() == SiyoSet.class) {
            walkedTypeName = TypeResolver.elementTypeNameOf(_typeResolver.declaredTypeNameOf(collection));
            collection = new BoundCallExpression(BuiltinFunctions.SET_VALUES,
                    java.util.List.of(collection));
        } else if (collection.getClassType() == Object.class) {
            // An erased value: what it is to walk it is decided when it runs.
            collection = new BoundCallExpression(BuiltinFunctions.ITERABLE, java.util.List.of(collection));
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
        if (walkedTypeName != null && _typeResolver.lookupType(walkedTypeName) != null) {
            elementType = _typeResolver.lookupType(walkedTypeName);
            structType = _structTypes.get(walkedTypeName);
        }
        if (structType != null) {
            _typeResolver.trackArrayType(collectionVar, elementType, structType);
        } else {
            _typeResolver.trackArrayType(collectionVar, elementType);
        }
        VariableSymbol itemVar = new VariableSymbol(itemName, false, elementType);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(itemVar);
        // Walking an Array<Array<T>> gives Array<T>s, whose elements are still Ts.
        String itemTypeName = walkedTypeName != null ? walkedTypeName
                : TypeResolver.elementTypeNameOf(_typeResolver.declaredTypeNameOf(collection));
        // An item is what its collection was inferred from, named or not.
        itemVar.addProvenance(_typeResolver.provenanceOf(originalCollection));
        // An item is taken out of its collection.
        TypeResolver.Storage collectionStorage = _typeResolver.storageOf(originalCollection);
        for (VariableSymbol container : collectionStorage.same) itemVar.elementOf(container);
        for (VariableSymbol container : collectionStorage.element) itemVar.elementOf(container);
        for (VariableSymbol held : collectionStorage.held) held.heldIn(itemVar);
        if (itemTypeName != null) {
            itemVar.setDeclaredTypeName(itemTypeName);
            // An element, a map key or a set element of a struct-derived collection.
            if (_typeResolver.isStructDerived(originalCollection)) _typeResolver.markStructDerived(itemVar, originalCollection);
            _typeResolver.stamp(itemVar, generation, !itemVar.getProvenance().isEmpty());
            markGenericTypeInferred(itemVar);
            String innerName = TypeResolver.elementTypeNameOf(itemTypeName);
            Class<?> innerType = _typeResolver.lookupType(innerName);
            if (elementType == SiyoArray.class && innerType != null) {
                StructSymbol innerStruct = _structTypes.get(innerName);
                if (innerStruct != null) _typeResolver.trackArrayType(itemVar, innerType, innerStruct);
                else _typeResolver.trackArrayType(itemVar, innerType);
            }
        }
        // Elements declared as an interface keep that identity, so a call on
        // the loop variable dispatches instead of resolving to whichever struct
        // happened to be written first.
        codeanalysis.InterfaceSymbol elementInterface = _typeResolver.resolveInterfaceElementType(collection);
        if (elementInterface != null) {
            _typeResolver.trackInterfaceType(itemVar, elementInterface);
        } else if (elementType == SiyoStruct.class && structType != null) {
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

    /**
     * Binds a set literal. Elements may be of any type, as a set holds values
     * the way a map holds keys.
     *
     * @param syntax The set literal.
     * @return The bound set literal.
     */
    private BoundExpression bindSetLiteralExpression(codeanalysis.syntax.SetLiteralExpressionSyntax syntax) {
        List<BoundExpression> elements = new ArrayList<>();
        for (ExpressionSyntax element : syntax.getElements()) {
            elements.add(bindExpression(element));
        }
        return new BoundSetLiteralExpression(elements);
    }

    private BoundExpression bindMapLiteralExpression(codeanalysis.syntax.MapLiteralExpressionSyntax syntax) {
        List<BoundExpression> boundKeys = new ArrayList<>();
        List<BoundExpression> boundValues = new ArrayList<>();

        for (ExpressionSyntax key : syntax.getKeys()) {
            boundKeys.add(bindExpression(key));
        }
        for (ExpressionSyntax value : syntax.getValues()) {
            boundValues.add(bindExpression(value));
        }

        return new BoundMapLiteralExpression(boundKeys, boundValues);
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

        // A map is indexed by its key, which is any value; everything else is
        // indexed by position.
        boolean indexesAMap = target.getClassType() == SiyoMap.class;
        if (!indexesAMap && index.getClassType() != Integer.class) {
            _diagnostics.reportCannotConvert(syntax.getIndex().getSpan(), index.getClassType(), Integer.class);
        }

        Class<?> resultType;
        if (indexesAMap) {
            Class<?> declaredValueType = _typeResolver.resolveMapValueType(target);
            resultType = declaredValueType != null ? declaredValueType : Object.class;
        } else if (target.getClassType() == SiyoArray.class) {
            resultType = _typeResolver.resolveArrayElementType(target);
        } else if (target.getClassType() == String.class) {
            resultType = String.class;
        } else if (target.getClassType() == Object.class) {
            // Allow indexing Object at compile time (e.g. actor method returns, dynamic dispatch).
            // At runtime this expects a SiyoArray — will ClassCast if not.
            resultType = Object.class;
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

            // A payload-less variant, qualified by its type: Option.None
            codeanalysis.UnionSymbol union = _moduleHandler.getUnionTypes().get(typeName);
            if (union != null) {
                return bindUnionConstruction(union, syntax.getMember().getData(), List.of(),
                        syntax.getMember().getSpan(), List.of());
            }

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

            // Module-level variable read through an import: module.name
            String qualifiedVariable = typeName + "." + syntax.getMember().getData();
            if (_moduleHandler.isImportedQualifier(typeName) && _scope.tryLookup(qualifiedVariable)) {
                return new BoundVariableExpression(_scope.lookupVariable(qualifiedVariable));
            }
            String privateOwner = _moduleHandler.privateMemberOwner(qualifiedVariable);
            if (privateOwner != null) {
                _diagnostics.reportPrivateMember(
                        syntax.getMember().getSpan(), qualifiedVariable, privateOwner);
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

        // An erased value whose declared type names a struct, an element of
        // keep(rows, p) or Some(r) on an Option<Row>, is read as that struct:
        // the access casts, so a value of another kind fails there on both
        // backends, and everything else about the value stays as 0.9.0 had it.
        if (target.getClassType() == Object.class && _typeResolver.resolveStructType(target) == null) {
            _diagnostics.reportCannotAccessMember(syntax.getDot().getSpan(), target.getClassType());
            return new BoundLiteralExpression(0);
        }
        if (target.getClassType() != SiyoStruct.class && target.getClassType() != Object.class) {
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

        if (structType != null && !structType.hasField(memberName)) {
            reportNoSuchField(syntax.getMember().getSpan(), structType, memberName);
            return new BoundLiteralExpression(0);
        }
        if (structType != null && structType.hasField(memberName)) {
            memberType = structType.getFieldType(memberName);
            // A generic struct's field reads at the type its value was given:
            // items of a Group<Row> is an Array<Row>, value of a Box<int> an int.
            if (!structType.getTypeParameters().isEmpty()) {
                Class<?> substituted = _typeResolver.lookupType(_typeResolver.fieldTypeNameOf(target, memberName));
                if (substituted != null) memberType = substituted;
            }
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
            return new BoundIndexAssignmentExpression(target, index, widenTo(value, slotType(target)));
        }

        if (syntax.getTarget() instanceof MemberAccessExpressionSyntax memberSyntax) {
            BoundExpression target = bindExpression(memberSyntax.getTarget());
            String memberName = memberSyntax.getMember().getData();
            StructSymbol structType = target.getClassType() == SiyoStruct.class || target.getClassType() == Object.class
                    ? _typeResolver.resolveStructType(target) : null;
            if (structType != null && !structType.hasField(memberName)) {
                reportNoSuchField(memberSyntax.getMember().getSpan(), structType, memberName);
            }
            if (structType != null && structType.hasField(memberName)) {
                Class<?> fieldType = structType.getFieldType(memberName);
                // A generic struct's field holds what the value's type argument says.
                if (!structType.getTypeParameters().isEmpty()) {
                    Class<?> substituted = _typeResolver.lookupType(_typeResolver.fieldTypeNameOf(target, memberName));
                    if (substituted != null) fieldType = substituted;
                }
                value = widenTo(value, fieldType);
                // A generic struct's literal widens to its type argument, as at
                // construction; a plain struct's assignment stays as 0.9.0 had it.
                if (!structType.getTypeParameters().isEmpty()) {
                    value = widenLiteral(value, _typeResolver.fieldTypeNameOf(target, memberName));
                }
                if (isScalar(fieldType) && isScalar(value.getClassType()) && fieldType != value.getClassType()) {
                    _diagnostics.reportCannotConvert(syntax.getEqualsToken().getSpan(), value.getClassType(), fieldType);
                }
            }
            return new BoundMemberAssignmentExpression(target, memberName, value);
        }

        _diagnostics.reportCannotAssign(syntax.getEqualsToken().getSpan(), "expression");
        return value;
    }

    /** What a container's slot is declared to hold, or Object when nothing says. */
    private Class<?> slotType(BoundExpression container) {
        if (container.getClassType() == SiyoMap.class) {
            Class<?> valueType = _typeResolver.resolveMapValueType(container);
            return valueType != null ? valueType : Object.class;
        }
        return container.getClassType() == SiyoArray.class
                ? _typeResolver.resolveArrayElementType(container) : Object.class;
    }

    /**
     * A field the struct does not declare. Reading one used to give null and
     * writing one added it silently, on both backends, so a misspelt field
     * name became a wrong value at run time instead of an error.
     */
    private void reportNoSuchField(codeanalysis.text.TextSpan span, StructSymbol structType, String fieldName) {
        _diagnostics.reportError(span, "Struct '" + structType.getName() + "' has no field '" + fieldName + "'");
    }

    private BoundExpression bindStructLiteralExpression(StructLiteralExpressionSyntax syntax) {
        String typeName = syntax.getTypeName().getData();
        StructSymbol structType = _structTypes.get(typeName);

        if (structType == null) {
            reportMissingType(syntax.getTypeName().getSpan(), typeName);
            return new BoundLiteralExpression(0);
        }

        java.util.LinkedHashMap<String, BoundExpression> fieldValues = new java.util.LinkedHashMap<>();
        for (SyntaxNode node : syntax.getFieldAssignments()) {
            FieldAssignmentSyntax field = (FieldAssignmentSyntax) node;
            String fieldName = field.getFieldName().getData();
            BoundExpression fieldValue = bindExpression(field.getValue());
            if (structType.hasField(fieldName)) {
                // A field widens like a parameter: `r: long` given 0 holds a long,
                // and reading an Integer back as one failed at run time.
                fieldValue = widenLiteral(widenTo(fieldValue, structType.getFieldType(fieldName)),
                        structType.getFieldTypeName(fieldName));
            }
            if (!structType.hasField(fieldName)) {
                reportNoSuchField(field.getFieldName().getSpan(), structType, fieldName);
            }
            fieldValues.put(fieldName, fieldValue);
        }

        return new BoundStructLiteralExpression(structType, fieldValues, genericStructTypeName(structType, fieldValues));
    }

    /**
     * The type a literal of a generic struct has, its type arguments read off
     * the values its fields were given: {@code Group { key: "a", items: rows }}
     * with {@code rows: Array<Row>} is a {@code Group<Row>}. A parameter no
     * field tells is erased to {@code object}.
     *
     * @param structType  The struct.
     * @param fieldValues The values given for its fields.
     * @return The type name with its arguments, or null for a struct that declares none.
     */
    private String genericStructTypeName(StructSymbol structType, Map<String, BoundExpression> fieldValues) {
        List<String> typeParameters = structType.getTypeParameters();
        if (typeParameters.isEmpty()) return null;
        Map<String, String> bindings = new HashMap<>();
        for (var entry : fieldValues.entrySet()) {
            TypeResolver.unify(structType.getFieldTypeName(entry.getKey()), typeNameOf(entry.getValue()),
                    typeParameters, bindings);
        }
        List<String> arguments = new ArrayList<>();
        for (String parameter : typeParameters) arguments.add(bindings.getOrDefault(parameter, "object"));
        // Kept as inferred: a field read applies the trust rule (TypeResolver.fieldTypeNameOf).
        return structType.getName() + "<" + String.join(",", arguments) + ">";
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
            // An actor's constructor runs right here. Any other call runs on its own
            // thread and holds what it uses, as a block does.
            java.util.Set<VariableSymbol> callCaptures = new java.util.LinkedHashSet<>();
            BoundSpawnExpression spawnExpr = new BoundSpawnExpression(block, callCaptures);

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
            StructSymbol spawned = spawnExpr.getActorTypeName() != null ? _structTypes.get(spawnExpr.getActorTypeName()) : null;
            if (spawned != null && spawned.isActor()) {
                spawnExpr.setExpressionForm(true);
            } else {
                java.util.Set<VariableSymbol> mutableCaptures = new java.util.LinkedHashSet<>();
                java.util.Set<VariableSymbol> sharedContainerCaptures = new java.util.LinkedHashSet<>();
                collectSpawnCaptures(block, callCaptures, mutableCaptures, sharedContainerCaptures);
                reportUnsafeSpawnCaptures(syntax, mutableCaptures, sharedContainerCaptures);
            }
            return spawnExpr;
        }

        // Block spawn: allowed inside scope (structured) or bare (fire-and-forget virtual thread)
        boolean wasInSpawn = _insideSpawn;
        _insideSpawn = true;

        // A loop outside the spawn is not one its break can leave.
        java.util.List<LoopLabels> enclosingLoops = new ArrayList<>(_loopStack);
        _loopStack.clear();
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.addAll(enclosingLoops);
        BoundBlockStatement block = body instanceof BoundBlockStatement b
                ? b : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));

        // Detect captured variables and CHECK for mutable captures
        java.util.Set<VariableSymbol> captured = new java.util.LinkedHashSet<>();
        java.util.Set<VariableSymbol> mutableCaptures = new java.util.LinkedHashSet<>();
        java.util.Set<VariableSymbol> sharedContainerCaptures = new java.util.LinkedHashSet<>();
        BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(block);

        // Compute captures on the LOWERED (flattened) body so synthetic vars are properly detected as locals
        collectSpawnCaptures(loweredBody, captured, mutableCaptures, sharedContainerCaptures);

        reportUnsafeSpawnCaptures(syntax, mutableCaptures, sharedContainerCaptures);

        _insideSpawn = wasInSpawn;

        return new BoundSpawnExpression(loweredBody, captured);
    }

    /**
     * A `mut` binding and an `imut` binding to a mutable container are rejected
     * for different reasons, and each is told which one applies.
     */
    private void reportUnsafeSpawnCaptures(SpawnExpressionSyntax syntax, java.util.Set<VariableSymbol> mutableCaptures,
                                           java.util.Set<VariableSymbol> sharedContainerCaptures) {
        for (VariableSymbol var : mutableCaptures) {
            _diagnostics.reportMutableCaptureInSpawn(syntax.getSpawnKeyword().getSpan(), var.getName());
        }
        for (VariableSymbol var : sharedContainerCaptures) {
            if (mutableCaptures.contains(var)) continue;
            _diagnostics.reportSharedContainerCaptureInSpawn(
                    syntax.getSpawnKeyword().getSpan(), var.getName(), describeType(var.getType()));
        }
    }

    private void collectSpawnCaptures(BoundNode node, java.util.Set<VariableSymbol> captured,
                                       java.util.Set<VariableSymbol> mutableCaptures,
                                       java.util.Set<VariableSymbol> sharedContainerCaptures) {
        // First: what this body declares, by symbol
        CaptureWalk declaredHere = new CaptureWalk();
        _captureWalk = declaredHere;
        collectExternalRefs(node, new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>());
        _captureWalk = null;
        java.util.Set<VariableSymbol> enclosingSpawnLocals = _spawnLocals;
        _spawnLocals = declaredHere.declared;

        // Then: find referenced variables that are NOT local → these are captures
        collectCapturedVarsFromBody(node, captured, mutableCaptures, sharedContainerCaptures);
        _spawnLocals = enclosingSpawnLocals;
        // A function it calls may not be finished yet, so its captures are settled later too.
        declaredHere.captures = captured;
        _captureWalks.add(declaredHere);
    }

    /** What the spawn body being walked declares, by symbol; a same-named variable outside is still captured. */
    private java.util.Set<VariableSymbol> _spawnLocals = java.util.Set.of();

    /** A type name a Siyo programmer would recognise, for diagnostics. */
    private static String describeType(Class<?> type) {
        if (type == SiyoStruct.class) return "a struct's";
        if (type == SiyoArray.class) return "an array's";
        if (type == codeanalysis.SiyoMap.class) return "a map's";
        if (type == codeanalysis.SiyoSet.class) return "a set's";
        return "its";
    }

    private void collectCapturedVarsFromBody(BoundNode node, java.util.Set<VariableSymbol> captured,
                                              java.util.Set<VariableSymbol> mutableCaptures,
                                              java.util.Set<VariableSymbol> sharedContainerCaptures) {
        // What a closure or block spawn nested here captures, this body has to hold
        // for it. `spawn Box.new(x)` runs its call in this body, so it is walked.
        if (node instanceof BoundLambdaExpression
                || (node instanceof BoundSpawnExpression nestedSpawn && !nestedSpawn.isExpressionForm())) {
            java.util.Set<VariableSymbol> nested = node instanceof BoundLambdaExpression lambda
                    ? lambda.getCapturedVariables() : ((BoundSpawnExpression) node).getCapturedVariables();
            for (VariableSymbol variable : nested) {
                collectCapturedVarsFromBody(new BoundVariableExpression(variable), captured,
                        mutableCaptures, sharedContainerCaptures);
            }
            return;
        }
        // Calling a nested function needs what that function holds on to.
        if (node instanceof BoundCallExpression call) {
            for (VariableSymbol variable : call.getFunction().getCapturedLocals()) {
                collectCapturedVarsFromBody(new BoundVariableExpression(variable), captured,
                        mutableCaptures, sharedContainerCaptures);
            }
        }
        // Variable reference → check if captured
        if (node instanceof BoundVariableExpression varExpr) {
            VariableSymbol var = varExpr.getVariable();
            if (!_spawnLocals.contains(var)) {
                captured.add(var);
                // Reject mutable variables (except channels, actor handles, Object/dynamic types, and synthetic loop vars)
                if (!var.isReadOnly() && var.getType() != SiyoChannel.class
                        && var.getType() != Object.class
                        && !var.getName().startsWith("_idx") && !var.getName().startsWith("_col")
                        && !isActorHandle(var)) {
                    mutableCaptures.add(var);
                }
                // Reject mutable containers even when imut — contents are mutable
                // Exempt: primitives, channels, actor handles (actors ARE meant to be shared)
                if (var.isReadOnly() && isMutableContainerType(var.getType())
                        && var.getType() != SiyoChannel.class
                        && !isActorHandle(var)) {
                    sharedContainerCaptures.add(var);
                }
            }
        }
        if (node instanceof BoundAssignmentExpression assignExpr) {
            VariableSymbol var = assignExpr.getVariable();
            if (!_spawnLocals.contains(var)) {
                captured.add(var);
                if (!var.isReadOnly()) { mutableCaptures.add(var); }
            }
        }
        for (BoundNode operand : BoundOperands.of(node)) {
            collectCapturedVarsFromBody(operand, captured, mutableCaptures, sharedContainerCaptures);
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
            param.setDeclaredTypeName(typeName);
            parameters.add(param);
            _scope.tryDeclare(param);
            Class<?> elementType = _typeResolver.lookupElementType(typeName);
            if (elementType != null) {
                String elementName = typeName.substring(0, typeName.length() - 2);
                StructSymbol elementStruct = _structTypes.get(elementName);
                if (elementStruct != null) _typeResolver.trackArrayType(param, elementType, elementStruct);
                else _typeResolver.trackArrayType(param, elementType);
            }
            StructSymbol structType = _structTypes.get(typeName);
            if (structType != null && paramType == SiyoStruct.class) {
                _typeResolver.trackStructType(param, structType);
            }
            codeanalysis.UnionSymbol unionType = _typeResolver.getUnionTypes().get(typeName);
            if (unionType != null && paramType == codeanalysis.SiyoUnion.class) {
                _typeResolver.trackUnionType(param, unionType);
            }
            codeanalysis.JavaClassInfo javaType = _typeResolver.getJavaClasses().get(typeName);
            if (javaType != null) _typeResolver.trackJavaClassType(param, javaType);
        }

        // Return type
        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            returnType = _typeResolver.lookupType(syntax.getTypeClause().getIdentifier().getData());
        }

        // Set _currentFunction so return statements are allowed inside lambda body
        FunctionSymbol previousFunction = _currentFunction;
        _currentFunction = new FunctionSymbol("$lambda", parameters, returnType);

        // Bind body. A loop outside the lambda is not one its break can leave.
        java.util.List<LoopLabels> enclosingLoops = new ArrayList<>(_loopStack);
        _loopStack.clear();
        BoundStatement body = bindStatement(syntax.getBody());
        _loopStack.addAll(enclosingLoops);
        _currentFunction = previousFunction;
        BoundBlockStatement blockBody = body instanceof BoundBlockStatement block
                ? block : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));

        // Detect captured variables (variables referenced in body but declared in outer scope)
        java.util.Set<VariableSymbol> captured = new java.util.LinkedHashSet<>();
        java.util.Set<VariableSymbol> writtenCaptures = new java.util.LinkedHashSet<>();
        collectCapturedVars(blockBody, parameters, captured, writtenCaptures);

        // A closure shares the mutable locals it captures: the variable moves
        // into a cell that the closure captures, so a write on either side is
        // seen by the other. An immutable local is captured by value, and a
        // module-level variable is already a static field, so neither needs a
        // cell. Writing to a captured immutable is reported where it is written.
        for (VariableSymbol var : captured) {
            if (!var.isReadOnly() && var.getOwnerClass() == null) {
                var.setCell(true);
            }
        }
        // writtenCaptures no longer drives a diagnostic; it stays as the record
        // of which captures are written, which the cell rule above subsumes.
        writtenCaptures.clear();

        // Restore scope
        _scope = outerScope;
        _moduleHandler.setScope(_scope);

        // A lambda returns the value of its body exactly as a named function
        // does. Without this a tail if/else was left a statement: the
        // interpreter still took its value, the emitter returned the default.
        // Lower the body
        BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(
                applyImplicitReturn(blockBody, returnType));

        return new BoundLambdaExpression(parameters, loweredBody, returnType, captured)
                .withReturnTypeName(syntax.getTypeClause() != null ? syntax.getTypeClause().getIdentifier().getData() : null);
    }

    private void collectCapturedVars(BoundNode node, List<ParameterSymbol> params,
                                      java.util.Set<VariableSymbol> captured) {
        collectCapturedVars(node, params, captured, new java.util.LinkedHashSet<>());
    }

    private void collectCapturedVars(BoundNode node, List<ParameterSymbol> params,
                                      java.util.Set<VariableSymbol> captured,
                                      java.util.Set<VariableSymbol> writtenCaptures) {
        // By symbol: a local of the same name in some inner block is another variable,
        // and treating a name as local hid a read of the outer one in the same body.
        CaptureWalk walk = new CaptureWalk();
        walk.declared.addAll(params);
        walk.captures = captured;
        _captureWalk = walk;
        collectExternalRefs(node, captured, writtenCaptures);
        _captureWalk = null;
        captured.removeAll(walk.declared);
        captured.addAll(walk.outerOnly());
        walk.holdsGlobals = true;
        _captureWalks.add(walk);
    }

    /**
     * Works out which of its parent's variables a nested function holds on to:
     * everything it refers to that it does not declare itself, short of the
     * globals. Going by the symbol rather than the name keeps its own locals,
     * bindings and catch variables out and its parent's in, whatever they are
     * called. A mutable one moves into a cell, as for a lambda.
     */
    private void collectFunctionCaptures(FunctionSymbol function, BoundBlockStatement body) {
        java.util.Set<VariableSymbol> referenced = new java.util.LinkedHashSet<>();
        CaptureWalk walk = new CaptureWalk();
        _captureWalk = walk;
        collectExternalRefs(body, referenced, new java.util.LinkedHashSet<>());
        _captureWalk = null;
        walk.declared.addAll(function.getParameters());
        for (VariableSymbol variable : referenced) {
            if (!walk.declared.contains(variable) && isOuterLocal(variable)) function.getCapturedLocals().add(variable);
        }
        walk.captures = function.getCapturedLocals();
        _captureWalks.add(walk);
        settleCaptures();
        for (VariableSymbol variable : function.getCapturedLocals()) {
            if (!variable.isReadOnly()) variable.setCell(true);
        }
    }

    /**
     * One walk over a function or lambda body. A body's captures include those of
     * what it calls and of closures inside it, which may not be complete yet: a
     * lambda inside k that calls k is bound before k's body is finished. So the walk
     * is kept, and settled again when a nested function is finished.
     */
    private static final class CaptureWalk {
        final java.util.Set<BoundNode> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final java.util.Set<VariableSymbol> declared = new java.util.HashSet<>();
        final java.util.List<java.util.Set<VariableSymbol>> sources = new java.util.ArrayList<>();
        java.util.Set<VariableSymbol> captures;
        /** A lambda passes globals on to the closures inside it; a named function reads them directly. */
        boolean holdsGlobals;

        /** What the sources capture that this body does not declare. */
        java.util.Set<VariableSymbol> outerOnly() {
            java.util.Set<VariableSymbol> result = new java.util.LinkedHashSet<>();
            for (java.util.Set<VariableSymbol> source : sources) {
                for (VariableSymbol variable : source) {
                    if (!declared.contains(variable)) result.add(variable);
                }
            }
            return result;
        }
    }

    private CaptureWalk _captureWalk;
    private final java.util.List<CaptureWalk> _captureWalks = new java.util.ArrayList<>();

    private void settleCaptures() {
        boolean grew = true;
        while (grew) {
            grew = false;
            for (CaptureWalk walk : _captureWalks) {
                for (VariableSymbol variable : walk.outerOnly()) {
                    boolean outerLocal = isOuterLocal(variable);
                    if ((outerLocal || walk.holdsGlobals) && walk.captures.add(variable)) {
                        grew = true;
                        if (outerLocal && !variable.isReadOnly()) variable.setCell(true);
                    }
                }
            }
        }
    }

    /** The scope around the outermost function being bound: what it holds are globals. */
    private BoundScope _fileLevelScope;

    /** A local of some function, rather than a global or a module's variable: what has to be captured. */
    private boolean isOuterLocal(VariableSymbol variable) {
        if (variable.getOwnerClass() != null) return false;
        for (BoundScope scope = _fileLevelScope; scope != null; scope = scope.getParent()) {
            for (VariableSymbol global : scope.getDeclaredVariables()) {
                if (global == variable) return false;
            }
        }
        return true;
    }

    /**
     * Walks a function or lambda body for the variables it refers to and declares.
     * A closure or spawn nested in it is not entered: what it captures, this body
     * has to hold for it, and that is read from its own capture set.
     */
    private void collectExternalRefs(BoundNode node, java.util.Set<VariableSymbol> captured,
                                     java.util.Set<VariableSymbol> writtenCaptures) {
        // A node may be shared, as the tail of more than one rewritten form.
        if (!_captureWalk.visited.add(node)) return;
        if (node instanceof BoundVariableExpression variable) captured.add(variable.getVariable());
        if (node instanceof BoundAssignmentExpression assignment) {
            captured.add(assignment.getVariable());
            writtenCaptures.add(assignment.getVariable());
        }
        if (node instanceof BoundVariableDeclaration declaration) _captureWalk.declared.add(declaration.getVariable());
        if (node instanceof BoundTryCatchStatement tryCatch) _captureWalk.declared.add(tryCatch.getErrorVariable());
        if (node instanceof BoundTryExpression tryExpression) _captureWalk.declared.add(tryExpression.getErrorVariable());
        if (node instanceof BoundMatchExpression match) {
            for (BoundMatchExpression.BoundMatchArm arm : match.getArms()) {
                if (arm.variant() == null) continue;
                for (VariableSymbol binding : arm.variant().bindings()) {
                    if (binding != null) _captureWalk.declared.add(binding);
                }
            }
        }
        if (node instanceof BoundCallExpression call) _captureWalk.sources.add(call.getFunction().getCapturedLocals());
        if (node instanceof BoundLambdaExpression lambda) {
            _captureWalk.sources.add(lambda.getCapturedVariables());
            return;
        }
        // `spawn Box.new(x)` runs its call right here; only a block has its own captures.
        if (node instanceof BoundSpawnExpression spawn && !spawn.isExpressionForm()) {
            _captureWalk.sources.add(spawn.getCapturedVariables());
            return;
        }
        for (BoundNode operand : BoundOperands.of(node)) {
            collectExternalRefs(operand, captured, writtenCaptures);
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

    private BoundExpression bindPostfixCallExpression(codeanalysis.syntax.PostfixCallExpressionSyntax syntax) {
        BoundExpression callee = bindExpression(syntax.getCallee());
        List<BoundExpression> args = new ArrayList<>();
        for (ExpressionSyntax argSyntax : syntax.getArguments()) {
            args.add(bindExpression(argSyntax));
        }
        return new BoundClosureCallExpression(callee, args);
    }

    private BoundExpression bindMemberCallExpression(MemberCallExpressionSyntax syntax) {
        MemberAccessExpressionSyntax memberAccess = syntax.getMemberAccess();

        // A variant may be written qualified by its type: Result.Ok(5). This is
        // what disambiguates a variant name that two sum types both declare.
        if (memberAccess.getTarget() instanceof NameExpressionSyntax typeNameExpr) {
            codeanalysis.UnionSymbol union = _moduleHandler.getUnionTypes()
                    .get(typeNameExpr.getIdentifierToken().getData());
            if (union != null) {
                List<BoundExpression> payload = new ArrayList<>();
                for (ExpressionSyntax argSyntax : syntax.getArguments()) {
                    payload.add(bindExpression(argSyntax));
                }
                return bindUnionConstruction(union, memberAccess.getMember().getData(), payload,
                        syntax.getSpan(), argumentSpans(syntax.getArguments()));
            }
        }

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
                    // A narrower number widens at a parameter of another
                    // module's function as at one of this file's.
                    boundArgs.set(i, widenTo(boundArgs.get(i), func.getParameters().get(i).getType()));
                    BoundExpression arg = boundArgs.get(i);
                    ParameterSymbol param = func.getParameters().get(i);
                    if (param.getType() != Object.class && arg.getClassType() != Object.class && arg.getClassType() != param.getType()) {
                        _diagnostics.reportWrongArgumentType(syntax.getArguments().get(i).getSpan(), param.getName(), param.getType(), arg.getClassType());
                    }
                }

                GenericResult generic = inferGenericResultType(func, boundArgs);
                return genericCall(func, boundArgs, generic);
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
                        ? javaClass.resolveConstructor(boundArgs.size(), getArgTypes(boundArgs), getArgJvmDescriptors(boundArgs))
                        : javaClass.resolveMethod(funcName, boundArgs.size(), getArgTypes(boundArgs), getArgJvmDescriptors(boundArgs));

                if (resolved == null) {
                    if (javaClass.wasLastResolutionAmbiguous()) {
                        _diagnostics.reportAmbiguousJavaCall(memberAccess.getMember().getSpan(), targetName + "." + funcName);
                    } else {
                        _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(), targetName + "." + funcName);
                    }
                    return new BoundLiteralExpression(0);
                }

                codeanalysis.JavaResolvedType returnType = _typeResolver.resolveMethodReturnType(
                        resolved, new codeanalysis.JavaResolvedType(javaClass));
                return new BoundJavaMethodCallExpression(javaClass, null, funcName, boundArgs, resolved, returnType);
            }

            if (_moduleHandler.isImportedQualifier(targetName)) {
                String owner = _moduleHandler.privateMemberOwner(qualifiedName);
                if (owner != null) {
                    _diagnostics.reportPrivateMember(
                            memberAccess.getMember().getSpan(), qualifiedName, owner);
                } else {
                    _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(), qualifiedName);
                }
                return new BoundLiteralExpression(0);
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

        // A value reached through an interface dispatches on the struct it
        // turns out to be, which is not known until the program runs.
        if (target.getClassType() == SiyoStruct.class) {
            codeanalysis.InterfaceSymbol declaredInterface = _typeResolver.resolveInterfaceType(target);
            if (declaredInterface != null && declaredInterface.getMethod(methodName) != null) {
                return bindInterfaceCall(declaredInterface, target, methodName, boundArgs,
                        memberAccess.getMember().getSpan());
            }
        }

        // Check for struct instance method: u.greet() → User.greet(u)
        if (target.getClassType() == SiyoStruct.class) {
            StructSymbol structType = _typeResolver.resolveStructType(target);
            // A value whose identity 0.10.0 inferred, from roots that no longer
            // hold (see TypeResolver.provenanceOf), is not dispatched through it:
            // its fields still read as tracked, but which method body runs could
            // differ by backend. It fails as the erased value 0.9.0 had.
            if (structType != null && _typeResolver.dispatchAtRisk(structType)
                    && !_typeResolver.provenanceHolds(target)) {
                structType = null;
            }
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
                // A field holding a function is callable: route.handler(req, res).
                // Without this the call fell through to Java dispatch and failed
                // at run time against the struct's internal representation.
                if (structType.hasField(methodName)) {
                    Class<?> fieldType = structType.getFieldType(methodName);
                    if (fieldType == SiyoClosure.class || fieldType == Object.class) {
                        return new BoundClosureCallExpression(
                                new BoundMemberAccessExpression(target, methodName, fieldType), boundArgs);
                    }
                    _diagnostics.reportFieldIsNotCallable(memberAccess.getMember().getSpan(),
                            structType.getName(), methodName);
                    return new BoundLiteralExpression(0);
                }
            }
        }

        // A function whose first parameter takes the receiver may be written as
        // a method on it: text.toUpper() is toUpper(text). Without this a chain
        // had to be unwound into nested calls the moment a builtin appeared in
        // it, because only a struct's own impl methods could be written postfix.
        //
        // A Siyo value takes this route first, since a builtin over it says
        // more than the Java method its representation happens to carry. An
        // imported Java object does not: its own methods are the point, and a
        // same-named Siyo function must not shadow them.
        if (isSiyoValueType(target.getClassType())) {
            BoundExpression asMethod = bindReceiverCall(target, methodName, boundArgs);
            if (asMethod != null) return asMethod;
        }

        // Resolve Java class info from the target expression (with generic type bindings)
        codeanalysis.JavaResolvedType targetResolvedType = _typeResolver.resolveJavaResolvedType(target);
        codeanalysis.JavaClassInfo targetClassInfo = targetResolvedType != null
                ? targetResolvedType.getClassInfo()
                : _typeResolver.resolveJavaClassInfo(target);
        codeanalysis.JavaMethodSignature resolved = null;
        codeanalysis.JavaResolvedType resolvedReturnType = null;
        if (targetClassInfo != null) {
            resolved = targetClassInfo.resolveMethod(methodName, boundArgs.size(),
                    getArgTypes(boundArgs), getArgJvmDescriptors(boundArgs));
            if (resolved == null) {
                // No Java method of that name; a Siyo function taking the
                // receiver is the remaining reading of the call.
                BoundExpression asMethod = bindReceiverCall(target, methodName, boundArgs);
                if (asMethod != null) return asMethod;
                if (targetClassInfo.getFullName().equals("java.lang.Object")) {
                    // Object class — method not found, fall through to dynamic dispatch
                    targetClassInfo = null;
                } else {
                    if (targetClassInfo.wasLastResolutionAmbiguous()) {
                        _diagnostics.reportAmbiguousJavaCall(memberAccess.getMember().getSpan(),
                                targetClassInfo.getSimpleName() + "." + methodName);
                    } else {
                        _diagnostics.reportUndefinedFunction(memberAccess.getMember().getSpan(),
                                targetClassInfo.getSimpleName() + "." + methodName);
                    }
                    return new BoundLiteralExpression(0);
                }
            }
            // Resolve generic return type using owner's type bindings
            resolvedReturnType = _typeResolver.resolveMethodReturnType(resolved, targetResolvedType);
        }

        return new BoundJavaMethodCallExpression(targetClassInfo, target, methodName, boundArgs, resolved, resolvedReturnType);
    }

    /**
     * Binds {@code receiver.name(args)} as {@code name(receiver, args)} when a
     * function of that name takes the receiver as its first parameter.
     *
     * <p>Only a function whose first parameter actually accepts the receiver's
     * type qualifies, so this never captures a call that was meant for a Java
     * method of the same name on an unrelated type.</p>
     *
     * @param receiver   The value the method was written on.
     * @param name       The method name.
     * @param arguments  The arguments written after it.
     * @return The bound call, or null when no function matches.
     */
    private BoundExpression bindReceiverCall(BoundExpression receiver, String name,
                                             List<BoundExpression> arguments) {
        if (!_scope.tryLookupFunction(name)) return null;

        List<Class<?>> argTypes = new ArrayList<>();
        argTypes.add(receiver.getClassType());
        for (BoundExpression argument : arguments) argTypes.add(argument.getClassType());

        FunctionSymbol function = _scope.lookupFunction(name, argTypes);
        if (function == null) function = _scope.lookupFunction(name, arguments.size() + 1);
        if (function == null) return null;
        if (function.getParameters().size() != arguments.size() + 1) return null;

        Class<?> firstParameter = function.getParameters().get(0).getType();
        if (!acceptsReceiver(firstParameter, receiver.getClassType())) return null;

        List<BoundExpression> callArguments = new ArrayList<>();
        callArguments.add(widenTo(receiver, firstParameter));
        for (int i = 0; i < arguments.size(); i++) {
            callArguments.add(widenTo(arguments.get(i), function.getParameters().get(i + 1).getType()));
        }
        GenericResult generic = inferGenericResultType(function, callArguments);
        return genericCall(function, callArguments, generic);
    }

    /**
     * Whether a parameter of one type can take a receiver of another.
     *
     * @param parameterType The declared parameter type.
     * @param receiverType  The receiver's type.
     * @return true when the receiver may be passed as that parameter.
     */
    private static boolean acceptsReceiver(Class<?> parameterType, Class<?> receiverType) {
        if (parameterType == receiverType) return true;
        // An erased receiver is deliberately not accepted: it would let any
        // same-named function capture a call meant for a Java object's method.
        if (parameterType == Object.class) return true;
        return widerNumericType(receiverType, parameterType) == parameterType;
    }

    /**
     * Whether a type is one of Siyo's own value types, as opposed to an
     * imported Java class or an erased value.
     *
     * @param type The receiver's type.
     * @return true for a Siyo value type.
     */
    private static boolean isSiyoValueType(Class<?> type) {
        return type == String.class
                || type == SiyoArray.class
                || type == SiyoMap.class
                || type == codeanalysis.SiyoSet.class
                || type == codeanalysis.SiyoUnion.class
                || type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Boolean.class;
    }

    /**
     * Binds a call through an interface, gathering the method each implementing
     * struct provides so dispatch can compare against the receiver's struct
     * name rather than look the method up reflectively.
     *
     * @param declared   The interface.
     * @param target     The receiver.
     * @param methodName The method being called.
     * @param arguments  The arguments, not counting the receiver.
     * @param span       The span of the method name, for diagnostics.
     * @return The bound interface call.
     */
    private BoundExpression bindInterfaceCall(codeanalysis.InterfaceSymbol declared, BoundExpression target,
                                              String methodName, List<BoundExpression> arguments,
                                              codeanalysis.text.TextSpan span) {
        FunctionSymbol required = declared.getMethod(methodName);
        if (arguments.size() != required.getParameters().size()) {
            _diagnostics.reportWrongArgumentCount(span, declared.getName() + "." + methodName,
                    required.getParameters().size(), arguments.size());
            return new BoundLiteralExpression(0);
        }

        Map<String, FunctionSymbol> implementations = new java.util.LinkedHashMap<>();
        for (String structName : declared.getImplementors()) {
            String qualifiedName = structName + "." + methodName;
            if (_scope.tryLookupFunction(qualifiedName)) {
                implementations.put(structName, _scope.lookupFunction(qualifiedName));
            }
        }
        if (implementations.isEmpty()) {
            _diagnostics.reportNoInterfaceImplementors(span, declared.getName(), methodName);
            return new BoundLiteralExpression(0);
        }

        List<BoundExpression> widened = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            widened.add(widenTo(arguments.get(i), required.getParameters().get(i).getType()));
        }
        return new BoundInterfaceCallExpression(target, declared.getName(), methodName,
                widened, implementations, required.getReturnType());
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

    /**
     * Registers an interface and the methods it requires, before any body is
     * bound, so an impl block and a parameter may both name it.
     *
     * @param syntax The interface declaration.
     */
    private void registerInterfaceDeclaration(InterfaceDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        codeanalysis.InterfaceSymbol previous = _typeResolver.getInterfaceTypes().get(name);
        codeanalysis.InterfaceSymbol declared = new codeanalysis.InterfaceSymbol(name);
        // Registering again once every type is known resolves signatures that
        // named a struct declared below the interface; the structs already
        // known to implement it are carried over.
        if (previous != null) {
            for (String implementor : previous.getImplementors()) declared.addImplementor(implementor);
        }
        for (FunctionDeclarationSyntax method : syntax.getMethods()) {
            List<ParameterSymbol> parameters = new ArrayList<>();
            for (ParameterSyntax parameterSyntax : method.getParameters()) {
                Class<?> parameterType = _typeResolver.lookupType(parameterSyntax.getTypeToken().getData());
                parameters.add(new ParameterSymbol(parameterSyntax.getIdentifier().getData(),
                        parameterType != null ? parameterType : Object.class));
            }
            Class<?> returnType = method.getTypeClause() == null
                    ? null
                    : _typeResolver.lookupType(method.getTypeClause().getIdentifier().getData());
            declared.declareMethod(new FunctionSymbol(
                    method.getIdentifier().getData(), parameters, returnType));
        }
        _typeResolver.getInterfaceTypes().put(name, declared);
    }

    /**
     * An interface declares no code of its own, so binding it produces nothing
     * to run. Its methods were registered before any body was bound.
     *
     * @param syntax The interface declaration.
     * @return An empty statement.
     */
    private BoundStatement bindInterfaceDeclaration(InterfaceDeclarationSyntax syntax) {
        registerInterfaceDeclaration(syntax);
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /**
     * Checks that a struct has every method its interface requires, with the
     * signature the interface declares, and records it as an implementor.
     *
     * @param syntax The {@code impl Interface for Struct} block.
     */
    private void checkInterfaceConformance(ImplDeclarationSyntax syntax) {
        String interfaceName = syntax.getInterfaceName().getData();
        codeanalysis.InterfaceSymbol declared = _typeResolver.getInterfaceTypes().get(interfaceName);
        if (declared == null) {
            _diagnostics.reportUndefinedType(syntax.getInterfaceName().getSpan(), interfaceName);
            return;
        }
        String structName = syntax.getTypeName().getData();
        if (!_structTypes.containsKey(structName)) {
            reportMissingType(syntax.getTypeName().getSpan(), structName);
            return;
        }

        for (FunctionSymbol required : declared.getMethods()) {
            String qualifiedName = structName + "." + required.getName();
            FunctionSymbol provided = _scope.tryLookupFunction(qualifiedName)
                    ? _scope.lookupFunction(qualifiedName)
                    : null;
            if (provided == null) {
                _diagnostics.reportMissingInterfaceMethod(syntax.getTypeName().getSpan(),
                        structName, interfaceName, required.getName());
                continue;
            }
            // The receiver is the implementation's first parameter and is not
            // part of what the interface declares.
            int providedArity = provided.getParameters().size() - 1;
            if (providedArity != required.getParameters().size()) {
                _diagnostics.reportInterfaceMethodSignature(syntax.getTypeName().getSpan(),
                        structName, interfaceName, required.getName(),
                        "it takes " + providedArity + " argument" + (providedArity == 1 ? "" : "s")
                        + " where the interface declares " + required.getParameters().size());
                continue;
            }
            if (required.getReturnType() != null && provided.getReturnType() != required.getReturnType()) {
                _diagnostics.reportInterfaceMethodSignature(syntax.getTypeName().getSpan(),
                        structName, interfaceName, required.getName(),
                        "it returns " + siyoTypeName(provided.getReturnType())
                        + " where the interface declares " + siyoTypeName(required.getReturnType()));
            }
        }
        declared.addImplementor(structName);
    }

    private BoundStatement bindImplDeclaration(ImplDeclarationSyntax syntax) {
        String structName = syntax.getTypeName().getData();
        StructSymbol structType = _structTypes.get(structName);
        if (syntax.getInterfaceName() != null) {
            checkInterfaceConformance(syntax);
        }

        for (FunctionDeclarationSyntax method : syntax.getMethods()) {
            String qualifiedName = structName + "." + method.getIdentifier().getData();
            if (!_scope.tryLookupFunction(qualifiedName)) continue;
            FunctionSymbol func = _scope.lookupFunction(qualifiedName);

            // Bind the method body in a new scope with parameters
            _scope = new BoundScope(_scope);
            _moduleHandler.setScope(_scope);
            int parameterIndex = 0;
            for (ParameterSymbol param : func.getParameters()) {
                _scope.tryDeclare(param);
                if (param.getName().equals("self") && structType != null) {
                    _typeResolver.trackStructType(param, structType);
                }
                if (parameterIndex < method.getParameters().getCount()) {
                    String typeName = method.getParameters().get(parameterIndex).getTypeToken().getData();
                    codeanalysis.JavaClassInfo javaType = _typeResolver.getJavaClasses().get(typeName);
                    if (javaType != null) _typeResolver.trackJavaClassType(param, javaType);
                    Class<?> elementType = _typeResolver.lookupElementType(typeName);
                    if (elementType != null) {
                        String elementName = typeName.substring(0, typeName.length() - 2);
                        StructSymbol elementStruct = _structTypes.get(elementName);
                        if (elementStruct != null) _typeResolver.trackArrayType(param, elementType, elementStruct);
                        else _typeResolver.trackArrayType(param, elementType);
                    }
                    StructSymbol parameterStruct = _structTypes.get(typeName);
                    if (parameterStruct != null && param.getType() == SiyoStruct.class) {
                        _typeResolver.trackStructType(param, parameterStruct);
                    }
                }
                parameterIndex++;
            }

            _currentFunction = func;
            BoundStatement body = bindStatement(method.getBody());
            _currentFunction = null;

            _scope = _scope.getParent();
            _moduleHandler.setScope(_scope);

            BoundBlockStatement methodBody = body instanceof BoundBlockStatement block
                    ? block
                    : new BoundBlockStatement(new ArrayList<>(java.util.List.of(body)));
            BoundBlockStatement loweredBody = codeanalysis.lowering.Lowerer.lower(
                    applyImplicitReturn(methodBody, func.getReturnType()));
            _functionBodies.put(func, loweredBody);
        }

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /**
     * A function returns the value of its body. The tail statement of the body
     * therefore becomes an explicit return, recursively through the forms whose
     * own value is the value of a nested tail: a block, an if/else, a try/catch.
     *
     * <p>Without this only a bare expression statement produced a value, so
     * {@code fn reason(c: int) -> string { if c == 200 { "OK" } else { "?" } }}
     * compiled and returned null. Doing it here rather than in the emitter means
     * lowering sees ordinary returns and every backend agrees.
     *
     * @param body The bound function body.
     * @param returnType The declared return type, or null for a void function.
     * @return The body with its tail rewritten to return.
     */
    BoundBlockStatement applyImplicitReturn(BoundBlockStatement body, Class<?> returnType) {
        return applyImplicitReturn(body, returnType, null);
    }

    private String _implicitReturnTypeName;

    /** Whether a statement ends in an expression whose value would be its result. */
    private static boolean endsInValue(BoundStatement statement) {
        if (statement instanceof BoundBlockStatement block) {
            var statements = block.getStatements();
            return !statements.isEmpty() && endsInValue(statements.get(statements.size() - 1));
        }
        return statement instanceof BoundExpressionStatement expression
                && expression.getExpression().getClassType() != null
                && !(expression.getExpression() instanceof BoundAssignmentExpression);
    }

    BoundBlockStatement applyImplicitReturn(BoundBlockStatement body, Class<?> returnType, String returnTypeName) {
        _implicitReturnTypeName = returnTypeName;
        if (returnType == null) return body;
        var statements = new ArrayList<>(body.getStatements());
        if (statements.isEmpty()) return body;
        int last = statements.size() - 1;
        BoundStatement rewritten = implicitReturnOf(statements.get(last), returnType);
        if (rewritten == null) return body;
        statements.set(last, rewritten);
        return new BoundBlockStatement(statements);
    }

    /**
     * Rewrites one statement so that the value it produces is returned, or
     * returns null when the statement produces no value.
     */
    private BoundStatement implicitReturnOf(BoundStatement statement, Class<?> returnType) {
        if (statement instanceof BoundExpressionStatement expressionStatement) {
            BoundExpression value = expressionStatement.getExpression();
            // A match whose arms disagree has no value to return.
            if (value instanceof BoundMatchExpression match && returnType == Object.class
                    && _erasableMatches.containsKey(match)) {
                return new BoundReturnStatement(_erasableMatches.get(match));
            }
            if (value instanceof BoundMatchExpression match && _mixedArmMatches.containsKey(match)) {
                _diagnostics.reportError(_mixedArmMatches.get(match),
                        _mixedArmMessages.getOrDefault(match, MIXED_ARMS));
                return null;
            }
            // An assignment used as a statement is a side effect, not a value,
            // and an expression with no value has nothing to return.
            if (value instanceof BoundAssignmentExpression || value.getClassType() == null) return null;
            // The tail of the body is the return value, so it is widened to the
            // declared type the same way an explicit return is. Without this
            // `fn f() -> float { 3 }` emitted an int where a double was
            // expected and the class failed verification.
            return new BoundReturnStatement(widenLiteral(widenTo(value, returnType), _implicitReturnTypeName));
        }
        // These already leave the function, so they count as an arm with a value.
        if (statement instanceof BoundReturnStatement || statement instanceof BoundThrowStatement) return statement;
        if (statement instanceof BoundBlockStatement block) {
            var statements = new ArrayList<>(block.getStatements());
            if (statements.isEmpty()) return null;
            int last = statements.size() - 1;
            BoundStatement rewritten = implicitReturnOf(statements.get(last), returnType);
            if (rewritten == null) return null;
            statements.set(last, rewritten);
            return new BoundBlockStatement(statements);
        }
        if (statement instanceof BoundIfStatement ifStatement) {
            // Only when both arms produce a value; a one-armed if falls through.
            if (ifStatement.getElseStatement() == null) return null;
            BoundStatement thenBranch = implicitReturnOf(ifStatement.getThenStatement(), returnType);
            BoundStatement elseBranch = implicitReturnOf(ifStatement.getElseStatement(), returnType);
            // One arm gives a value and the other falls off the end: the value
            // would be lost, on one backend only.
            if ((thenBranch == null && endsInValue(ifStatement.getElseStatement()))
                    || (elseBranch == null && endsInValue(ifStatement.getThenStatement()))) {
                _diagnostics.reportError(new codeanalysis.text.TextSpan(
                        Math.max(0, ifStatement.getCondition().getSourceOffset()), 1), MIXED_ARMS);
            }
            if (thenBranch == null || elseBranch == null) return null;
            return new BoundIfStatement(ifStatement.getCondition(), thenBranch, elseBranch);
        }
        if (statement instanceof BoundTryCatchStatement tryCatch) {
            BoundStatement tryBody = implicitReturnOf(tryCatch.getTryBody(), returnType);
            BoundStatement catchBody = implicitReturnOf(tryCatch.getCatchBody(), returnType);
            if ((tryBody == null && endsInValue(tryCatch.getCatchBody()))
                    || (catchBody == null && endsInValue(tryCatch.getTryBody()))) {
                _diagnostics.reportError(new codeanalysis.text.TextSpan(0, 1), MIXED_ARMS);
            }
            if (tryBody == null || catchBody == null) return null;
            return new BoundTryCatchStatement(tryBody, tryCatch.getErrorVariable(), catchBody);
        }
        return null;
    }

    private BoundStatement bindTryCatchStatement(TryCatchStatementSyntax syntax) {
        BoundStatement tryBody = bindStatement(syntax.getTryBody());
        VariableSymbol errorVar = declareErrorVariable(
                syntax.getErrorVariable(), syntax.getErrorTypeToken());
        BoundStatement catchBody = bindStatement(syntax.getCatchBody());
        _scope = _scope.getParent();
        _moduleHandler.setScope(_scope);
        return new BoundTryCatchStatement(tryBody, errorVar, catchBody);
    }

    /**
     * Raises the value of an expression. Whatever the expression evaluates to is
     * what the catch block binds, so an error may be a variant, a struct or a
     * number and not only text.
     *
     * @param syntax The throw statement to bind.
     * @return The bound throw statement.
     */
    private BoundStatement bindThrowStatement(ThrowStatementSyntax syntax) {
        BoundExpression value = bindExpression(syntax.getExpression());
        return new BoundThrowStatement(value);
    }

    /**
     * Declares the catch variable in a fresh scope and returns it. The caller
     * pops the scope after binding the catch body.
     *
     * <p>Without an annotation the variable is the erased payload, because what
     * reaches a handler is only known at run time. With one it carries the
     * declared type, which is what makes a match over a caught sum type
     * checkable.</p>
     *
     * @param nameToken The catch variable's name.
     * @param typeToken The declared type, or null.
     * @return The declared catch variable.
     */
    private VariableSymbol declareErrorVariable(SyntaxToken nameToken, SyntaxToken typeToken) {
        String errorName = nameToken.getData();
        Class<?> declared = Object.class;
        String typeName = typeToken != null ? typeToken.getData() : null;
        if (typeName != null) {
            Class<?> resolved = _typeResolver.lookupType(typeName);
            if (resolved == null) {
                reportMissingType(nameToken.getSpan(), typeName);
            } else {
                declared = resolved;
            }
        }
        VariableSymbol errorVar = new VariableSymbol(errorName, true, declared);
        _scope = new BoundScope(_scope);
        _moduleHandler.setScope(_scope);
        _scope.tryDeclare(errorVar);
        if (typeName != null) {
            String declaredName = TypeResolver.erasedTypeName(typeName);
            codeanalysis.UnionSymbol unionType = _unionTypes.get(declaredName);
            if (unionType != null) _typeResolver.trackUnionType(errorVar, unionType);
            StructSymbol structType = _structTypes.get(declaredName);
            if (structType != null) _typeResolver.trackStructType(errorVar, structType);
            Class<?> elementType = _typeResolver.lookupElementType(typeName);
            if (elementType != null) _typeResolver.trackArrayType(errorVar, elementType);
        }
        return errorVar;
    }

    private Class<?>[] getArgTypes(List<BoundExpression> args) {
        Class<?>[] types = new Class<?>[args.size()];
        for (int i = 0; i < args.size(); i++) types[i] = args.get(i).getClassType();
        return types;
    }

    private String[] getArgJvmDescriptors(List<BoundExpression> args) {
        String[] descriptors = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            codeanalysis.JavaResolvedType resolved = _typeResolver.resolveJavaResolvedType(args.get(i));
            if (resolved != null && resolved.getClassInfo() != null) {
                descriptors[i] = "L" + resolved.getClassInfo().getInternalName() + ";";
            }
        }
        return descriptors;
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
