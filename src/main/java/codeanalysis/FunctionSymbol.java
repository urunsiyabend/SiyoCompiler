package codeanalysis;

import java.util.List;

/**
 * The FunctionSymbol class represents a function symbol.
 * It encapsulates the function name, parameters, and return type.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class FunctionSymbol {
    private final String _name;
    private final List<ParameterSymbol> _parameters;
    private final Class<?> _returnType;
    private final String _moduleName;
    private String _returnStructName; // if return type is SiyoStruct, which struct
    private String _returnUnionName; // if return type is SiyoUnion, which sum type
    private Class<?> _returnElementType; // if return type is SiyoArray
    private String _returnElementStructName; // if returning Struct[]
    private String _jvmMethodName; // declaring module's emitted method name, when imported
    private String _originModule;  // file that declared it; distinguishes same-named functions

    public FunctionSymbol(String name, List<ParameterSymbol> parameters, Class<?> returnType) {
        this(name, parameters, returnType, null);
    }

    public FunctionSymbol(String name, List<ParameterSymbol> parameters, Class<?> returnType, String moduleName) {
        _name = name;
        _parameters = parameters;
        _returnType = returnType;
        _moduleName = moduleName;
    }

    public String getModuleName() { return _moduleName; }
    public String getReturnStructName() { return _returnStructName; }
    public void setReturnStructName(String name) { _returnStructName = name; }
    public String getReturnUnionName() { return _returnUnionName; }
    public void setReturnUnionName(String name) { _returnUnionName = name; }
    public Class<?> getReturnElementType() { return _returnElementType; }
    public void setReturnElementType(Class<?> type) { _returnElementType = type; }
    public String getReturnElementStructName() { return _returnElementStructName; }
    public void setReturnElementStructName(String name) { _returnElementStructName = name; }
    public String getJvmMethodName() { return _jvmMethodName; }
    public void setJvmMethodName(String name) { _jvmMethodName = name; }

    /**
     * The source file that declared this function.
     *
     * <p>Two modules may each declare a function called {@code parse}. Without
     * this, the two symbols compare equal and collide in every map the compiler
     * keys by symbol, so one module's body ends up emitted under the other's
     * signature.
     *
     * @return The declaring file path, or null for the entry compilation unit.
     */
    public String getOriginModule() { return _originModule; }
    public void setOriginModule(String origin) { _originModule = origin; }

    /**
     * Gets the name of the function.
     *
     * @return The function name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Gets the list of parameters.
     *
     * @return The parameters.
     */
    public List<ParameterSymbol> getParameters() {
        return _parameters;
    }

    /**
     * Gets the return type of the function.
     *
     * @return The return type, or null for void functions.
     */
    public Class<?> getReturnType() {
        return _returnType;
    }

    /**
     * Returns a string representation of the function symbol.
     *
     * @return The function name.
     */
    @Override
    public String toString() {
        return _name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FunctionSymbol other)) return false;
        return _name.equals(other._name)
                && java.util.Objects.equals(_moduleName, other._moduleName)
                && java.util.Objects.equals(_originModule, other._originModule)
                && _parameters.size() == other._parameters.size();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(_name, _moduleName, _originModule, _parameters.size());
    }
}
