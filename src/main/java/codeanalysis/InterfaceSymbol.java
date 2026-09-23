package codeanalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An interface: a name and the methods a struct must have to be one.
 *
 * <p>A value of an interface type is a struct at run time, so the interface
 * carries no representation of its own — only the signatures a call through it
 * is checked against, and the set of structs that satisfy them.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 */
public class InterfaceSymbol {
    private final String _name;
    private final Map<String, FunctionSymbol> _methods = new LinkedHashMap<>();
    private final java.util.Set<String> _implementors = new java.util.LinkedHashSet<>();

    /**
     * Creates an interface with the given name.
     *
     * @param name The interface's name.
     */
    public InterfaceSymbol(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    /**
     * Declares a method the interface requires.
     *
     * @param method The signature, with no receiver parameter.
     */
    public void declareMethod(FunctionSymbol method) {
        _methods.put(method.getName(), method);
    }

    /**
     * The signature of one required method, or null when the interface does
     * not require a method of that name.
     *
     * @param name The method name.
     * @return The signature, or null.
     */
    public FunctionSymbol getMethod(String name) {
        return _methods.get(name);
    }

    /**
     * The methods the interface requires, in declaration order.
     *
     * @return The required signatures.
     */
    public List<FunctionSymbol> getMethods() {
        return List.copyOf(_methods.values());
    }

    /**
     * The names of the methods the interface requires.
     *
     * @return The required method names.
     */
    public java.util.Set<String> getMethodNames() {
        return _methods.keySet();
    }

    /** Records that a struct implements this interface. */
    public void addImplementor(String structName) {
        _implementors.add(structName);
    }

    /** The structs that implement this interface. */
    public java.util.Set<String> getImplementors() {
        return _implementors;
    }

    @Override
    public String toString() {
        return _name;
    }
}
