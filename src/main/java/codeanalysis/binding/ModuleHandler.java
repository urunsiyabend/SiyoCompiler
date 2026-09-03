package codeanalysis.binding;

import codeanalysis.BuiltinFunctions;
import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.ModuleRegistry;
import codeanalysis.ModuleSymbol;
import codeanalysis.ParameterSymbol;
import codeanalysis.SiyoArray;
import codeanalysis.SiyoStruct;
import codeanalysis.StructSymbol;
import codeanalysis.VariableSymbol;
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
    private final java.util.Set<String> _importedClassNames = new java.util.LinkedHashSet<>();
    private final Map<String, Map<String, Integer>> _enumTypes = new HashMap<>();
    private final Map<String, codeanalysis.UnionSymbol> _unionTypes;
    private ModuleRegistry _registry;
    private String _filePath;
    private String _currentModuleName; // set when compiling a module (e.g., "db")

    private final Map<String, StructSymbol> _structTypes;
    private final TypeResolver _typeResolver;

    // These are provided by the Binder for callback access
    private DiagnosticBox _diagnostics;
    private BoundScope _scope;
    private final Map<FunctionSymbol, BoundBlockStatement> _functionBodies;

    public ModuleHandler(Map<String, StructSymbol> structTypes,
                         Map<String, codeanalysis.UnionSymbol> unionTypes,
                         TypeResolver typeResolver,
                         Map<FunctionSymbol, BoundBlockStatement> functionBodies) {
        _structTypes = structTypes;
        _unionTypes = unionTypes;
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

    public Map<String, codeanalysis.UnionSymbol> getUnionTypes() {
        return _unionTypes;
    }

    public Map<String, Map<String, Integer>> getEnumTypes() {
        return _enumTypes;
    }

    /** JVM class names of every module imported by the file being bound. */
    public java.util.Set<String> getImportedClassNames() {
        return _importedClassNames;
    }

    public boolean isImportedQualifier(String qualifier) {
        for (String moduleName : _importedModules) {
            String shortName = moduleName;
            int slash = shortName.lastIndexOf('/');
            if (slash >= 0) shortName = shortName.substring(slash + 1);
            int dot = shortName.lastIndexOf('.');
            if (dot >= 0) shortName = shortName.substring(dot + 1);
            if (shortName.equals(qualifier)) return true;
        }
        return false;
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
                _diagnostics.markModuleFailed(shortModuleName(moduleName));
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
        if (moduleName.startsWith("std/") || moduleName.startsWith("std.")) {
            className = "Siyo_" + className;
        }
        _importedClassNames.add(className);

        for (FunctionSymbol func : module.getFunctions()) {
            if (BuiltinFunctions.isBuiltin(func)) continue;
            // Register with qualified name: module.func
            String qualifiedName = shortName + "." + func.getName();
            FunctionSymbol importedFunc = new FunctionSymbol(
                    qualifiedName, func.getParameters(), func.getReturnType(), className);
            importedFunc.setReturnStructName(func.getReturnStructName());
            importedFunc.setReturnUnionName(func.getReturnUnionName());
            importedFunc.setReturnElementType(func.getReturnElementType());
            importedFunc.setReturnElementStructName(func.getReturnElementStructName());
            importedFunc.setJvmMethodName(func.getName().replace('.', '$'));
            importedFunc.setOriginModule(func.getOriginModule());
            _scope.tryDeclareFunction(importedFunc);
            BoundBlockStatement body = module.getFunctionBodies().get(func);
            if (body != null) {
                // Only the qualified symbol carries the body. Registering the
                // module's own bare symbol here used to leak it into this file's
                // exports, which made two modules' same-named functions collide
                // and emitted a module's body into the wrong class — taking its
                // static fields out of scope with it.
                _functionBodies.put(importedFunc, body);
            }
        }

        // Methods of re-exported structs travel with them, keeping the class
        // that declared each one as its JVM owner.
        for (var entry : module.getInheritedMethods().entrySet()) {
            FunctionSymbol source = entry.getKey();
            FunctionSymbol inherited = new FunctionSymbol(source.getName(), source.getParameters(),
                    source.getReturnType(), source.getModuleName());
            inherited.setReturnStructName(source.getReturnStructName());
            inherited.setReturnUnionName(source.getReturnUnionName());
            inherited.setReturnElementType(source.getReturnElementType());
            inherited.setReturnElementStructName(source.getReturnElementStructName());
            inherited.setJvmMethodName(source.getJvmMethodName());
            inherited.setOriginModule(source.getOriginModule());
            if (_scope.tryDeclareFunction(inherited) && entry.getValue() != null) {
                _functionBodies.put(inherited, entry.getValue());
            }
        }

        // Module-level variables are exported as module.name, reading the static
        // field on the module's own class.
        for (var entry : module.getVariables().entrySet()) {
            String qualifiedName = shortName + "." + entry.getKey();
            VariableSymbol source = entry.getValue();
            VariableSymbol imported = new VariableSymbol(
                    qualifiedName, source.isReadOnly(), source.getType());
            imported.setOwner(className, entry.getKey());
            _scope.tryDeclare(imported);
        }

        // Register imported structs
        for (var entry : module.getStructs().entrySet()) {
            _structTypes.put(entry.getKey(), entry.getValue());
        }

        // Register imported enums so EnumName.Member remains usable across modules.
        for (var entry : module.getEnums().entrySet()) {
            _enumTypes.putIfAbsent(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // Register imported sum types, so both their name and their variants
        // remain usable across a module boundary.
        for (var entry : module.getUnions().entrySet()) {
            _unionTypes.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Register imported impl methods (Struct.method)
        for (FunctionSymbol func : module.getFunctions()) {
            if (func.getName().contains(".") && !func.getName().startsWith(moduleName + ".")) {
                // This is a struct impl method like "Vec2.new"
                FunctionSymbol importedImpl = new FunctionSymbol(
                        func.getName(), func.getParameters(), func.getReturnType(), className);
                importedImpl.setReturnStructName(func.getReturnStructName());
                importedImpl.setReturnUnionName(func.getReturnUnionName());
                importedImpl.setReturnElementType(func.getReturnElementType());
                importedImpl.setReturnElementStructName(func.getReturnElementStructName());
                importedImpl.setJvmMethodName(func.getName().replace('.', '$'));
                importedImpl.setOriginModule(func.getOriginModule());
                _scope.tryDeclareFunction(importedImpl);
                BoundBlockStatement body = module.getFunctionBodies().get(func);
                if (body != null) {
                    _functionBodies.put(importedImpl, body);
                }
            }
        }

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /** The name a module's symbols are qualified with. */
    static String shortModuleName(String moduleName) {
        if (moduleName == null) return null;
        int slash = moduleName.lastIndexOf('/');
        if (slash >= 0) return moduleName.substring(slash + 1);
        int dot = moduleName.lastIndexOf('.');
        if (dot >= 0) return moduleName.substring(dot + 1);
        return moduleName;
    }

    public BoundStatement bindJavaImportStatement(JavaImportStatementSyntax syntax) {
        String requestedName = (String) syntax.getClassName().getValue();
        if (requestedName == null) return new BoundExpressionStatement(new BoundLiteralExpression(0));

        // A nested class may be written either way: java.net.http.HttpResponse$BodyHandlers
        // or the dotted java.net.http.HttpResponse.BodyHandlers. Both bind to
        // the innermost name, so the class can actually be referred to — the
        // binary name is not a Siyo identifier.
        String binaryName = resolveJavaBinaryName(requestedName);
        if (binaryName == null) {
            _diagnostics.reportModuleNotFound(syntax.getClassName().getSpan(), requestedName);
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }

        codeanalysis.JavaClassMetadata metadata = codeanalysis.JavaClassMetadata.load(binaryName);
        if (metadata == null) {
            _diagnostics.reportModuleNotFound(syntax.getClassName().getSpan(), requestedName);
            return new BoundExpressionStatement(new BoundLiteralExpression(0));
        }
        String simpleName = simpleJavaName(binaryName);
        _typeResolver.getJavaClasses().put(simpleName,
                new codeanalysis.JavaClassInfo(simpleName, binaryName, metadata));

        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /**
     * Finds the JVM binary name for an imported Java class.
     *
     * <p>Tries the name as written, then treats trailing dotted segments as
     * nested classes, right to left: {@code a.b.C.D} becomes {@code a.b.C$D}.
     *
     * @param requestedName The name as written in the import.
     * @return The binary name of a class that loads, or null.
     */
    static String resolveJavaBinaryName(String requestedName) {
        if (codeanalysis.JavaClassMetadata.load(requestedName) != null) return requestedName;
        StringBuilder candidate = new StringBuilder(requestedName);
        for (int dot = candidate.lastIndexOf("."); dot > 0; dot = candidate.lastIndexOf(".", dot - 1)) {
            candidate.setCharAt(dot, '$');
            String attempt = candidate.toString();
            if (codeanalysis.JavaClassMetadata.load(attempt) != null) return attempt;
        }
        return null;
    }

    /** The name a nested or top-level Java class is referred to by in Siyo. */
    static String simpleJavaName(String binaryName) {
        int nested = binaryName.lastIndexOf('$');
        if (nested >= 0) return binaryName.substring(nested + 1);
        int dot = binaryName.lastIndexOf('.');
        return dot >= 0 ? binaryName.substring(dot + 1) : binaryName;
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

        // 5. Standard library: std/ from classpath resources or dev filesystem
        if (pathName.startsWith("std/")) {
            // Dev mode: check std/ relative to CWD (for development)
            candidate = java.nio.file.Paths.get(projectRoot, "src", "main", "resources", pathName + ".siyo");
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            // Production: check classpath resource (inside JAR)
            String resourcePath = pathName + ".siyo";
            if (ModuleHandler.class.getClassLoader().getResource(resourcePath) != null) {
                return "classpath:" + resourcePath;
            }
        }

        return null;
    }

    public ModuleSymbol compileModule(String moduleName, String filePath) {
        try {
            if (_registry != null) _registry.markInProgress(filePath);

            String source;
            if (filePath.startsWith("classpath:")) {
                String resourcePath = filePath.substring("classpath:".length());
                try (java.io.InputStream is = ModuleHandler.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) throw new java.io.FileNotFoundException("Resource not found: " + resourcePath);
                    source = new String(is.readAllBytes());
                }
            } else {
                source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
            }
            codeanalysis.syntax.SyntaxTree tree = codeanalysis.syntax.SyntaxTree.parse(source);

            // Create a dedicated binder for the module so we can access its struct types
            var parentScope = Binder.createParentScopes(null);
            Binder moduleBinder = new Binder(parentScope);
            // Diagnostics raised while binding this module name this module, not
            // whichever file happened to import it.
            moduleBinder.getDiagnostics().setSource(filePath, tree.getText());
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
                // Everything this module would have exported is now missing.
                // Reporting each missing symbol would bury the real error.
                _diagnostics.markModuleFailed(shortName);
                if (_registry != null) _registry.markComplete(filePath);
                return null;
            }

            // Generate a valid JVM class name — prefix std modules to avoid collisions (e.g., Math → Siyo_Math)
            String classBase = shortName;
            String className = Character.toUpperCase(classBase.charAt(0)) + classBase.substring(1);
            if (moduleName.startsWith("std/") || moduleName.startsWith("std.")) {
                className = "Siyo_" + className;
            }

            // A module exports only functions declared in that source file.
            // Imported symbols also live in the binder's body map so local calls
            // can resolve, but re-exporting them changes their JVM owner in the
            // next importer (A -> B -> C becomes C.B$method).
            Map<FunctionSymbol, BoundBlockStatement> bodies = new HashMap<>();
            for (var entry : moduleBinder._functionBodies.entrySet()) {
                if (entry.getKey().getModuleName() == null) {
                    bodies.put(entry.getKey(), entry.getValue());
                }
            }
            List<FunctionSymbol> functions = new ArrayList<>(bodies.keySet());
            Map<String, StructSymbol> structs = new HashMap<>(moduleBinder.getStructTypes());

            // Preserve the module's top-level block so module-level variables
            // can be emitted as static fields on the module class.
            BoundBlockStatement topLevelBlock = null;
            if (statement instanceof BoundBlockStatement blk) {
                topLevelBlock = codeanalysis.lowering.Lowerer.lower(blk);
            }

            Map<String, Map<String, Integer>> enums = new HashMap<>();
            for (var entry : moduleBinder.getModuleHandler().getEnumTypes().entrySet()) {
                enums.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            ModuleSymbol module = new ModuleSymbol(moduleName, className, filePath,
                    functions, bodies, structs, enums, topLevelBlock);
            module.setUnions(new java.util.LinkedHashMap<>(moduleBinder.getModuleHandler().getUnionTypes()));
            module.setVariables(collectTopLevelVariables(topLevelBlock, className));
            module.setInheritedMethods(collectInheritedMethods(moduleBinder, structs));
            module.setImportedClassNames(
                    new java.util.LinkedHashSet<>(moduleBinder.getModuleHandler().getImportedClassNames()));
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

    /**
     * Impl methods that reached this module through its own imports, for the
     * structs it re-exports. They keep their declaring class as JVM owner.
     */
    private Map<FunctionSymbol, BoundBlockStatement> collectInheritedMethods(
            Binder moduleBinder, Map<String, StructSymbol> structs) {
        Map<FunctionSymbol, BoundBlockStatement> methods = new java.util.LinkedHashMap<>();
        for (var entry : moduleBinder._functionBodies.entrySet()) {
            FunctionSymbol function = entry.getKey();
            if (function.getModuleName() == null) continue; // declared here, already exported
            int dot = function.getName().indexOf('.');
            if (dot <= 0) continue;
            String owner = function.getName().substring(0, dot);
            if (!structs.containsKey(owner)) continue; // not a method of a re-exported struct
            methods.put(function, entry.getValue());
        }
        return methods;
    }

    /**
     * Top-level variable declarations become static fields on the module class,
     * so they are the module's exportable state. Synthetic variables introduced
     * by lowering are not part of the module's surface.
     */
    private Map<String, codeanalysis.VariableSymbol> collectTopLevelVariables(
            BoundBlockStatement topLevelBlock, String className) {
        Map<String, codeanalysis.VariableSymbol> variables = new java.util.LinkedHashMap<>();
        if (topLevelBlock == null) return variables;
        for (BoundStatement stmt : topLevelBlock.getStatements()) {
            if (!(stmt instanceof BoundVariableDeclaration declaration)) continue;
            codeanalysis.VariableSymbol variable = declaration.getVariable();
            String name = variable.getName();
            if (name.startsWith("_idx") || name.startsWith("_col") || name.startsWith("_ch")) continue;
            variables.putIfAbsent(name, variable);
        }
        return variables;
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
        function.setOriginModule(_filePath);
        if (returnType == codeanalysis.SiyoUnion.class && syntax.getTypeClause() != null) {
            function.setReturnUnionName(syntax.getTypeClause().getIdentifier().getData());
        }
        if (returnType == SiyoStruct.class && syntax.getTypeClause() != null) {
            function.setReturnStructName(syntax.getTypeClause().getIdentifier().getData());
        }
        if (returnType == SiyoArray.class && syntax.getTypeClause() != null) {
            String typeName = syntax.getTypeClause().getIdentifier().getData();
            Class<?> elementType = _typeResolver.lookupElementType(typeName);
            function.setReturnElementType(elementType != null ? elementType : Object.class);
            if (typeName.endsWith("[]")) {
                String elementName = typeName.substring(0, typeName.length() - 2);
                if (_structTypes.containsKey(elementName)) function.setReturnElementStructName(elementName);
            }
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
            func.setOriginModule(_filePath);
            if (returnType == codeanalysis.SiyoUnion.class && method.getTypeClause() != null) {
                func.setReturnUnionName(method.getTypeClause().getIdentifier().getData());
            }
            if (returnType == SiyoStruct.class && method.getTypeClause() != null) {
                func.setReturnStructName(method.getTypeClause().getIdentifier().getData());
            }
            if (returnType == SiyoArray.class && method.getTypeClause() != null) {
                String typeName = method.getTypeClause().getIdentifier().getData();
                Class<?> elementType = _typeResolver.lookupElementType(typeName);
                func.setReturnElementType(elementType != null ? elementType : Object.class);
                if (typeName.endsWith("[]")) {
                    String elementName = typeName.substring(0, typeName.length() - 2);
                    if (_structTypes.containsKey(elementName)) func.setReturnElementStructName(elementName);
                }
            }
            _scope.tryDeclareFunction(func);
        }
    }

    public void registerEnumDeclaration(EnumDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_enumTypes.containsKey(name)) return;
        // A member without an explicit value continues from the previous one,
        // so `enum E { A = 10, B }` gives B the value 11.
        Map<String, Integer> members = new HashMap<>();
        List<SyntaxToken> memberTokens = syntax.getMembers();
        List<Integer> explicitValues = syntax.getExplicitValues();
        int next = 0;
        for (int i = 0; i < memberTokens.size(); i++) {
            Integer explicit = i < explicitValues.size() ? explicitValues.get(i) : null;
            int value = explicit != null ? explicit : next;
            members.put(memberTokens.get(i).getData(), value);
            next = value + 1;
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

    /**
     * Registers a sum type declaration, so its name is a usable type and its
     * variants are usable constructors in the rest of the file.
     *
     * @param syntax The declaration to register.
     */
    public void registerTypeDeclaration(TypeDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (_unionTypes.containsKey(name)) return;

        java.util.LinkedHashMap<String, codeanalysis.UnionSymbol.Variant> variants = new java.util.LinkedHashMap<>();
        for (UnionVariantSyntax variantSyntax : syntax.getVariants()) {
            String variantName = variantSyntax.getIdentifier().getData();
            List<Class<?>> payloadTypes = new ArrayList<>();
            List<String> payloadTypeNames = new ArrayList<>();
            for (SyntaxToken payloadType : variantSyntax.getPayloadTypes()) {
                String typeName = payloadType.getData();
                Class<?> resolved = typeName.equals(name) ? codeanalysis.SiyoUnion.class : _typeResolver.lookupType(typeName);
                if (resolved == null) {
                    _diagnostics.reportUndefinedType(payloadType.getSpan(), typeName);
                    resolved = Object.class;
                }
                payloadTypes.add(resolved);
                payloadTypeNames.add(typeName);
            }
            if (variants.containsKey(variantName)) {
                _diagnostics.reportDuplicateVariant(variantSyntax.getIdentifier().getSpan(), name, variantName);
                continue;
            }
            variants.put(variantName, new codeanalysis.UnionSymbol.Variant(variantName, payloadTypes, payloadTypeNames));
        }

        _unionTypes.put(name, new codeanalysis.UnionSymbol(name, variants));
    }

    /**
     * Binds a sum type declaration. The type is registered in the first pass,
     * so this only fills in a declaration the first pass did not see.
     *
     * @param syntax The declaration to bind.
     * @return The bound statement standing in for the declaration.
     */
    public BoundStatement bindTypeDeclaration(TypeDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        if (!_unionTypes.containsKey(name)) {
            registerTypeDeclaration(syntax);
        }
        return new BoundExpressionStatement(new BoundLiteralExpression(0));
    }

    /**
     * Finds the sum type that declares the named variant.
     *
     * <p>A variant name is written on its own — {@code Ok(5)}, not
     * {@code Result.Ok(5)} — so a variant is looked up across the sum types in
     * scope. The first declaring type wins, and a name declared by two types
     * has to be written qualified.</p>
     *
     * @param variantName The variant name.
     * @return The declaring type, or null when no type declares it.
     */
    public codeanalysis.UnionSymbol findUnionByVariant(String variantName) {
        for (codeanalysis.UnionSymbol union : _unionTypes.values()) {
            if (union.hasVariant(variantName)) return union;
        }
        return null;
    }
}
