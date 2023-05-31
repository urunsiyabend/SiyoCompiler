package codeanalysis.binding;

import codeanalysis.DiagnosticBox;
import codeanalysis.VariableSymbol;
import codeanalysis.syntax.*;

import java.util.ArrayList;
import java.util.Stack;

/**
 * The `Binder` class is responsible for binding expression syntax to bound expressions.
 * It performs type checking and generates diagnostic messages for any binding errors.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Binder {
    DiagnosticBox _diagnostics = new DiagnosticBox();
    private BoundScope _scope;

    /**
     * Initializes a new instance of the Binder class with the specified variables.
     *
     * @param parent The parent scope.
     */
    public Binder(BoundScope parent) {
        _scope = new BoundScope(parent);
    }

    public static BoundGlobalScope bindGlobalScope(BoundGlobalScope previous, CompilationUnitSyntax syntax) {
        var parentScope = createParentScopes(previous);
        Binder binder = new Binder(parentScope);
        BoundStatement expression = binder.bindStatement(syntax.getStatement());
        Iterable<VariableSymbol> variables = binder._scope.getDeclaredVariables();
        DiagnosticBox diagnostics = binder._diagnostics;
        return new BoundGlobalScope(previous, diagnostics, variables, expression);
    }

    private static BoundScope createParentScopes(BoundGlobalScope previous) {
        Stack<BoundGlobalScope> scopeStack = new Stack<>();
        while (previous != null) {
            scopeStack.push(previous);
            previous = previous.getPrevious();
        }
        BoundScope parent = null;

        while (scopeStack.size() > 0) {
            previous = scopeStack.pop();
            BoundScope scope = new BoundScope(parent);
            for (VariableSymbol variable : previous.getVariableSymbols()) {
                scope.tryDeclare(variable);
            }
            parent = scope;
        }
        return parent;
    }

    /**
     * Binds the given expression syntax and returns the corresponding bound statement.
     *
     * @param syntax The statement syntax to bind.
     * @return The bound statement.
     */
    private BoundStatement bindStatement(StatementSyntax syntax) {
        return switch (syntax.getType()) {
            case BlockStatement -> bindBlockStatement((BlockStatementSyntax)syntax);
            case ExpressionStatement -> bindExpressionStatement((ExpressionStatementSyntax)syntax);
            case VariableDeclaration -> bindVariableDeclaration((VariableDeclarationSyntax)syntax);
            default -> throw new RuntimeException("Unexpected syntax type " + syntax.getType());
        };
    }

    /**
     * Binds the block statement syntax and returns the corresponding bound block statement.
     *
     * @param syntax The block statement syntax to bind.
     * @return The bound block statement.
     */
    private BoundStatement bindBlockStatement(BlockStatementSyntax syntax) {
        ArrayList<BoundStatement> statements = new ArrayList<>();
        _scope = new BoundScope(_scope);

        for (StatementSyntax statementSyntax : syntax.getStatements()) {
            BoundStatement boundStatement = bindStatement(statementSyntax);
            statements.add(boundStatement);
        }

        _scope = _scope.getParent();
        return new BoundBlockStatement(statements);
    }

    /**
     * Binds the expression statement syntax and returns the corresponding bound expression statement.
     *
     * @param syntax The expression statement syntax to bind.
     * @return The bound expression statement.
     */
    private BoundStatement bindExpressionStatement(ExpressionStatementSyntax syntax) {
        BoundExpression expression = bindExpression(syntax.getExpression());
        return new BoundExpressionStatement(expression);
    }

    /**
     * Binds the variable declaration syntax and returns the corresponding bound variable declaration.
     *
     * @param syntax The variable declaration syntax to bind.
     * @return The bound variable declaration.
     */
    private BoundStatement bindVariableDeclaration(VariableDeclarationSyntax syntax) {
        String name = syntax.getIdentifier().getData();
        var isReadOnly = syntax.getKeyword().getType() == SyntaxType.ImmutableKeyword;
        BoundExpression initializer = bindExpression(syntax.getInitializer());
        VariableSymbol variableSymbol = new VariableSymbol(name, isReadOnly, initializer.getClassType());

        if (!_scope.tryDeclare(variableSymbol)) {
            _diagnostics.reportVariableAlreadyDeclared(syntax.getIdentifier().getSpan(), name);
        }

        return new BoundVariableDeclaration(variableSymbol, initializer);
    }

    /**
     * Binds the given expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    public BoundExpression bindExpression(ExpressionSyntax syntax) {
        switch (syntax.getType()) {
            case ParenthesizedExpression -> {
                try {
                    return bindParenthesizedExpression(((ParanthesizedExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case LiteralExpression -> {
                return bindLiteralExpression((LiteralExpressionSyntax)syntax);
            }
            case NameExpression -> {
                try {
                    return bindNameExpression(((NameExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case AssignmentExpression -> {
                try {
                    return bindAssignmentExpression(((AssignmentExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case UnaryExpression -> {
                try {
                    return bindUnaryExpression((UnaryExpressionSyntax)syntax);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case BinaryExpression -> {
                try {
                    return bindBinaryExpression((BinaryExpressionSyntax)syntax);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
        return null;
    }

    /**
     * Gets an iterator over the diagnostic messages generated during binding.
     *
     * @return An iterator over the diagnostic messages.
     */
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Binds the given expression syntax to a bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindLiteralExpression(LiteralExpressionSyntax syntax) {
        Object value = syntax.getValue() != null ? syntax.getValue() : 0;
        return new BoundLiteralExpression(value);
    }


    /**
     * Binds unary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound unary expression.
     */
    private BoundExpression bindUnaryExpression(UnaryExpressionSyntax syntax) throws Exception {
        BoundExpression boundOperand = bindExpression(syntax.getOperand());
        BoundUnaryOperator boundOperator = BoundUnaryOperator.bind(syntax.getOperator().getType(), boundOperand.getClassType());
        if (boundOperator == null) {
            _diagnostics.reportUndefinedUnaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), boundOperand.getClassType());
            return boundOperand;
        }
        return new BoundUnaryExpression(boundOperator, boundOperand);
    }

    /**
     * Binds binary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound binary expression.
     */
    private BoundExpression bindBinaryExpression(BinaryExpressionSyntax syntax) throws Exception {
        BoundExpression boundLeft = bindExpression(syntax.getLeft());
        BoundExpression boundRight = bindExpression(syntax.getRight());
        BoundBinaryOperator boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), boundLeft.getClassType(), boundRight.getClassType());
        if (boundOperator == null) {
            _diagnostics.reportUndefinedBinaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), boundLeft.getClassType(), boundRight.getClassType());
            return boundLeft;
        }
        return new BoundBinaryExpression(boundLeft, boundOperator, boundRight);
    }

    /**
     * Binds a parenthesized expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindParenthesizedExpression(ParanthesizedExpressionSyntax syntax) {
        return bindExpression(syntax.getExpression());
    }

    /**
     * Binds a name expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindNameExpression(NameExpressionSyntax syntax) {
        String name = syntax.getIdentifierToken().getData();
        boolean hasVariable = _scope.tryLookup(name);

        if (!hasVariable) {
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return new BoundLiteralExpression(0);
        }
        var variable  = _scope.lookupVariable(name);
        return new BoundVariableExpression(variable);
    }

    /**
     * Binds an assignment expression syntax to a bound expression.
     *
     * @param syntax The assignment expression syntax to bind.
     * @return The bound expression.
     * @throws Exception If an error occurs during binding.
     */
    private BoundExpression bindAssignmentExpression(AssignmentExpressionSyntax syntax) throws Exception {
        String name = syntax.getIdentifierToken().getData();
        BoundExpression boundExpression = bindExpression(syntax.getExpressionSyntax());
        VariableSymbol variable;

        if (!_scope.tryLookup(name)) {
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return boundExpression;
        }
        else {
            variable = _scope.lookupVariable(name);
        }

        if (variable.isReadOnly()) {
            _diagnostics.reportCannotAssign(syntax.getEqualsToken().getSpan(), name);
            return boundExpression;
        }

        if (boundExpression.getClassType() != variable.getType()) {
            _diagnostics.reportCannotConvert(syntax.getExpressionSyntax().getSpan(), boundExpression.getClassType(), variable.getType());
            return boundExpression;
        }

        return new BoundAssignmentExpression(variable, boundExpression);
    }
}
