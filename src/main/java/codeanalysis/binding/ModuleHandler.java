package codeanalysis.binding;

import codeanalysis.BuiltinFunctions;
import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.ModuleRegistry;
import codeanalysis.ModuleSymbol;
import codeanalysis.ParameterSymbol;
import codeanalysis.SiyoStruct;
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
    private String _currentModuleName; // set when compiling a module (e.g., "db")

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

    public void setCurrentModuleName(String moduleName) {
        _currentModuleName = moduleName;
    }

    public String getCurrentModuleName() {
        return _currentModuleName;
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
        // For nested paths like "util/str", use the last segment as qualifier
        String shortName = moduleName.contains("/")
                ? moduleName.substring(moduleName.lastIndexOf('/') + 1)
                : moduleName.contains(".")
                ? moduleName.substring(moduleName.lastIndexOf('.') + 1)
                : moduleName;
        String className = Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
        for (FunctionSymbol func : module.getFunctions()) {
            if (BuiltinFunctions.isBuiltin(func)) continue;
            // Register with qualified name: module.func
            String qualifiedName = shortName + "." + func.getName();
            FunctionSymbol importedFunc = new FunctionSymbol(
                    qualifiedName, func.getParameters(), func.getReturnType(), className);
            importedFunc.setReturnStructName(func.getReturnStructName());
            _scope.tryDeclareFunction(importedFunc);
            BoundBlockStatement body = module.getFunctionBodies().get(func);
            if (body != null) {
                _functionBodies.put(importedFunc, body);
                // Also register with original unqualified name for internal cross-references
                _functionBodies.put(func, body);
            }
        }

        // Register imported structs
        for (var entry : module.getStructs().entrySet()) {
            _structTypes.put(entry.getKey(), entry.getValue());
        }

        // Register imported impl methods (Struct.method)
        for (FunctionSymbol func : module.getFunctions()) {
            if (func.getName().contains(".") && !func.getName().startsWith(moduleName + ".")) {
                // This is a struct impl method like "Vec2.new"
                _scope.tryDeclareFunction(func);
                BoundBlockStatement body = module.getFunctionBodies().get(func);
                if (body != null) {
                    _functionBodies.put(func, body);
                }
            }
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

        // Support dot notation: "util.str" → "util/str"
        String pathName = moduleName.replace('.', '/');

        // 1. Same directory: math.siyo
        java.nio.file.Path candidate = java.nio.file.Paths.get(basePath, pathName + ".siyo");
        if (java.nio.file.Files.exists(candidate)) {
            return candidate.toAbsolutePath().toString();
        }

        // 2. Subdirectory with index: util/ → util/util.siyo (or util/index.siyo)
        java.nio.file.Path dirCandidate = java.nio.file.Paths.get(basePath, pathName);
        if (java.nio.file.Files.isDirectory(dirCandidate)) {
            // Try dir/index.siyo
            java.nio.file.Path indexFile = dirCandidate.resolve("index.siyo");
            if (java.nio.file.Files.exists(indexFile)) {
                return indexFile.toAbsolutePath().toString();
            }
            // Try dir/dirname.siyo
            String dirName = dirCandidate.getFileName().toString();
            java.nio.file.Path namedFile = dirCandidate.resolve(dirName + ".siyo");
            if (java.nio.file.Files.exists(namedFile)) {
                return namedFile.toAbsolutePath().toString();
            }
        }

        // 3. Project src/ root (when siyo.toml exists)
        codeanalysis.project.SiyoProject project = codeanalysis.project.SiyoProject.getCurrent();
        if (project != null) {
            java.nio.file.Path srcRoot = project.getSourceRoot();
            candidate = srcRoot.resolve(pathName + ".siyo");
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            // Subdirectory in src/
            dirCandidate = srcRoot.resolve(pathName);
            if (java.nio.file.Files.isDirectory(dirCandidate)) {
                java.nio.file.Path indexFile = dirCandidate.resolve("index.siyo");
                if (java.nio.file.Files.exists(indexFile)) {
                    return indexFile.toAbsolutePath().toString();
                }
                String dirName = dirCandidate.getFileName().toString();
                java.nio.file.Path namedFile = dirCandidate.resolve(dirName + ".siyo");
                if (java.nio.file.Files.exists(namedFile)) {
                    return namedFile.toAbsolutePath().toString();
                }
            }
        }

        // 4. CWD root (fallback)
        String projectRoot = System.getProperty("user.dir");
        candidate = java.nio.file.Paths.get(projectRoot, pathName + ".siyo");
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
            // Derive short module name for self-reference resolution
            String shortName = moduleName.contains("/")
                    ? moduleName.substring(moduleName.lastIndexOf('/') + 1)
                    : moduleName.contains(".")
                    ? moduleName.substring(moduleName.lastIndexOf('.') + 1)
                    : moduleName;
            moduleBinder.getModuleHandler().setCurrentModuleName(shortName);
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
        if (returnType == SiyoStruct.class && syntax.getTypeClause() != null) {
            function.setReturnStructName(syntax.getTypeClause().getIdentifier().getData());
        }
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

    public void registerImplDeclaration(ImplDeclarationSyntax syntax) {
        String structName = syntax.getTypeName().getData();
        for (FunctionDeclarationSyntax method : syntax.getMethods()) {
            String methodName = method.getIdentifier().getData();
            String qualifiedName = structName + "." + methodName;

            List<ParameterSymbol> parameters = new ArrayList<>();
            boolean isInstance = false;
            for (ParameterSyntax paramSyntax : method.getParameters()) {
                String paramName = paramSyntax.getIdentifier().getData();
                if (paramName.equals("self")) {
                    isInstance = true;
                    parameters.add(new ParameterSymbol("self", true, SiyoStruct.class));
                    continue;
                }
                String typeName = paramSyntax.getTypeToken().getData();
                Class<?> paramType = _typeResolver.lookupType(typeName);
                if (paramType == null) paramType = Integer.class;
                parameters.add(new ParameterSymbol(paramName, paramSyntax.isMutable(), paramType));
            }

            Class<?> returnType = null;
            if (method.getTypeClause() != null) {
                String rtName = method.getTypeClause().getIdentifier().getData();
                returnType = _typeResolver.lookupType(rtName);
                if (returnType == null && rtName.equals(structName)) returnType = SiyoStruct.class;
            }

            FunctionSymbol func = new FunctionSymbol(qualifiedName, parameters, returnType);
            if (returnType == SiyoStruct.class && method.getTypeClause() != null) {
                func.setReturnStructName(method.getTypeClause().getIdentifier().getData());
            }
            _scope.tryDeclareFunction(func);
        }
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
