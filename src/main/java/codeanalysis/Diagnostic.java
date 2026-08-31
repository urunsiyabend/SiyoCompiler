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
    private String _sourceFile;
    private codeanalysis.text.SourceText _sourceText;

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
     * The file this diagnostic belongs to, when it did not come from the file
     * being compiled — an imported module, typically.
     *
     * <p>Without it, an error inside a module was printed against the importing
     * file at a position that file does not have.
     *
     * @return The source file path, or null for the current compilation unit.
     */
    public String getSourceFile() {
        return _sourceFile;
    }

    /**
     * The text of {@link #getSourceFile()}, so a printer can turn the span into
     * a line and column of the right file.
     *
     * @return The source text, or null.
     */
    public codeanalysis.text.SourceText getSourceText() {
        return _sourceText;
    }

    public void setSource(String sourceFile, codeanalysis.text.SourceText sourceText) {
        _sourceFile = sourceFile;
        _sourceText = sourceText;
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
