package codeanalysis;

import codeanalysis.binding.*;
import codeanalysis.lowering.Lowerer;
import codeanalysis.syntax.SyntaxTree;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


/**
 * The Compilation class represents a compilation unit in the code analysis process.
 * It encapsulates a syntax tree and provides methods for evaluating the syntax tree and obtaining the result.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Compilation {

    private SyntaxTree _syntaxTree;
    private final Compilation _previous;
    private final AtomicReference<BoundGlobalScope> _globalScope = new AtomicReference<>(null);
    private ModuleRegistry _registry;
    private String _filePath;
    private DiagnosticBox _emitDiagnostics;

    public Compilation(SyntaxTree syntaxTree) {
        this(null, syntaxTree);
        _syntaxTree = syntaxTree;
    }

    public Compilation(SyntaxTree syntaxTree, ModuleRegistry registry, String filePath) {
        this(null, syntaxTree);
        _syntaxTree = syntaxTree;
        _registry = registry;
        _filePath = filePath;
    }

    private Compilation(Compilation previous, SyntaxTree syntaxTree) {
        _previous = previous;
        _syntaxTree = syntaxTree;
    }

    /**
     * Gets the syntax tree associated with this compilation.
     *
     * @return The syntax tree.
     */
    public SyntaxTree getSyntaxTree() {
        return _syntaxTree;
    }

    /**
     * Gets the global scope associated with this compilation.
     * If the global scope is not yet created, creates it and returns it.
     *
     * @return The global scope.
     */
    public BoundGlobalScope getGlobalScope() {
        BoundGlobalScope globalScope = _globalScope.get();
        if (globalScope == null) {
            BoundGlobalScope previousScope = _previous == null ? null : _previous.getGlobalScope();
            ModuleRegistry reg = _registry != null ? _registry : new ModuleRegistry();
            boolean enforceTopLevel = _filePath != null;
            globalScope = Binder.bindGlobalScope(previousScope, _syntaxTree.getRoot(), reg, _filePath, enforceTopLevel);
            _globalScope.compareAndSet(null, globalScope);
        }
        return globalScope;
    }

    public ModuleRegistry getRegistry() {
        return _registry != null ? _registry : new ModuleRegistry();
    }

    /**
     * Creates a new compilation with the specified syntax tree and returns it.
     * The new compilation will have this compilation as its previous compilation.
     *
     * @param syntaxTree The syntax tree representing the code to be compiled.
     * @return The new compilation.
     */
    public Compilation continueWith(SyntaxTree syntaxTree) {
        return new Compilation(this, syntaxTree);
    }

    /**
     * Evaluates the syntax tree and returns the evaluation result.
     *
     * @param variables The variables to be used during the evaluation process.
     *
     * @return The evaluation result.
     * @throws Exception if an error occurs during the evaluation process.
     */
    public EvaluationResult evaluate(Map<VariableSymbol, Object> variables) throws Exception {
        DiagnosticBox diagnostics = _syntaxTree.diagnostics().addAll(getGlobalScope().getDiagnostics());
        if (diagnostics.hasNext()) {
            return new EvaluationResult(diagnostics, null);
        }

        BoundBlockStatement statement = getStatement();
        Map<FunctionSymbol, BoundBlockStatement> functions = getFunctions();
        // The entry points are this file's own, found before module bodies join.
        FunctionSymbol init = _filePath != null ? findEntryPoint(functions, "init") : null;
        FunctionSymbol main = _filePath != null ? findEntryPoint(functions, "main") : null;
        // Every module's functions are callable, including a transitive
        // import's init() that this file never names.
        for (ModuleSymbol module : getRegistry().getAllModules()) {
            for (var entry : module.getFunctionBodies().entrySet()) {
                functions.computeIfAbsent(entry.getKey(), k -> Lowerer.lower(entry.getValue()));
            }
        }
        Map<String, VariableSymbol> moduleVariables = new HashMap<>();
        for (ModuleSymbol module : getRegistry().getAllModules()) {
            for (var entry : module.getVariables().entrySet()) {
                moduleVariables.put(module.getClassName() + "." + entry.getKey(), entry.getValue());
            }
        }
        Evaluator evaluator = new Evaluator(statement, variables, functions);
        evaluator.setModuleVariables(moduleVariables);
        for (var entry : getGlobalScope().getStructTypes().entrySet()) {
            if (entry.getValue().isActor()) {
                evaluator.registerActorType(entry.getKey());
            }
        }
        // Imported modules are initialised first, dependencies before their
        // importers — the order the bytecode backend's class initialisers run
        // in. Without this every module-level variable read as null.
        for (ModuleSymbol module : getRegistry().getAllModules()) {
            initialiseModule(module, variables, functions, moduleVariables);
        }
        Object value = evaluator.evaluate();

        // A file compiled as an entry point runs init() then main(). The
        // interpreter follows the same protocol as the bytecode backend, so
        // both agree on what a module-style program does.
        if (_filePath != null) {
            if (init != null) evaluator.invokeFunction(init, new Object[0]);
            if (main != null) {
                value = evaluator.invokeFunction(main, new Object[0]);
                if (main.getReturnType() == null) value = null;
            }
        }
        return new EvaluationResult(new DiagnosticBox(), value);
    }

    /**
     * Runs a module's top-level variable initialisers and then its init(), as
     * the module class's static initialiser does when compiled.
     */
    private static void initialiseModule(ModuleSymbol module, Map<VariableSymbol, Object> variables,
                                         Map<FunctionSymbol, BoundBlockStatement> functions,
                                         Map<String, VariableSymbol> moduleVariables) throws Exception {
        Evaluator evaluator = module.getTopLevelBlock() != null
                ? new Evaluator(Lowerer.lower(module.getTopLevelBlock()), variables, functions)
                : new Evaluator(new BoundBlockStatement(new java.util.ArrayList<>()), variables, functions);
        evaluator.setModuleVariables(moduleVariables);
        evaluator.evaluate();
        FunctionSymbol init = module.initFunction();
        if (init != null) evaluator.invokeFunction(init, new Object[0]);
    }

    /**
     * Emits an imported module as its own class.
     *
     * <p>Its static initialiser force-loads the modules it imports, so a
     * module's dependencies are initialised before it is, however it uses
     * them. Without the import list a module that only read another module's
     * variable initialised that module lazily, after its own init().
     *
     * @param module The module.
     * @return The class file bytes.
     */
    public static byte[] emitModule(ModuleSymbol module) {
        Map<FunctionSymbol, BoundBlockStatement> loweredBodies = new HashMap<>();
        for (var entry : module.getFunctionBodies().entrySet()) {
            loweredBodies.put(entry.getKey(), Lowerer.lower(entry.getValue()));
        }
        // Use module's top-level block so module-level variables become static fields
        BoundBlockStatement topLevel = module.getTopLevelBlock() != null
                ? module.getTopLevelBlock()
                : new BoundBlockStatement(new java.util.ArrayList<>());
        codeanalysis.emitting.Emitter emitter = new codeanalysis.emitting.Emitter(topLevel, loweredBodies);
        emitter.setModuleClass(true);
        emitter.setImportedModuleClasses(module.getImportedClassNames());
        return emitter.emit(module.getClassName());
    }

    /**
     * Finds a zero-argument entry point declared by this file. Functions
     * imported from a module carry their module's name and are skipped, so an
     * imported main() never runs.
     *
     * @param functions Every known function body.
     * @param name The entry point name — "init" or "main".
     * @return The function, or null when the file declares none.
     */
    private static FunctionSymbol findEntryPoint(Map<FunctionSymbol, BoundBlockStatement> functions, String name) {
        for (FunctionSymbol function : functions.keySet()) {
            if (function.getModuleName() != null) continue;
            if (function.getName().equals(name) && function.getParameters().isEmpty()) {
                return function;
            }
        }
        return null;
    }

    /**
     * Emits the tree representing the compilation unit to the specified print writer.
     *
     * @param printWriter The print writer to emit the tree to.
     * @throws IOException if an error occurs during the emitting process.
     */
    /**
     * Compiles the program to JVM bytecode.
     *
     * @param className The name of the generated class.
     * @return The class file bytes, or null if there are errors.
     */
    public byte[] compile(String className) {
        // A tree that failed to parse contains synthetic error tokens; binding it
        // would report follow-on noise at best and crash at worst.
        if (_syntaxTree.diagnostics().size() > 0) {
            return null;
        }
        DiagnosticBox diagnostics = _syntaxTree.diagnostics().addAll(getGlobalScope().getDiagnostics());
        if (diagnostics.hasNext()) {
            return null;
        }

        // A source file compiles to a class named after it. If an import would
        // produce the same class, calls silently resolve to the wrong one.
        if (getGlobalScope().getImportedClassNames().contains(className)) {
            _emitDiagnostics = new DiagnosticBox();
            _emitDiagnostics.reportModuleClassNameCollision(
                    new codeanalysis.text.TextSpan(0, 0), className, className.toLowerCase());
            return null;
        }

        BoundBlockStatement statement = getStatement();
        Map<FunctionSymbol, BoundBlockStatement> functions = getFunctions();
        codeanalysis.emitting.Emitter emitter = new codeanalysis.emitting.Emitter(statement, functions);
        emitter.setImportedModuleClasses(getGlobalScope().getImportedClassNames());
        // Pass source text for line number emission
        if (_syntaxTree.getText() != null) {
            String fileName = _filePath != null
                    ? java.nio.file.Paths.get(_filePath).getFileName().toString()
                    : className + ".siyo";
            emitter.setSourceText(_syntaxTree.getText(), fileName);
        }
        for (var entry : getGlobalScope().getStructTypes().entrySet()) {
            if (entry.getValue().isActor()) {
                emitter.registerActorType(entry.getKey());
            }
        }
        byte[] bytecode;
        try {
            bytecode = emitter.emit(className);
        } catch (Exception | StackOverflowError e) {
            _emitDiagnostics = new DiagnosticBox();
            _emitDiagnostics.reportInternalCompilerError(
                    new codeanalysis.text.TextSpan(0, 0), "class '" + className + "'",
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return null;
        }
        if (emitter.getDiagnostics().size() > 0) {
            _emitDiagnostics = emitter.getDiagnostics();
            return null;
        }
        return bytecode;
    }

    /**
     * Diagnostics raised while generating code, if any. Separate from binding
     * diagnostics because they are produced after the global scope is built.
     *
     * @return The emit diagnostics, or null when code generation succeeded.
     */
    public DiagnosticBox getEmitDiagnostics() {
        return _emitDiagnostics;
    }

    public void emitTree(PrintWriter printWriter) throws IOException {
        BoundStatement statement = getStatement();
        statement.writeTo(printWriter);
    }

    /**
     * Gets the bound statement representing the compilation unit.
     * If the bound statement is not yet created, creates it and returns it.
     * The bound statement is created by lowering the global scope.
     *
     * @return The bound statement representing the compilation unit.
     */
    private BoundBlockStatement getStatement() {
        var result = getGlobalScope().getBoundStatement();
        return Lowerer.lower(result);
    }

    /**
     * Gets the function bodies from all global scopes in the compilation chain.
     *
     * @return A map of function symbols to their bound bodies.
     */
    private Map<FunctionSymbol, BoundBlockStatement> getFunctions() {
        Map<FunctionSymbol, BoundBlockStatement> functions = new HashMap<>();
        // Use identity map so bodies that are the same object (shared module copies) get the same lowered result.
        // Without this, two FunctionSymbols pointing to the same pre-lowered body get different lowered objects,
        // which breaks the module-body deduplication check in the emitter.
        java.util.Map<BoundBlockStatement, BoundBlockStatement> loweredCache = new java.util.IdentityHashMap<>();
        BoundGlobalScope scope = getGlobalScope();
        while (scope != null) {
            if (scope.getFunctionBodies() != null) {
                for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : scope.getFunctionBodies().entrySet()) {
                    if (!functions.containsKey(entry.getKey())) {
                        BoundBlockStatement lowered = loweredCache.computeIfAbsent(entry.getValue(), Lowerer::lower);
                        functions.put(entry.getKey(), lowered);
                    }
                }
            }
            scope = scope.getPrevious();
        }
        return functions;
    }
}
