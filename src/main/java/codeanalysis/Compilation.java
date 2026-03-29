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
            globalScope = Binder.bindGlobalScope(previousScope, _syntaxTree.getRoot(), reg, _filePath);
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
        Evaluator evaluator = new Evaluator(statement, variables, functions);
        for (var entry : getGlobalScope().getStructTypes().entrySet()) {
            if (entry.getValue().isActor()) {
                evaluator.registerActorType(entry.getKey());
            }
        }
        Object value = evaluator.evaluate();
        return new EvaluationResult(new DiagnosticBox(), value);
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
        DiagnosticBox diagnostics = _syntaxTree.diagnostics().addAll(getGlobalScope().getDiagnostics());
        if (diagnostics.hasNext()) {
            return null;
        }

        BoundBlockStatement statement = getStatement();
        Map<FunctionSymbol, BoundBlockStatement> functions = getFunctions();
        codeanalysis.emitting.Emitter emitter = new codeanalysis.emitting.Emitter(statement, functions);
        return emitter.emit(className);
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
        BoundGlobalScope scope = getGlobalScope();
        while (scope != null) {
            if (scope.getFunctionBodies() != null) {
                // Lower each function body
                for (Map.Entry<FunctionSymbol, BoundBlockStatement> entry : scope.getFunctionBodies().entrySet()) {
                    if (!functions.containsKey(entry.getKey())) {
                        functions.put(entry.getKey(), Lowerer.lower(entry.getValue()));
                    }
                }
            }
            scope = scope.getPrevious();
        }
        return functions;
    }
}
