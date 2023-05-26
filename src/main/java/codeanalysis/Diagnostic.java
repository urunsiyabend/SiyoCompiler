package codeanalysis;

import codeanalysis.text.TextSpan;

/**
 * The Diagnostic class represents a diagnostic message produced during code analysis.
 * It contains information about the location of the issue in the source code (specified by a TextSpan)
 * and the corresponding error message.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Diagnostic {
    private final TextSpan _span;
    private final String _message;

    /**
     * Constructs a Diagnostic object with the specified TextSpan and error message.
     *
     * @param span    The TextSpan representing the location of the issue in the source code.
     * @param message The error message describing the issue.
     */
    public Diagnostic(TextSpan span, String message) {
        _span = span;
        _message = message;
    }

    /**
     * Gets the TextSpan representing the location of the issue in the source code.
     *
     * @return The TextSpan.
     */
    public TextSpan getSpan() {
        return _span;
    }

    /**
     * Gets the error message describing the issue.
     *
     * @return The error message.
     */
    public String getMessage() {
        return _message;
    }

    /**
     * Returns the error message as a string representation of the Diagnostic.
     *
     * @return The error message.
     */
    @Override
    public String toString() {
        return getMessage();
    }
}
