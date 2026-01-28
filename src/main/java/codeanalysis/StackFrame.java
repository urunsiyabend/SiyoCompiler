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
        _function = function;
        _locals = new HashMap<>();
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
