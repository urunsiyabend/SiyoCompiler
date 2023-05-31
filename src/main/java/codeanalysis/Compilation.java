package codeanalysis;

import codeanalysis.binding.Binder;
import codeanalysis.binding.BoundExpression;
import codeanalysis.binding.BoundGlobalScope;
import codeanalysis.syntax.SyntaxTree;

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
    private final AtomicReference<BoundGlobalScope> _globalScope = new AtomicReference<>(null);;

    /**
     * Constructs a Compilation object with the specified syntax tree.
     *
     * @param syntaxTree The syntax tree representing the code to be compiled.
     */
    public Compilation(SyntaxTree syntaxTree) {
        this(null, syntaxTree);
        _syntaxTree = syntaxTree;
    }

    /**
     * Constructs a Compilation object with the specified syntax tree.
     *
     * @param syntaxTree The syntax tree representing the code to be compiled.
     */
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
            globalScope = Binder.bindGlobalScope(previousScope, _syntaxTree.getRoot());
            _globalScope.compareAndSet(null, globalScope);
        }
        return globalScope;
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

        Evaluator evaluator = new Evaluator(getGlobalScope().getBoundStatement(), variables);
        Object value = evaluator.evaluate();
        return new EvaluationResult(new DiagnosticBox(), value);
    }
}
