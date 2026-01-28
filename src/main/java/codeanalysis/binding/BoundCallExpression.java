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

    /**
     * Constructs a new instance of the BoundCallExpression class.
     *
     * @param function  The function symbol being called.
     * @param arguments The list of bound argument expressions.
     */
    public BoundCallExpression(FunctionSymbol function, List<BoundExpression> arguments) {
        _function = function;
        _arguments = arguments;
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
        return _function.getReturnType();
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
