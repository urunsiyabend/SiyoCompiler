package codeanalysis.binding;

import codeanalysis.FunctionSymbol;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A method call on a value reached through an interface.
 *
 * <p>Which method runs depends on the struct the value turns out to be, and
 * that is only known when the program runs. The set of candidates is not:
 * every struct that implements the interface is known at compile time, so the
 * call carries them and dispatch is a comparison against the receiver's struct
 * name rather than a reflective lookup.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundInterfaceCallExpression extends BoundExpression {
    private final BoundExpression _target;
    private final String _interfaceName;
    private final String _methodName;
    private final List<BoundExpression> _arguments;
    private final Map<String, FunctionSymbol> _implementations;
    private final Class<?> _returnType;

    /**
     * Constructs an interface method call.
     *
     * @param target          The receiver.
     * @param interfaceName   The interface it is reached through.
     * @param methodName      The method being called.
     * @param arguments       The arguments, not counting the receiver.
     * @param implementations The method of each struct that implements the
     *                        interface, keyed by struct name.
     * @param returnType      The method's declared return type, or null when void.
     */
    public BoundInterfaceCallExpression(BoundExpression target, String interfaceName, String methodName,
                                        List<BoundExpression> arguments,
                                        Map<String, FunctionSymbol> implementations,
                                        Class<?> returnType) {
        _target = target;
        _interfaceName = interfaceName;
        _methodName = methodName;
        _arguments = arguments;
        _implementations = implementations;
        _returnType = returnType;
    }

    public BoundExpression getTarget() { return _target; }

    public String getInterfaceName() { return _interfaceName; }

    public String getMethodName() { return _methodName; }

    public List<BoundExpression> getArguments() { return _arguments; }

    /** The method of each struct implementing the interface, by struct name. */
    public Map<String, FunctionSymbol> getImplementations() { return _implementations; }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.InterfaceCallExpression;
    }

    @Override
    public Class<?> getClassType() {
        return _returnType == null ? Object.class : _returnType;
    }

    /** The declared return type, or null when the method returns nothing. */
    public Class<?> getReturnType() {
        return _returnType;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        List<BoundNode> children = new ArrayList<>();
        children.add(_target);
        children.addAll(_arguments);
        return children.iterator();
    }
}
