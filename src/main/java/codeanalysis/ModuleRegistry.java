package codeanalysis;

import java.util.*;

/**
 * Shared cache for compiled modules. Tracks compilation state for circular import detection.
 */
public class ModuleRegistry {
    // Insertion order is completion order: a module is registered once its own
    // imports are, so iterating initialises dependencies first.
    private final Map<String, ModuleSymbol> _compiledModules = new LinkedHashMap<>();
    private final Set<String> _inProgress = new HashSet<>();

    public boolean isCompiled(String modulePath) {
        return _compiledModules.containsKey(modulePath);
    }

    public ModuleSymbol getModule(String modulePath) {
        return _compiledModules.get(modulePath);
    }

    public void register(String modulePath, ModuleSymbol module) {
        _compiledModules.put(modulePath, module);
    }

    public boolean isInProgress(String modulePath) {
        return _inProgress.contains(modulePath);
    }

    public void markInProgress(String modulePath) {
        _inProgress.add(modulePath);
    }

    public void markComplete(String modulePath) {
        _inProgress.remove(modulePath);
    }

    public Collection<ModuleSymbol> getAllModules() {
        return _compiledModules.values();
    }

    // The order modules are initialised in: each after the modules its own
    // import statements bound, in the order they were bound, starting from the
    // file being run. A module compiled early, ahead of the import that brings
    // it in (see ModuleHandler.anticipateImport), takes its place only when
    // that import is bound, so compiling ahead never reorders initialisation.
    private final LinkedHashSet<String> _initOrder = new LinkedHashSet<>();
    private final Set<String> _activating = new HashSet<>();
    private boolean _activated;

    /** Places a module, after the modules it imports, in the initialisation order. */
    public void activate(String modulePath) {
        _activated = true;
        if (_initOrder.contains(modulePath) || !_activating.add(modulePath)) return;
        try {
            ModuleSymbol module = _compiledModules.get(modulePath);
            if (module == null) return;
            for (String dependency : module.getImportOrder()) activate(dependency);
            _initOrder.add(modulePath);
        } finally {
            _activating.remove(modulePath);
        }
    }

    /** The modules to initialise, dependencies before their importers, in the order they were imported. */
    public List<ModuleSymbol> getInitialisationOrder() {
        if (!_activated) return new ArrayList<>(_compiledModules.values());
        List<ModuleSymbol> modules = new ArrayList<>();
        for (String path : _initOrder) modules.add(_compiledModules.get(path));
        return modules;
    }
}
