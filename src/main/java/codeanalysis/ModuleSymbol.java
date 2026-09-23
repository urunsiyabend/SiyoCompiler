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
    private final Map<String, Map<String, Integer>> _enums;
    private final BoundBlockStatement _topLevelBlock;
    private Map<String, VariableSymbol> _variables = new java.util.LinkedHashMap<>();
    private java.util.Set<String> _importedClassNames = new java.util.LinkedHashSet<>();
    private Map<FunctionSymbol, BoundBlockStatement> _inheritedMethods = new java.util.LinkedHashMap<>();
    private Map<String, UnionSymbol> _unions = new java.util.LinkedHashMap<>();
    private java.util.Set<String> _exportedNames = null;
    private Map<String, InterfaceSymbol> _interfaces = new java.util.LinkedHashMap<>();

    public ModuleSymbol(String name, String className, String filePath,
                        List<FunctionSymbol> functions,
                        Map<FunctionSymbol, BoundBlockStatement> functionBodies,
                        Map<String, StructSymbol> structs) {
        this(name, className, filePath, functions, functionBodies, structs,
                new java.util.HashMap<>(), null);
    }

    public ModuleSymbol(String name, String className, String filePath,
                        List<FunctionSymbol> functions,
                        Map<FunctionSymbol, BoundBlockStatement> functionBodies,
                        Map<String, StructSymbol> structs,
                        BoundBlockStatement topLevelBlock) {
        this(name, className, filePath, functions, functionBodies, structs,
                new java.util.HashMap<>(), topLevelBlock);
    }

    public ModuleSymbol(String name, String className, String filePath,
                        List<FunctionSymbol> functions,
                        Map<FunctionSymbol, BoundBlockStatement> functionBodies,
                        Map<String, StructSymbol> structs,
                        Map<String, Map<String, Integer>> enums,
                        BoundBlockStatement topLevelBlock) {
        _name = name;
        _className = className;
        _filePath = filePath;
        _functions = functions;
        _functionBodies = functionBodies;
        _structs = structs != null ? structs : new java.util.HashMap<>();
        _enums = enums != null ? enums : new java.util.HashMap<>();
        _topLevelBlock = topLevelBlock;
    }

    public String getName() { return _name; }
    public String getClassName() { return _className; }
    public String getFilePath() { return _filePath; }
    public List<FunctionSymbol> getFunctions() { return _functions; }
    public Map<FunctionSymbol, BoundBlockStatement> getFunctionBodies() { return _functionBodies; }
    public Map<String, StructSymbol> getStructs() { return _structs; }
    public Map<String, Map<String, Integer>> getEnums() { return _enums; }

    /** The sum types this module declares, keyed by name. */
    public Map<String, UnionSymbol> getUnions() { return _unions; }

    /** The interfaces this module declares, keyed by name. */
    public Map<String, InterfaceSymbol> getInterfaces() { return _interfaces; }

    public void setInterfaces(Map<String, InterfaceSymbol> interfaces) {
        _interfaces = interfaces != null ? interfaces : new java.util.LinkedHashMap<>();
    }

    public void setUnions(Map<String, UnionSymbol> unions) {
        _unions = unions;
    }
    public BoundBlockStatement getTopLevelBlock() { return _topLevelBlock; }

    /**
     * The names this module marks {@code pub}, or null when it marks nothing
     * and so exports everything it declares.
     */
    public java.util.Set<String> getExportedNames() { return _exportedNames; }

    public void setExportedNames(java.util.Set<String> names) {
        _exportedNames = names;
    }

    /**
     * Whether an importer may see a name this module declares.
     *
     * @param name The declared name.
     * @return true when the name is exported.
     */
    public boolean exports(String name) {
        return _exportedNames == null || _exportedNames.contains(name);
    }

    /**
     * Top-level variables this module declares, by name. These become static
     * fields on the module class and are readable from an importer as
     * {@code module.name}.
     */
    public Map<String, VariableSymbol> getVariables() { return _variables; }

    public void setVariables(Map<String, VariableSymbol> variables) {
        _variables = variables != null ? variables : new java.util.LinkedHashMap<>();
    }

    /**
     * JVM class names of the modules this one imports. The module's static
     * initializer force-loads them so their own init() runs first.
     */
    public java.util.Set<String> getImportedClassNames() { return _importedClassNames; }

    public void setImportedClassNames(java.util.Set<String> names) {
        _importedClassNames = names != null ? names : new java.util.LinkedHashSet<>();
    }

    /**
     * Impl methods this module did not declare but re-exports along with the
     * structs it re-exports.
     *
     * <p>A struct reaches an importer through any number of hops, so its methods
     * have to travel with it — otherwise a facade module could hand out a
     * {@code Server} whose methods could not be called. Each symbol keeps the
     * class that declared it as its JVM owner.
     */
    public Map<FunctionSymbol, BoundBlockStatement> getInheritedMethods() { return _inheritedMethods; }

    public void setInheritedMethods(Map<FunctionSymbol, BoundBlockStatement> methods) {
        _inheritedMethods = methods != null ? methods : new java.util.LinkedHashMap<>();
    }
}
