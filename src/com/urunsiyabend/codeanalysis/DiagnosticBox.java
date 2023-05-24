package com.urunsiyabend.codeanalysis;

import com.urunsiyabend.codeanalysis.syntax.SyntaxType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The DiagnosticBox class represents a collection of diagnostics produced during code analysis.
 * It allows iterating over the diagnostics and provides methods for adding new diagnostics.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class DiagnosticBox implements Iterator<Diagnostic> {
    private final ArrayList<Diagnostic> _diagnostics = new ArrayList<>();
    private int _position = 0;

    /**
     * Checks if there are more diagnostics to iterate over.
     *
     * @return {@code true} if there are more diagnostics, {@code false} otherwise.
     */
    @Override
    public boolean hasNext() {
        return _position < _diagnostics.size();
    }

    /**
     * Retrieves the next diagnostic in the collection.
     *
     * @return The next diagnostic.
     * @throws NoSuchElementException if there are no more diagnostics to retrieve.
     */
    @Override
    public Diagnostic next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Diagnostic diagnostic = _diagnostics.get(_position);
        _position++;
        return diagnostic;
    }

    /**
     * Adds all diagnostics from another DiagnosticBox to this DiagnosticBox.
     *
     * @param diagnostics The DiagnosticBox containing additional diagnostics to be added.
     * @return This DiagnosticBox instance.
     */
    public DiagnosticBox addAll(DiagnosticBox diagnostics) {
        _diagnostics.addAll(diagnostics._diagnostics);
        return this;
    }

    /**
     * Reports a diagnostic with the specified TextSpan and error message.
     *
     * @param span    The TextSpan representing the location of the issue in the source code.
     * @param message The error message describing the issue.
     */
    private void report(TextSpan span, String message) {
        Diagnostic diagnostic = new Diagnostic(span, message);
        _diagnostics.add(diagnostic);
    }

    /**
     * Reports an error for an invalid number with the specified TextSpan, number text, and expected type.
     *
     * @param span The TextSpan representing the location of the invalid number.
     * @param text The invalid number text.
     * @param type The expected type of the number.
     */
    public void reportInvalidNumber(TextSpan span, String text, Class<Integer> type) {
        String message = String.format("The number %s is not valid <%s>", text, type);
        report(span, message);
    }

    /**
     * Reports an error for a bad character input at the specified position.
     *
     * @param position The position of the bad character in the input.
     * @param c        The bad character.
     */
    public void reportBadCharacter(int position, char c) {
        String message = String.format("ERROR: Bad character input: <%s>", c);
        report(new TextSpan(position, 1), message);
    }

    /**
     * Reports an error for an unexpected token with the specified TextSpan, actual type, and expected type.
     *
     * @param span         The TextSpan representing the location of the unexpected token.
     * @param actualType   The actual syntax type of the token.
     * @param expectedType The expected syntax type of the token.
     */
    public void reportUnexpectedToken(TextSpan span, SyntaxType actualType, SyntaxType expectedType) {
        String message = String.format("ERROR: Unexpected token: <%s>, expected <%s>", actualType, expectedType);
        report(span, message);
    }

    /**
     * Reports an error for an undefined unary operator with the specified TextSpan, operator data, and operand type.
     *
     * @param span        The TextSpan representing the location of the undefined unary operator.
     * @param data        The data of the undefined unary operator.
     * @param operandType The type of the operand.
     */
    public void reportUndefinedUnaryOperator(TextSpan span, String data, Class<?> operandType) {
        String message = String.format("Unary operator <%s> is not defined for type <%s>", data, operandType);
        report(span, message);
    }


    /**
     * Reports an error for an undefined binary operator with the specified TextSpan, operator data, left type, and right type.
     *
     * @param span      The TextSpan representing the location of the undefined binary operator.
     * @param data      The data of the undefined binary operator.
     * @param leftType  The type of the left operand.
     * @param rightType The type of the right operand.
     */
    public void reportUndefinedBinaryOperator(TextSpan span, String data, Class<?> leftType, Class<?> rightType) {
        String message = String.format("Binary operator <%s> is not defined for types <%s> and <%s>", data, leftType, rightType);
        report(span, message);
    }
}
