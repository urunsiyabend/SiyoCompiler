package codeanalysis;

import codeanalysis.binding.BoundBlockStatement;

import java.util.List;
import java.util.Map;

/**
 * Holds compiled metadata for an imported module.
 */
public class ModuleSymbol {
    private final String _name;
    private final String _className;
    private final String _filePath;
    private final List<FunctionSymbol> _functions;
    private final Map<FunctionSymbol, BoundBlockStatement> _functionBodies;
    private final Map<String, StructSymbol> _structs;

    public ModuleSymbol(String name, String className, String filePath,
                        List<FunctionSymbol> functions,
                        Map<FunctionSymbol, BoundBlockStatement> functionBodies,
                        Map<String, StructSymbol> structs) {
        _name = name;
        _className = className;
        _filePath = filePath;
        _functions = functions;
        _functionBodies = functionBodies;
        _structs = structs != null ? structs : new java.util.HashMap<>();
    }

    public String getName() { return _name; }
    public String getClassName() { return _className; }
    public String getFilePath() { return _filePath; }
    public List<FunctionSymbol> getFunctions() { return _functions; }
    public Map<FunctionSymbol, BoundBlockStatement> getFunctionBodies() { return _functionBodies; }
    public Map<String, StructSymbol> getStructs() { return _structs; }
}
