package codeanalysis;

import codeanalysis.syntax.SyntaxType;
import codeanalysis.text.TextSpan;

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
     * Returns the item at the current position in the collection without advancing the position.
     *
     * @return The current diagnostic.
     */
    public Diagnostic peek() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return _diagnostics.get(_position);
    }

    /**
     * Retrieves the diagnostic at the specified index.
     *
     * @param index The index of the diagnostic to retrieve.
     * @return The diagnostic at the specified index.
     */
    public Diagnostic get(int index) {
        return _diagnostics.get(index);
    }

    /**
     * Retrieves the size of the diagnostics collection.
     *
     * @return The size of the diagnostics collection.
     */
    public int size() {
        return _diagnostics.size();
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
        String message = String.format("Unary operator '%s' is not defined for type <%s>", data, operandType);
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
        String message = String.format("Binary operator '%s' is not defined for types <%s> and <%s>", data, leftType, rightType);
        report(span, message);
    }

    /**
     * Reports an undefined name diagnostic with the specified span and name.
     *
     * @param span The text span where the undefined name occurs.
     * @param name The undefined name.
     */
    public void reportUndefinedName(TextSpan span, String name) {
        String message = String.format("Name '%s' does not exist", name);
        report(span, message);
    }

    /**
     * Reports a variable already declared diagnostic with the specified span and name.
     *
     * @param span The text span where the variable is already declared.
     * @param name The name of the variable.
     */
    public void reportVariableAlreadyDeclared(TextSpan span, String name) {
        String message = String.format("Variable '%s' is already declared", name);
        report(span, message);
    }

    /**
     * @param span The text span where the name is already declared.
     * @param fromType The type of the name to be assigned.
     * @param toType The type of the name that already assigned.
     */
    public void reportCannotConvert(TextSpan span, Class<?> fromType, Class<?> toType) {
        String message = String.format("Cannot convert type <%s> to <%s>", fromType, toType);
        report(span, message);
    }

    /**
     * Reports a variable already declared diagnostic with the specified span and name.
     *
     * @param span The text span where the variable is already declared.
     * @param name The name of the variable.
     */
    public void reportCannotAssign(TextSpan span, String name) {
        String message = String.format("Name '%s' is read-only and cannot be assigned", name);
        report(span, message);
    }

    /**
     * Reports an unexpected expression type with the specified span and type.
     *
     * @param span The text span where the unexpected expression occurs.
     * @param type The unexpected syntax type.
     */
    public void reportUnexpectedExpression(TextSpan span, SyntaxType type) {
        String message = String.format("Unexpected expression syntax '%s'", type);
        report(span, message);
    }
}
