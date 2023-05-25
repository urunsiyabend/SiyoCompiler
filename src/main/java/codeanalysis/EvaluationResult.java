package codeanalysis;

/**
 * The EvaluationResult class represents the result of a code evaluation.
 * It contains the computed value and any associated diagnostics.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class EvaluationResult {
    DiagnosticBox _diagnostics;
    private final Object _value;

    /**
     * Creates a new instance of the EvaluationResult class with the specified diagnostics and value.
     *
     * @param diagnostics The diagnostics associated with the evaluation.
     * @param value       The computed value of the evaluation.
     */
    public EvaluationResult(DiagnosticBox diagnostics, Object value) {
        _diagnostics = diagnostics;
        _value = value;
    }

    /**
     * Retrieves the diagnostics box associated with the evaluation result.
     *
     * @return The diagnostics.
     */
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Retrieves the computed value of the evaluation.
     *
     * @return The computed value.
     */
    public Object getValue() {
        return _value;
    }
}
