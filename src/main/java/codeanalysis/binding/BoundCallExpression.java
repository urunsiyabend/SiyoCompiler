package codeanalysis.binding;

import codeanalysis.FunctionSymbol;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents a bound call expression in the code analysis process.
 * A bound call expression represents a function call with arguments.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundCallExpression extends BoundExpression {
    private final FunctionSymbol _function;
    private final List<BoundExpression> _arguments;
    private final Class<?> _resultType;
    private final String _resultTypeName;

    /**
     * Constructs a new instance of the BoundCallExpression class.
     *
     * @param function  The function symbol being called.
     * @param arguments The list of bound argument expressions.
     */
    public BoundCallExpression(FunctionSymbol function, List<BoundExpression> arguments) {
        this(function, arguments, null);
    }

    /**
     * Constructs a call whose value has a more precise type than the function's
     * declared one.
     *
     * <p>A generic function is declared over a type parameter and compiled once
     * with it erased. The call site is where the parameter is known, so that is
     * where the result gets its real type.
     *
     * @param function   The function symbol being called.
     * @param arguments  The list of bound argument expressions.
     * @param resultType The call's type, or null to use the function's own.
     */
    public BoundCallExpression(FunctionSymbol function, List<BoundExpression> arguments, Class<?> resultType) {
        this(function, arguments, resultType, null);
    }

    /**
     * Constructs a call whose value has a more precise written type than the
     * function's declared one: {@code keep(rows, p)} is an {@code Array<Row>}
     * where {@code keep} declares {@code Array<T>}.
     *
     * @param function       The function symbol being called.
     * @param arguments      The list of bound argument expressions.
     * @param resultType     The call's type, or null to use the function's own.
     * @param resultTypeName The call's type as written with its type arguments, or null.
     */
    public BoundCallExpression(FunctionSymbol function, List<BoundExpression> arguments, Class<?> resultType,
                               String resultTypeName) {
        _function = function;
        _arguments = arguments;
        _resultType = resultType;
        _resultTypeName = resultTypeName;
    }

    /**
     * The precise type this call was given when it was bound, or null when the
     * function's own return type stands.
     *
     * @return The result type override.
     */
    public Class<?> getResultTypeOverride() {
        return _resultType;
    }

    /**
     * The call's type written out with the type arguments its generic function
     * was called at, or null when the function's declared return type name stands.
     *
     * @return The result type name.
     */
    public String getResultTypeName() {
        return _resultTypeName;
    }

    /**
     * Gets the function symbol.
     *
     * @return The function symbol.
     */
    public FunctionSymbol getFunction() {
        return _function;
    }

    /**
     * Gets the list of arguments.
     *
     * @return The list of bound argument expressions.
     */
    public List<BoundExpression> getArguments() {
        return _arguments;
    }

    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.CallExpression;
    }

    /**
     * Gets the class type of the bound expression (the function's return type).
     *
     * @return The class type of the expression.
     */
    @Override
    public Class<?> getClassType() {
        return _resultType != null ? _resultType : _function.getReturnType();
    }

    /**
     * Gets an iterator that iterates over the children of the bound node.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<BoundNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<BoundNode> {
        private int _index = 0;

        @Override
        public boolean hasNext() {
            return _index < _arguments.size();
        }

        @Override
        public BoundNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return _arguments.get(_index++);
        }
    }
}
