package codeanalysis;

import codeanalysis.binding.Binder;
import codeanalysis.binding.BoundExpression;
import codeanalysis.syntax.SyntaxTree;

import java.util.Map;


/**
 * The Compilation class represents a compilation unit in the code analysis process.
 * It encapsulates a syntax tree and provides methods for evaluating the syntax tree and obtaining the result.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Compilation {

    private final SyntaxTree _syntaxTree;

    /**
     * Constructs a Compilation object with the specified syntax tree.
     *
     * @param syntaxTree The syntax tree representing the code to be compiled.
     */
    public Compilation(SyntaxTree syntaxTree) {
        this._syntaxTree = syntaxTree;
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
     * Evaluates the syntax tree and returns the evaluation result.
     *
     * @param variables The variables to be used during the evaluation process.
     *
     * @return The evaluation result.
     * @throws Exception if an error occurs during the evaluation process.
     */
    public EvaluationResult evaluate(Map<VariableSymbol, Object> variables) throws Exception {
        Binder binder = new Binder(variables);
        BoundExpression boundExpression = binder.bindExpression(_syntaxTree.getRoot());

        DiagnosticBox diagnostics = _syntaxTree.diagnostics().addAll(binder.diagnostics());
        if (diagnostics.hasNext()) {
            return new EvaluationResult(diagnostics, null);
        }

        Evaluator evaluator = new Evaluator(boundExpression, variables);
        Object value = evaluator.evaluate();
        return new EvaluationResult(new DiagnosticBox(), value);
    }
}
