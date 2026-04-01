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
     * Declare a function. Allows overloads (same name, different param count or types).
     * Rejects exact duplicates (same name AND same param count AND same param types).
     */
    public boolean tryDeclareFunction(FunctionSymbol functionSymbol) {
        List<FunctionSymbol> overloads = _functions.get(functionSymbol.getName());
        if (overloads != null) {
            // Check for exact duplicate (same param count AND same param types)
            for (FunctionSymbol existing : overloads) {
                if (existing.getParameters().size() == functionSymbol.getParameters().size()) {
                    boolean sameTypes = true;
                    for (int i = 0; i < existing.getParameters().size(); i++) {
                        if (existing.getParameters().get(i).getType() != functionSymbol.getParameters().get(i).getType()) {
                            sameTypes = false;
                            break;
                        }
                    }
                    if (sameTypes) return false; // true duplicate
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

    /**
     * Lookup function by name, arg count, and argument types (type-aware overload resolution).
     */
    public FunctionSymbol lookupFunction(String name, java.util.List<Class<?>> argTypes) {
        List<FunctionSymbol> overloads = _functions.get(name);
        if (overloads != null) {
            // First: exact match on all parameter types
            for (FunctionSymbol func : overloads) {
                if (func.getParameters().size() != argTypes.size()) continue;
                boolean match = true;
                for (int i = 0; i < argTypes.size(); i++) {
                    Class<?> paramType = func.getParameters().get(i).getType();
                    Class<?> argType = argTypes.get(i);
                    if (paramType != Object.class && argType != Object.class && paramType != argType) {
                        match = false;
                        break;
                    }
                }
                if (match) return func;
            }
            // Fallback: match by arg count only
            for (FunctionSymbol func : overloads) {
                if (func.getParameters().size() == argTypes.size()) return func;
            }
        }
        if (_parent == null) return null;
        return _parent.lookupFunction(name, argTypes);
    }

    public Iterable<FunctionSymbol> getDeclaredFunctions() {
        List<FunctionSymbol> all = new ArrayList<>();
        for (List<FunctionSymbol> overloads : _functions.values()) {
            all.addAll(overloads);
        }
        return all;
    }
}
