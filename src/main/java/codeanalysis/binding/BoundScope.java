package codeanalysis.binding;

import codeanalysis.FunctionSymbol;
import codeanalysis.VariableSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoundScope {
    private final Map<String, VariableSymbol> _variables = new HashMap<>();
    private final Map<String, List<FunctionSymbol>> _functions = new HashMap<>();
    private final BoundScope _parent;

    public BoundScope(BoundScope parent) {
        _parent = parent;
    }

    public boolean tryDeclare(VariableSymbol variableSymbol) {
        if (_variables.containsKey(variableSymbol.getName())) {
            return false;
        }
        _variables.put(variableSymbol.getName(), variableSymbol);
        return true;
    }

    public boolean tryLookup(String name) {
        if (_variables.containsKey(name)) return true;
        if (_parent == null) return false;
        return _parent.tryLookup(name);
    }

    public VariableSymbol lookupVariable(String name) {
        VariableSymbol variable = _variables.get(name);
        if (variable != null) return variable;
        if (_parent == null) return null;
        return _parent.lookupVariable(name);
    }

    public Iterable<VariableSymbol> getDeclaredVariables() {
        return _variables.values();
    }

    public BoundScope getParent() {
        return _parent;
    }

    // --- Function overloading support ---

    public boolean hasDeclaredFunction(String name) {
        return _functions.containsKey(name);
    }

    /**
     * Declare a function. Allows overloads (same name, different param count).
     * Rejects exact duplicates (same name AND same param count).
     */
    public boolean tryDeclareFunction(FunctionSymbol functionSymbol) {
        List<FunctionSymbol> overloads = _functions.get(functionSymbol.getName());
        if (overloads != null) {
            // Check for exact duplicate (same param count)
            for (FunctionSymbol existing : overloads) {
                if (existing.getParameters().size() == functionSymbol.getParameters().size()) {
                    return false; // duplicate
                }
            }
            overloads.add(functionSymbol);
        } else {
            overloads = new ArrayList<>();
            overloads.add(functionSymbol);
            _functions.put(functionSymbol.getName(), overloads);
        }
        return true;
    }

    public boolean tryLookupFunction(String name) {
        if (_functions.containsKey(name)) return true;
        if (_parent == null) return false;
        return _parent.tryLookupFunction(name);
    }

    /**
     * Lookup function by name only (returns first overload — backward compat).
     */
    public FunctionSymbol lookupFunction(String name) {
        List<FunctionSymbol> overloads = _functions.get(name);
        if (overloads != null && !overloads.isEmpty()) return overloads.get(0);
        if (_parent == null) return null;
        return _parent.lookupFunction(name);
    }

    /**
     * Lookup function by name and argument count (overload resolution).
     */
    public FunctionSymbol lookupFunction(String name, int argCount) {
        List<FunctionSymbol> overloads = _functions.get(name);
        if (overloads != null) {
            for (FunctionSymbol func : overloads) {
                if (func.getParameters().size() == argCount) return func;
            }
        }
        if (_parent == null) return null;
        return _parent.lookupFunction(name, argCount);
    }

    public Iterable<FunctionSymbol> getDeclaredFunctions() {
        List<FunctionSymbol> all = new ArrayList<>();
        for (List<FunctionSymbol> overloads : _functions.values()) {
            all.addAll(overloads);
        }
        return all;
    }
}
