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
    private String _sourceFile;
    private codeanalysis.text.SourceText _sourceText;
    private final java.util.Set<String> _failedModules = new java.util.HashSet<>();

    /**
     * Names the file that subsequent diagnostics belong to, so an error raised
     * while binding an imported module is reported against that module.
     *
     * @param sourceFile The file path.
     * @param sourceText Its text, for resolving line and column.
     */
    public void setSource(String sourceFile, codeanalysis.text.SourceText sourceText) {
        _sourceFile = sourceFile;
        _sourceText = sourceText;
    }

    /**
     * Records that a module failed to compile. Every symbol it would have
     * exported is then missing, and reporting each one buries the real error,
     * so those follow-on diagnostics are suppressed.
     *
     * @param moduleName The module's short name, as used to qualify its symbols.
     */
    public void markModuleFailed(String moduleName) {
        if (moduleName != null) _failedModules.add(moduleName);
    }

    /** Whether a qualified name belongs to a module that already failed. */
    public boolean isFromFailedModule(String qualifiedName) {
        if (qualifiedName == null) return false;
        int dot = qualifiedName.indexOf('.');
        if (dot <= 0) return false;
        return _failedModules.contains(qualifiedName.substring(0, dot));
    }

    private void report(TextSpan span, String message) {
        Diagnostic diagnostic = new Diagnostic(span, message);
        diagnostic.setSource(_sourceFile, _sourceText);
        _diagnostics.add(diagnostic);
    }

    /**
     * Reports an error for an invalid number with the specified TextSpan, number text, and expected type.
     *
     * @param span The TextSpan representing the location of the invalid number.
     * @param text The invalid number text.
     * @param type The expected type of the number.
     */
    public void reportInvalidNumber(TextSpan span, String text, Class<?> type) {
        String message = String.format("The number %s is not a valid %s", text, siyoTypeName(type));
        report(span, message);
    }

    /**
     * Reports an error for a numeric literal whose value does not fit the widest
     * type that can hold it.
     *
     * @param span The TextSpan representing the location of the literal.
     * @param text The literal text.
     * @param type The widest type that was tried.
     */
    public void reportNumberOutOfRange(TextSpan span, String text, Class<?> type) {
        String message = String.format("The number %s does not fit in a %s", text, siyoTypeName(type));
        report(span, message);
    }

    /**
     * Maps a Java class to the Siyo type name used in diagnostics.
     *
     * @param type The Java class backing a Siyo type.
     * @return The Siyo name of the type.
     */
    private static String siyoTypeName(Class<?> type) {
        if (type == Integer.class) return "int";
        if (type == Long.class) return "long";
        if (type == Double.class) return "float";
        if (type == Boolean.class) return "bool";
        if (type == String.class) return "string";
        return type.getSimpleName();
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
        if (isFromFailedModule(name)) return;
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

    /**
     * Reports an undefined type diagnostic with the specified span and name.
     *
     * @param span The text span where the undefined type occurs.
     * @param name The undefined type name.
     */
    public void reportUndefinedType(TextSpan span, String name) {
        String message = String.format("Type '%s' does not exist", name);
        report(span, message);
    }

    /**
     * Reports a duplicate parameter diagnostic with the specified span and name.
     *
     * @param span The text span where the duplicate parameter is declared.
     * @param name The name of the duplicate parameter.
     */
    public void reportDuplicateParameter(TextSpan span, String name) {
        String message = String.format("Parameter '%s' is already declared", name);
        report(span, message);
    }

    /**
     * Reports a function already declared diagnostic with the specified span and name.
     *
     * @param span The text span where the function is already declared.
     * @param name The name of the function.
     */
    public void reportFunctionAlreadyDeclared(TextSpan span, String name) {
        String message = String.format("Function '%s' is already declared", name);
        report(span, message);
    }

    /**
     * Reports a sum type that declares the same variant twice.
     *
     * @param span        The span of the second declaration.
     * @param typeName    The sum type being declared.
     * @param variantName The variant declared twice.
     */
    public void reportDuplicateVariant(TextSpan span, String typeName, String variantName) {
        report(span, String.format("Type '%s' already declares a variant named '%s'", typeName, variantName));
    }

    /**
     * Reports a variant that the named sum type does not declare.
     *
     * @param span        The span of the use.
     * @param typeName    The sum type.
     * @param variantName The variant that does not exist.
     * @param known       The variants the type does declare.
     */
    public void reportUndefinedVariant(TextSpan span, String typeName, String variantName, java.util.List<String> known) {
        report(span, String.format("Type '%s' has no variant '%s'%n%n  help: it declares %s",
                typeName, variantName, String.join(", ", known)));
    }

    /**
     * Reports a value that does not have the declared function type.
     *
     * @param span     The span of the value.
     * @param declared The declared function type, as written.
     * @param actual   A description of the value's own shape.
     */
    public void reportFunctionTypeMismatch(TextSpan span, String declared, String actual) {
        report(span, String.format("Expected %s, but got %s", declared, actual));
    }

    /**
     * Reports a bare variant name that more than one sum type declares.
     *
     * @param span        The span of the use.
     * @param variantName The variant name.
     * @param typeNames   The types that declare it.
     */
    public void reportAmbiguousVariant(TextSpan span, String variantName, java.util.List<String> typeNames) {
        report(span, String.format("Variant '%s' is declared by %s%n%n  help: write it qualified, such as %s.%s",
                variantName, String.join(" and ", typeNames), typeNames.get(0), variantName));
    }

    /**
     * Reports a match over a sum type that does not cover every variant.
     *
     * @param span     The span of the match.
     * @param typeName The sum type being matched.
     * @param missing  The variants no arm covers.
     */
    public void reportNonExhaustiveMatch(TextSpan span, String typeName, java.util.List<String> missing) {
        report(span, String.format("This match on '%s' does not cover %s%n%n  help: add an arm for each, or a _ arm",
                typeName, String.join(", ", missing)));
    }

    /**
     * Reports a variant constructed with the wrong number of payload values.
     *
     * @param span        The span of the construction.
     * @param typeName    The sum type.
     * @param variantName The variant.
     * @param expected    The declared payload size.
     * @param actual      The number of values supplied.
     */
    public void reportWrongVariantPayloadCount(TextSpan span, String typeName, String variantName,
                                               int expected, int actual) {
        report(span, String.format("Variant '%s.%s' carries %d value%s, but %d %s given",
                typeName, variantName, expected, expected == 1 ? "" : "s",
                actual, actual == 1 ? "was" : "were"));
    }

    /**
     * Reports an undefined function diagnostic with the specified span and name.
     *
     * @param span The text span where the undefined function is called.
     * @param name The undefined function name.
     */
    public void reportUndefinedFunction(TextSpan span, String name) {
        // A module that failed to compile exports nothing. Reporting each of its
        // symbols as missing would bury the error that actually needs fixing.
        if (isFromFailedModule(name)) return;
        String message = String.format("Function '%s' does not exist", name);
        report(span, message);
    }

    public void reportAmbiguousJavaCall(TextSpan span, String name) {
        String message = String.format(
                "Java call '%s' is ambiguous for erased object arguments; add an imported Java type annotation",
                name);
        report(span, message);
    }

    public void reportError(TextSpan span, String message) {
        report(span, message);
    }

    public void reportSendOnNonActor(TextSpan span) {
        report(span, "send can only be used with actor method calls\n\n  help: send dispatches asynchronously to an actor's mailbox.\n  For regular function calls, just call the function directly.");
    }

    /**
     * A `mut` binding cannot be shared with a spawned task.
     *
     * <p>The suggested keyword is `imut`: Siyo has no `let`, and a reader who
     * followed the old wording got a parse error.
     */
    public void reportMutableCaptureInSpawn(TextSpan span, String varName) {
        String message = String.format(
            "Mutable variable '%s' cannot be captured by a spawn block\n\n" +
            "  help: consider one of these alternatives:\n" +
            "    - declare it 'imut' if it does not need to change\n" +
            "    - send the value over a channel: ch.send(%s)\n" +
            "    - keep the state in an actor and call it from the task", varName, varName);
        report(span, message);
    }

    /**
     * An `imut` binding to a mutable container cannot be shared with a spawned
     * task: the binding is immutable but its contents are not, so two threads
     * would share unsynchronised state.
     *
     * <p>Reported instead of the old "Mutable variable" wording, which
     * contradicted the source for an `imut` declaration.
     */
    public void reportSharedContainerCaptureInSpawn(TextSpan span, String varName, String typeName) {
        String message = String.format(
            "'%s' cannot be captured by a spawn block: %s contents are mutable "
            + "and would be shared between threads\n\n"
            + "  help: consider one of these alternatives:\n"
            + "    - keep the state in an actor and call it from the task\n"
            + "    - send a copy over a channel: ch.send(%s)\n"
            + "    - capture only the scalar values the task needs", varName, typeName, varName);
        report(span, message);
    }

    /**
     * A closure may read an enclosing variable but not write to it.
     *
     * <p>The write used to be discarded silently, so the program compiled, ran,
     * and produced a wrong answer.
     */
    public void reportAssignmentToCapturedVariable(TextSpan span, String varName) {
        String message = String.format(
            "Cannot assign to '%s': it is captured by a closure and captured variables are read-only\n\n"
            + "  help: consider one of these alternatives:\n"
            + "    - return the new value from the closure instead of writing it\n"
            + "    - keep the state in a struct and mutate its field\n"
            + "    - keep the state in an actor when it is shared between threads", varName);
        report(span, message);
    }

    /**
     * A struct field was called but does not hold a function.
     *
     * <p>Previously such a call fell through to Java method dispatch and failed
     * at run time naming the struct's internal representation, a type the Siyo
     * program never mentions.
     */
    public void reportFieldIsNotCallable(TextSpan span, String structName, String fieldName) {
        report(span, String.format(
                "Field '%s' of struct '%s' is not callable\n\n"
                + "  help: declare it 'fn' if it is meant to hold a function", fieldName, structName));
    }

    /**
     * The compiler failed while generating code.
     *
     * <p>Reported instead of letting an emitter or ASM exception reach the user
     * as a raw Java stack trace with no Siyo source location.
     *
     * @param span Where the failure happened, as precisely as the emitter knows.
     * @param context What was being emitted — a function name, typically.
     * @param detail The underlying failure.
     */
    public void reportInternalCompilerError(TextSpan span, String context, String detail) {
        report(span, String.format(
                "Internal compiler error while emitting %s: %s\n\n"
                + "  This is a compiler bug. A reduced program that reproduces it is the\n"
                + "  most useful thing to report.", context, detail));
    }

    /**
     * The file being compiled and one of its imports would produce the same JVM
     * class, so calls resolve to the wrong one.
     *
     * <p>Reported here rather than surfacing as a NoSuchMethodError at run time,
     * where the file's own name is the last thing a reader suspects.
     */
    public void reportModuleClassNameCollision(TextSpan span, String fileName, String moduleName) {
        report(span, String.format(
                "This file and the module '%s' it imports would both compile to the class '%s'\n\n"
                + "  help: rename this file so its name differs from the module it imports",
                moduleName, fileName));
    }

    public void reportSpawnOutsideScope(TextSpan span) {
        report(span, "spawn must be inside a scope block\n\n  help: wrap your spawn in a scope { } block");
    }

    /**
     * Reports a wrong argument count diagnostic.
     *
     * @param span     The text span where the wrong argument count occurs.
     * @param name     The function name.
     * @param expected The expected argument count.
     * @param actual   The actual argument count.
     */
    public void reportWrongArgumentCount(TextSpan span, String name, int expected, int actual) {
        String message = String.format("Function '%s' expects %d argument(s) but was given %d", name, expected, actual);
        report(span, message);
    }

    /**
     * Reports a wrong argument type diagnostic.
     *
     * @param span      The text span where the wrong argument type occurs.
     * @param paramName The parameter name.
     * @param expected  The expected type.
     * @param actual    The actual type.
     */
    public void reportWrongArgumentType(TextSpan span, String paramName, Class<?> expected, Class<?> actual) {
        String message = String.format("Parameter '%s' expects type <%s> but was given <%s>", paramName, expected, actual);
        report(span, message);
    }

    /**
     * Reports a return outside function diagnostic.
     *
     * @param span The text span where the return statement occurs.
     */
    public void reportReturnOutsideFunction(TextSpan span) {
        String message = "Return statement is not allowed outside of a function";
        report(span, message);
    }

    /**
     * Reports a return with value in void function diagnostic.
     *
     * @param span The text span where the return statement with value occurs.
     */
    public void reportReturnWithValueInVoidFunction(TextSpan span) {
        String message = "Cannot return a value from a void function";
        report(span, message);
    }

    /**
     * Reports a missing return value diagnostic.
     *
     * @param span         The text span where the return statement occurs.
     * @param expectedType The expected return type.
     */
    public void reportMissingReturnValue(TextSpan span, Class<?> expectedType) {
        String message = String.format("Function must return a value of type <%s>", expectedType);
        report(span, message);
    }

    /**
     * Reports a return type mismatch diagnostic.
     *
     * @param span     The text span where the return statement occurs.
     * @param actual   The actual return type.
     * @param expected The expected return type.
     */
    public void reportReturnTypeMismatch(TextSpan span, Class<?> actual, Class<?> expected) {
        String message = String.format("Cannot return type <%s>, expected <%s>", actual, expected);
        report(span, message);
    }

    /**
     * Reports an unterminated string literal diagnostic.
     *
     * @param span The text span where the unterminated string starts.
     */
    public void reportUnterminatedString(TextSpan span) {
        String message = "Unterminated string literal";
        report(span, message);
    }

    /**
     * Reports an invalid escape character in a string literal.
     *
     * @param span The text span where the invalid escape character occurs.
     * @param character The invalid escape character.
     */
    public void reportInvalidEscapeCharacter(TextSpan span, char character) {
        String message = String.format("Invalid escape character '\\%s'", character);
        report(span, message);
    }

    public void reportBreakOutsideLoop(TextSpan span) {
        report(span, "Break statement is not allowed outside of a loop");
    }

    public void reportContinueOutsideLoop(TextSpan span) {
        report(span, "Continue statement is not allowed outside of a loop");
    }

    public void reportCannotIndex(TextSpan span, Class<?> type) {
        String message = String.format("Type <%s> cannot be indexed", type);
        report(span, message);
    }

    public void reportCannotAccessMember(TextSpan span, Class<?> type) {
        String message = String.format("Type <%s> does not have members", type);
        report(span, message);
    }

    public void reportModuleNotFound(TextSpan span, String name) {
        report(span, String.format("Module '%s' not found", name));
    }

    public void reportCircularImport(TextSpan span, String name) {
        report(span, String.format("Circular import detected: '%s'", name));
    }

    public void reportIndexOutOfBounds(TextSpan span, int index, int length) {
        String message = String.format("Index %d is out of bounds for length %d", index, length);
        report(span, message);
    }

    public void reportTopLevelNotAllowed(TextSpan span) {
        report(span, "top-level statement not allowed; move into init() or main()");
    }
}
