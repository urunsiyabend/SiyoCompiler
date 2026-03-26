package codeanalysis;

import java.util.*;

/**
 * Shared cache for compiled modules. Tracks compilation state for circular import detection.
 */
public class ModuleRegistry {
    private final Map<String, ModuleSymbol> _compiledModules = new HashMap<>();
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
}
