package codeanalysis;

public class ParameterSymbol extends VariableSymbol {
    public ParameterSymbol(String name, Class<?> type) {
        super(name, true, type);  // default: read-only
    }

    public ParameterSymbol(String name, boolean isMutable, Class<?> type) {
        super(name, !isMutable, type);  // mut → not read-only
    }
}
