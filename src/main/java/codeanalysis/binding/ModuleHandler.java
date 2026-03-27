package codeanalysis.binding;

import codeanalysis.BuiltinFunctions;
import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.ModuleRegistry;
import codeanalysis.ModuleSymbol;
import codeanalysis.ParameterSymbol;
import codeanalysis.StructSymbol;
import codeanalysis.syntax.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles module imports, enum/struct/function registration, and module compilation.
 * Extracted from Binder to separate module-handling concerns.
 */
public class ModuleHandler {
    private final java.util.Set<String> _importedModules = new java.util.HashSet<>();
    private final Map<String, Map<String, Integer>> _enumTypes = new HashMap<>();
    private ModuleRegistry _registry;
    private String _filePath;

    private final Map<String, StructSymbol> _structTypes;
    private final TypeResolver _typeResolver;

    // These are provided by the Binder for callback access
    private DiagnosticBox _diagnostics;
    private BoundScope _scope;
    private final Map<FunctionSymbol, BoundBlockStatement> _functionBodies;

    public ModuleHandler(Map<String, StructSymbol> structTypes, TypeResolver typeResolver,
                         Map<FunctionSymbol, BoundBlockStatement> functionBodies) {
        _structTypes = structTypes;
        _typeResolver = typeResolver;
        _functionBodies = functionBodies;
    }

    // --- State setters (called by Binder to keep in sync) ---

    public void setDiagnostics(DiagnosticBox diagnostics) {
        _diagnostics = diagnostics;
    }

    public void setScope(BoundScope scope) {
        _scope = scope;
    }

    public void setRegistry(ModuleRegistry registry) {
        _registry = registry;
    }

    public void setFilePath(String filePath) {
        _filePath = filePath;
    }

    public ModuleRegistry getRegistry() {
        return _registry;
    }

    public String getFilePath() {
        return _filePath;
    }

    public Map<String, Map<String, Integer>> getEnumTypes() {
        return _enumTypes;
    }

    // --- Import handling ---

    public BoundStatement bindImportStatement(ImportStatementSyntax syntax) {
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

    public BoundStatement bindJavaImportStatement(JavaImportStatementSyntax syntax) {
        String fullClassName = (String) syntax.getClassName().getValue();
        if (fullClassName == null) return new BoundExpressionStatement(new BoundLiteralExpression(0));

        String simpleName = fullClassName.contains(".")
                ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1)
                : fullClassName;

        // Load class metadata via ASM ClassReader (no reflection!)
        codeanalysis.JavaClassMetadata metadata = codeanalysis.JavaClassMetadata.load(fullClassName);
        if (metadata == null) {
            _diagnostics.reportModuleNotFound(syntax.getClassName().getSpan(), fullClassName);
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }
        _typeResolver.getJavaClasses().put(simpleName, new codeanalysis.JavaClassInfo(simpleName, fullClassName, metadata));

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    public String resolveModulePath(String moduleName) {
        String basePath = _filePath != null
                ? java.nio.file.Paths.get(_filePath).getParent().toString()
                : System.getProperty("user.dir");
        java.nio.file.Path candidate = java.nio.file.Paths.get(basePath, moduleName + ".siyo");
        if (java.nio.file.Files.exists(candidate)) {
            return candidate.toAbsolutePath().toString();
        }
        return null;
    }

    public ModuleSymbol compileModule(String moduleName, String filePath) {
        try {
            if (_registry != null) _registry.markInProgress(filePath);

            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
            codeanalysis.syntax.SyntaxTree tree = codeanalysis.syntax.SyntaxTree.parse(source);

            // Create a dedicated binder for the module so we can access its struct types
            var parentScope = Binder.createParentScopes(null);
            Binder moduleBinder = new Binder(parentScope);
            moduleBinder.getModuleHandler().setRegistry(_registry);
            moduleBinder.getModuleHandler().setFilePath(filePath);
            BoundStatement statement = moduleBinder.bindStatement(tree.getRoot().getStatement());

            if (moduleBinder._diagnostics.size() > 0) {
                _diagnostics.addAll(moduleBinder._diagnostics);
                if (_registry != null) _registry.markComplete(filePath);
                return null;
            }

            String className = Character.toUpperCase(moduleName.charAt(0)) + moduleName.substring(1);

            Map<FunctionSymbol, BoundBlockStatement> bodies = new HashMap<>(moduleBinder._functionBodies);
            List<FunctionSymbol> functions = new ArrayList<>(bodies.keySet());
            Map<String, StructSymbol> structs = new HashMap<>(moduleBinder.getStructTypes());

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

    // --- Registration (first pass) ---

    public void registerFunctionDeclaration(FunctionDeclarationSyntax syntax) {
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
            Class<?> parameterType = _typeResolver.lookupType(typeName);
            if (parameterType == null) parameterType = Integer.class;
            parameters.add(new ParameterSymbol(parameterName, parameterSyntax.isMutable(), parameterType));
        }

        Class<?> returnType = null;
        if (syntax.getTypeClause() != null) {
            returnType = _typeResolver.lookupType(syntax.getTypeClause().getIdentifier().getData());
        }

        FunctionSymbol function = new FunctionSymbol(name, parameters, returnType);
        _scope.tryDeclareFunction(function);
    }

    public void registerStructDeclaration(StructDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_structTypes.containsKey(name)) return;

        java.util.LinkedHashMap<String, Class<?>> fields = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> fieldTypeNames = new java.util.LinkedHashMap<>();
        for (ParameterSyntax field : syntax.getFields()) {
            String fieldName = field.getIdentifier().getData();
            String typeName = field.getTypeToken().getData();
            Class<?> fieldType = _typeResolver.lookupType(typeName);
            if (fieldType == null) fieldType = Integer.class;
            fields.put(fieldName, fieldType);
            fieldTypeNames.put(fieldName, typeName);
        }
        _structTypes.put(name, new StructSymbol(name, fields, fieldTypeNames));
    }

    public void registerEnumDeclaration(EnumDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_enumTypes.containsKey(name)) return;
        Map<String, Integer> members = new HashMap<>();
        int ordinal = 0;
        for (SyntaxToken member : syntax.getMembers()) {
            members.put(member.getData(), ordinal++);
        }
        _enumTypes.put(name, members);
    }

    public BoundStatement bindEnumDeclaration(EnumDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (!_enumTypes.containsKey(name)) {
            registerEnumDeclaration(syntax);
        }
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    public BoundStatement bindStructDeclaration(StructDeclarationSyntax syntax) {
        // Struct already registered in first pass, just validate
        String name = syntax.getIdentifier().getData();
        if (!_structTypes.containsKey(name)) {
            registerStructDeclaration(syntax);
        }
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }
}
