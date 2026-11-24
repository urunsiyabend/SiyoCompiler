package codeanalysis;

import java.util.HashMap;
import java.util.Map;

/**
 * The StackFrame class represents a stack frame for function execution.
 * It contains the function being executed and its local variables.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class StackFrame {
    private final FunctionSymbol _function;
    private final Map<VariableSymbol, Object> _locals;

    /**
     * Creates a new stack frame for the given function.
     *
     * @param function The function being executed.
     */
    public StackFrame(FunctionSymbol function) {
        this(function, null);
    }

    private final Class<?> _closureReturnType;

    /**
     * Creates a frame for a closure, which has no function symbol but may
     * declare what it returns.
     *
     * @param function   The function being executed, or null for a closure.
     * @param returnType The closure's declared return type, or null.
     */
    public StackFrame(FunctionSymbol function, Class<?> returnType) {
        _function = function;
        _closureReturnType = returnType;
        _locals = new HashMap<>();
    }

    /** What the function or closure running in this frame declares it returns, or null. */
    public Class<?> getDeclaredReturnType() {
        return _function != null ? _function.getReturnType() : _closureReturnType;
    }

    /**
     * Gets the function associated with this stack frame.
     *
     * @return The function symbol.
     */
    public FunctionSymbol getFunction() {
        return _function;
    }

    /**
     * Gets the local variables map.
     *
     * @return The map of local variables.
     */
    public Map<VariableSymbol, Object> getLocals() {
        return _locals;
    }
}
