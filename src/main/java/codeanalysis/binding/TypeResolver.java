package codeanalysis.binding;

import codeanalysis.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles type resolution, type tracking, and Java class metadata loading.
 * Extracted from Binder to separate type-resolution concerns.
 */
public class TypeResolver {
    private final Map<VariableSymbol, VariableTypeInfo> _typeInfo = new HashMap<>();
    private final Map<String, JavaClassInfo> _javaClasses = new HashMap<>();
    private final VersionedMap<String, StructSymbol> _structTypes;
    private final VersionedMap<String, UnionSymbol> _unionTypes;
    private final VersionedMap.Version _version;
    private final VersionedMap<String, InterfaceSymbol> _interfaceTypes;
    private final Map<VariableSymbol, InterfaceSymbol> _variableInterfaces = new HashMap<>();
    private final java.util.Set<String> _typeParameters = new java.util.LinkedHashSet<>();
    // Types a file declares, known by kind before each declaration is registered,
    // so a signature may name a struct or sum type written further down.
    private final Map<String, Class<?>> _declaredAhead = new java.util.HashMap<>();

    public TypeResolver(VersionedMap<String, StructSymbol> structTypes, VersionedMap<String, UnionSymbol> unionTypes,
                        VersionedMap.Version version) {
        _structTypes = structTypes;
        _unionTypes = unionTypes;
        _version = version;
        _interfaceTypes = new VersionedMap<>(version);
    }

    /** The interfaces in scope, keyed by name. */
    public Map<String, InterfaceSymbol> getInterfaceTypes() {
        return _interfaceTypes;
    }

    /**
     * Records that a variable holds a value reached through an interface, so a
     * method call on it dispatches on the struct it turns out to be.
     *
     * @param var           The variable.
     * @param interfaceType The interface it is declared as.
     */
    public void trackInterfaceType(VariableSymbol var, InterfaceSymbol interfaceType) {
        _variableInterfaces.put(var, interfaceType);
    }

    /**
     * The value type of a map declared with type arguments, or null when the
     * map's values are untyped.
     *
     * <p>{@code Map<string, int>} indexes to an int, so {@code m["a"] + 1}
     * needs no conversion; an undeclared map still indexes to an erased value.
     *
     * @param target The map expression.
     * @return The declared value type, or null.
     */
    public Class<?> resolveMapValueType(BoundExpression target) {
        String declaredName = declaredTypeNameOf(target);
        if (!"Map".equals(genericBaseName(declaredName))) return null;
        List<String> arguments = typeArgumentsOf(declaredName);
        return arguments.size() == 2 ? lookupType(arguments.get(1)) : null;
    }

    /**
     * The type an expression was declared as, written out, or null when no
     * declaration names it.
     *
     * <p>A variable carries the name it was annotated with; indexing follows
     * that name inward, so in {@code groups: Map<string, Array<Rec>>} the
     * expression {@code groups["e"]} is an {@code Array<Rec>} and
     * {@code groups["e"][0]} a {@code Rec}. Without following it the inner
     * value was erased and {@code groups["e"][0].n} was rejected.
     *
     * @param expr The expression.
     * @return The declared type name, or null.
     */
    public String declaredTypeNameOf(BoundExpression expr) {
        if (expr instanceof BoundVariableExpression varExpr) {
            // An imported value whose structs mean something else here has no
            // usable type name: it reads erased, and a member read is rejected.
            if (!originHolds(varExpr.getVariable())) return null;
            return varExpr.getVariable().getDeclaredTypeName();
        }
        if (expr instanceof BoundIndexExpression indexExpr) {
            return containedTypeNameOf(declaredTypeNameOf(indexExpr.getTarget()));
        }
        if (expr instanceof BoundMemberAccessExpression memberExpr) {
            return fieldTypeNameOf(memberExpr.getTarget(), memberExpr.getMemberName());
        }
        if (expr instanceof BoundStructLiteralExpression structLit) {
            return structLit.getTypeName();
        }
        if (expr instanceof BoundMatchExpression match) return commonArmTypeName(match);
        // [mk(0.5), mk(1.5)] is an Array<Box<float>> when every element says so;
        // only a generic struct's name is carried this way, since its field
        // reads are checked, while 0.9.0's element classes stay as they were.
        if (expr instanceof BoundArrayLiteralExpression array && !array.getElements().isEmpty()) {
            String common = declaredTypeNameOf(array.getElements().get(0));
            if (common == null || genericBaseName(common) == null || !_structTypes.containsKey(common)) return null;
            for (BoundExpression element : array.getElements()) {
                common = joinTypeNames(common, declaredTypeNameOf(element));
                if (common == null) return null;
            }
            return "Array<" + common + ">";
        }
        // A call through a closure whose signature names a generic struct:
        // ha(x) with ha: fn(float) -> Box<Array<float>>.
        if (expr instanceof BoundClosureCallExpression closureCall
                && closureCall.getClosure() instanceof BoundVariableExpression callee) {
            // Through declaredTypeNameOf, so a signature whose names changed since is not read.
            var signature = codeanalysis.FunctionTypeSignature.parse(declaredTypeNameOf(callee));
            String returned = signature != null ? signature.getReturnTypeName() : null;
            return mentionsGenericStruct(returned) ? returned : null;
        }
        // filter keeps its elements, and so the generic struct they are.
        if (expr instanceof BoundCallExpression filtered && filtered.getFunction() == BuiltinFunctions.FILTER
                && !filtered.getArguments().isEmpty()) {
            String name = declaredTypeNameOf(filtered.getArguments().get(0));
            return mentionsGenericStruct(name) || isStructDerived(filtered.getArguments().get(0)) ? name : null;
        }
        if (expr instanceof BoundCallExpression call && call.getFunction() == BuiltinFunctions.POP
                && !call.getArguments().isEmpty()) {
            return elementTypeNameOf(declaredTypeNameOf(call.getArguments().get(0)));
        }
        if (expr instanceof BoundCallExpression generic && generic.getResultTypeName() != null) {
            return returnOriginHolds(generic.getFunction()) ? generic.getResultTypeName() : null;
        }
        if (expr instanceof BoundCallExpression call && call.getFunction().getTypeParameters().isEmpty()) {
            return returnOriginHolds(call.getFunction()) ? call.getFunction().getDeclaredReturnTypeName() : null;
        }
        return null;
    }

    /**
     * The type a field of a struct value has, written out. A generic struct's
     * field is read at the type arguments the value carries, so {@code items}
     * of a {@code Group<Row>} declared {@code items: Array<T>} is an
     * {@code Array<Row>}; with no arguments known it is erased to {@code object}.
     *
     * @param target The struct-valued expression.
     * @param field  The field's name.
     * @return The field's type name, or null when the struct is not known.
     */
    /**
     * What a type argument may be trusted as. Only a struct, a sum type or an
     * interface is: its identity is checked where a value of it is read on both
     * backends alike. A number, a bool, a string, and a container of them are
     * erased to {@code object}, as 0.9.0 left them, since an erased path may
     * have stored null or another kind there and an unboxing read would fail
     * compiled only.
     *
     * @param typeName A type name bound to a type parameter.
     * @return The name to substitute: itself, its trusted form, or object.
     */
    public String trustedTypeArgument(String typeName) {
        if (typeName == null) return "object";
        String name = canonicalTypeName(typeName);
        String base = genericBaseName(name);
        String declared = base != null ? base : name;
        if (_structTypes.containsKey(declared) || _unionTypes.containsKey(declared)
                || _interfaceTypes.containsKey(declared)) {
            return name;
        }
        if (base == null) return "object";
        List<String> arguments = typeArgumentsOf(name);
        if (("Array".equals(base) || "List".equals(base) || "Set".equals(base)) && arguments.size() == 1) {
            String element = trustedTypeArgument(arguments.get(0));
            return element.equals("object") ? "object" : base + "<" + element + ">";
        }
        if ("Map".equals(base) && arguments.size() == 2) {
            String value = trustedTypeArgument(arguments.get(1));
            return value.equals("object") ? "object" : "Map<object," + value + ">";
        }
        return "object";
    }

    /**
     * A generic call's result type written out. A type argument of a generic
     * struct in it is kept as bound, Box<float> for mk(1.5), since that struct's
     * fields are read checked; anywhere else it is trusted only as a struct, sum
     * type or interface, so wrap(1) is an Array<object> as 0.9.0 had it.
     */
    public String substituteResult(String typeName, java.util.Collection<String> typeParameters,
                                   Map<String, String> bindings) {
        String name = canonicalTypeName(typeName);
        if (typeParameters.contains(name)) return trustedTypeArgument(bindings.getOrDefault(name, "object"));
        String base = genericBaseName(name);
        if (base == null) return name;
        StructSymbol struct = _structTypes.get(base);
        boolean genericStruct = struct != null && !struct.getTypeParameters().isEmpty();
        List<String> arguments = new java.util.ArrayList<>();
        for (String argument : typeArgumentsOf(name)) {
            String canonical = canonicalTypeName(argument);
            if (genericStruct) {
                arguments.add(substitute(canonical, typeParameters, bindings));
            } else {
                arguments.add(substituteResult(canonical, typeParameters, bindings));
            }
        }
        String result = base + "<" + String.join(",", arguments) + ">";
        return genericStruct ? result : trustedOrSelf(result);
    }

    /** A container of what is not trusted is erased whole, as trustedTypeArgument does. */
    private String trustedOrSelf(String name) {
        String base = genericBaseName(name);
        if ("Array".equals(base) || "List".equals(base) || "Set".equals(base) || "Map".equals(base)) {
            return trustedTypeArgument(name);
        }
        return name;
    }

    // Calls whose bare T result was bound through a generic struct argument.
    private final java.util.Set<BoundCallExpression> _boundThroughStruct =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public void markBoundThroughStruct(BoundCallExpression call) {
        _boundThroughStruct.add(call);
    }

    /** The type name every arm of an if or match gives, when each arm is struct-derived; else null. */
    private String commonArmTypeName(BoundMatchExpression match) {
        String common = null;
        for (var arm : match.getArms()) {
            if (!isStructDerived(arm.body())) return null;
            String name = declaredTypeNameOf(arm.body());
            if (name == null || (common != null && !common.equals(name))) return null;
            common = name;
        }
        return common;
    }

    /**
     * Whether an imported variable's type name means, here, exactly what it meant
     * in the module that exported it. Every struct, sum type and interface the
     * name mentions, and every one their fields and variant payloads mention in
     * turn (cycle-safe), must not be a different declaration here: a library's
     * private P, or a public P a later import replaced, is never read as the
     * type of that name here. A name this module cannot write at all cannot be
     * mistaken, and a struct's own type parameters are not type names. Checked
     * where the value is used, against the tables as they stand there. A local
     * variable is checked the same way against this file's tables at the
     * generation it was stamped in, and holds at once while none has changed.
     */
    boolean originHolds(VariableSymbol var) {
        if (var.getOriginStructs() == null) return true;
        // Stamped in this generation: nothing it names can have changed.
        if (var.getOriginVersion() == _version.value()) return true;
        return originCheckOf(var).holds;
    }

    private OriginCheck originCheckOf(VariableSymbol var) {
        Map<String, OriginCheck> byName = _originChecked.computeIfAbsent(var.getOriginStructs(),
                k -> new HashMap<>());
        String name = var.getDeclaredTypeName() == null ? "" : var.getDeclaredTypeName();
        OriginCheck cached = byName.get(name);
        if (cached != null && cached.stillHolds()) return cached;
        // An imported value is checked against its exporter's fixed tables in every
        // generation after an import, so its check keeps the names it consulted and
        // is redone only when one of them changed. A local's stamp is checked in few
        // generations, and keeps nothing.
        java.util.Set<String> consulted = var.getOriginVersion() < 0 ? new java.util.HashSet<>() : null;
        boolean holds = checkOrigin(name, List.of(), var.getOriginStructs(), var.getOriginUnions(),
                var.getOriginInterfaces(), consulted, null);
        OriginCheck check = new OriginCheck(_version.value(), holds, consulted);
        byName.put(name, check);
        return check;
    }

    /**
     * Whether an imported function's declared return type means here what it
     * meant where the function was declared, by the same check as an imported
     * value. 0.10.0 names a call's result from that declaration (0.9.0 left an
     * imported call's {@code Array<P>} erased); when a name in it now means a
     * different declaration here, such as after an import inside the exporter's
     * own function replaced its {@code P}, the result is left erased as 0.9.0
     * left it, never read as the other type. The function's own type
     * parameters are not type names.
     */
    boolean returnOriginHolds(FunctionSymbol function) {
        OriginCheck check = returnCheckOf(function);
        return check == null || check.holds;
    }

    private OriginCheck returnCheckOf(FunctionSymbol function) {
        Map<String, StructSymbol> structs = function.getReturnOriginStructs();
        String name = function.getDeclaredReturnTypeName();
        if (structs == null || name == null) return null;
        Map<String, OriginCheck> byName = _originChecked.computeIfAbsent(structs, k -> new HashMap<>());
        String key = name + " " + function.getTypeParameters();
        OriginCheck cached = byName.get(key);
        if (cached != null && cached.stillHolds()) return cached;
        java.util.Set<String> consulted = new java.util.HashSet<>();
        boolean holds = checkOrigin(name, function.getTypeParameters(), structs, function.getReturnOriginUnions(),
                function.getReturnOriginInterfaces(), consulted, null);
        OriginCheck check = new OriginCheck(_version.value(), holds, consulted);
        byName.put(key, check);
        return check;
    }

    // Checks remembered per origin (an exporter's tables, or this file's at a
    // generation) and type name, so aliases of one value share one walk. A check
    // holds in the generation it was made in, and later only while no name it
    // consulted has changed since, whichever import, at the top of the file or
    // inside a function, changed the tables.
    private final Map<Map<String, StructSymbol>, Map<String, OriginCheck>> _originChecked = new java.util.IdentityHashMap<>();

    private final class OriginCheck {
        private long version;
        final boolean holds;
        private final java.util.Set<String> consulted;

        OriginCheck(long version, boolean holds, java.util.Set<String> consulted) {
            this.version = version;
            this.holds = holds;
            this.consulted = consulted;
        }

        boolean stillHolds() {
            if (version == _version.value()) return true;
            if (consulted == null) return false;
            for (Object changed : _version.changedSince(version)) {
                if (consulted.contains(changed)) return false;
            }
            version = _version.value();
            return true;
        }
    }

    // This file's tables as they stood at each generation a variable was stamped
    // in, shared by every variable stamped in it. Each is a view, not a copy.
    private final Map<Long, Snapshot> _snapshots = new HashMap<>();

    private record Snapshot(Map<String, StructSymbol> structs, Map<String, UnionSymbol> unions,
                            Map<String, InterfaceSymbol> interfaces) {
    }

    private Snapshot _declared;
    private java.util.Set<String> _declaredContested = java.util.Set.of();

    // For each struct name and method, the files that declared that method here,
    // whether or not the declaration won. Struct tables keep the latest
    // declaration of a name, method tables the first, and the interpreter finds
    // a method by name, so after `import "other"` then `import "lib"` the struct
    // P is lib's and P.who may be other's, as in 0.9.0.
    private final Map<String, Map<String, java.util.Set<String>>> _methodModules = new HashMap<>();

    // The declarations among those that can still change which body the
    // interpreter runs by name after the first was registered: a method this
    // file declares itself, and one an import nested in a function brings in
    // when it runs. A duplicate a top-level import brings in is registered
    // before anything runs, the same for both backends.
    private final Map<String, Map<String, java.util.Set<String>>> _lateMethodModules = new HashMap<>();

    /**
     * Records that {@code module} declared a method {@code Struct.name} here;
     * {@code late} when it is this file's own or comes from a nested import.
     */
    public void recordMethod(String qualifiedName, String module, boolean late) {
        int dot = qualifiedName.indexOf('.');
        if (dot <= 0 || module == null) return;
        String struct = qualifiedName.substring(0, dot);
        String method = qualifiedName.substring(dot + 1);
        // A new declarer can change what a name means here, so it is a change like any other.
        // In the order registered: the first is the body a call binds to.
        boolean changed = _methodModules.computeIfAbsent(struct, k -> new HashMap<>())
                .computeIfAbsent(method, k -> new java.util.LinkedHashSet<>()).add(module);
        if (late) {
            changed |= _lateMethodModules.computeIfAbsent(struct, k -> new HashMap<>())
                    .computeIfAbsent(method, k -> new java.util.HashSet<>()).add(module);
        }
        if (changed) _version.bump(struct);
    }

    // Structs an imported module found contested, as its declarations stood. Its
    // methods reach here too, by inheritance or by name in the interpreter.
    private final java.util.Set<StructSymbol> _importedContested =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // Files whose struct of a name an import inside a function will bring in.
    private final Map<String, java.util.Set<String>> _futureStructs = new HashMap<>();

    /** Records that an import nested in a function will declare a struct {@code name} from {@code module}. */
    public void recordFutureStruct(String name, String module) {
        if (module != null && _futureStructs.computeIfAbsent(name, k -> new java.util.HashSet<>()).add(module)) {
            _version.bump(name);
        }
    }

    /** Records that a module imported here found its struct {@code struct} contested. */
    public void recordImportedContested(String name, StructSymbol struct) {
        if (struct != null && _importedContested.add(struct)) _version.bump(name);
    }

    /**
     * Whether a struct of this name here may run another declaration's methods:
     * two files declared one of its methods, or two files declared a struct of
     * the name and a method of it here is not the current struct's file's. The
     * backends may then dispatch the method differently, so a name 0.10.0 infers
     * (an imported value's, an imported call's result) is not trusted through
     * it. Methods another file adds to the one struct of a name, or a second
     * struct of the name with no methods, clash with nothing. A local's names,
     * re-checked after an import, are held to what changed since, not to this.
     */
    private boolean methodsContested(String name, StructSymbol here) {
        return methodsContested(name, here, true);
    }

    /**
     * As above; without {@code duplicates}, only what contests a struct for any
     * file importing this one: a second struct of the name, or a contest an
     * import of this file's found. A method this file declares again is carried
     * to its importers as a method instead (see ModuleSymbol.getOwnMethods), which
     * contests only where it can still win by name later.
     */
    private boolean methodsContested(String name, StructSymbol here, boolean duplicates) {
        if (here != null && _importedContested.contains(here)) return true;
        Map<String, java.util.Set<String>> methods = _methodModules.get(name);
        if (methods == null || here == null) return false;
        java.util.Set<String> declarers = new java.util.HashSet<>();
        for (StructSymbol declared : _structTypes.everHeld(name)) declarers.add(declared.getOriginModule());
        declarers.addAll(_futureStructs.getOrDefault(name, java.util.Set.of()));
        // Files whose own struct of this name is another struct than this one.
        java.util.Set<String> otherStructs = new java.util.HashSet<>(declarers);
        otherStructs.remove(here.getOriginModule());
        Map<String, java.util.Set<String>> late = _lateMethodModules.getOrDefault(name, Map.of());
        for (var method : methods.entrySet()) {
            java.util.Set<String> from = method.getValue();
            // A method of another struct of the name: it may run on this one.
            for (String module : from) {
                if (otherStructs.contains(module)) return true;
            }
            if (from.size() < 2) continue;
            // One method from two files, the struct's own file not registered first.
            if (from.contains(here.getOriginModule()) && !from.iterator().next().equals(here.getOriginModule())) {
                return true;
            }
            // One method from two files, another body than the first registered
            // able to win by name later.
            if (duplicates) {
                String first = from.iterator().next();
                for (String module : late.getOrDefault(method.getKey(), java.util.Set.of())) {
                    if (!module.equals(first)) return true;
                }
            }
        }
        return false;
    }

    /** The struct names whose methods were contested as this file's declarations stood. */
    public java.util.Set<String> declaredContested() {
        return _declaredContested;
    }

    /** Records this file's tables as its top-level imports and declarations left them. */
    public void markDeclarationsComplete() {
        _declared = snapshotAt(_version.value());
        java.util.Set<String> contested = new java.util.HashSet<>();
        for (String name : _structTypes.keySet()) {
            if (methodsContested(name, _structTypes.get(name), false)) contested.add(name);
        }
        _declaredContested = contested;
    }

    /** This file's structs as its declarations left them; null before they are complete. */
    public Map<String, StructSymbol> declaredStructs() { return _declared == null ? null : _declared.structs(); }
    public Map<String, UnionSymbol> declaredUnions() { return _declared == null ? null : _declared.unions(); }
    public Map<String, InterfaceSymbol> declaredInterfaces() { return _declared == null ? null : _declared.interfaces(); }

    private Snapshot snapshotAt(long generation) {
        return _snapshots.computeIfAbsent(generation, g -> new Snapshot(
                _structTypes.asOf(g), _unionTypes.asOf(g), _interfaceTypes.asOf(g)));
    }

    /**
     * Whether dispatching a method statically on {@code struct} could run another
     * declaration's body: its methods are contested here, or its name now means
     * another struct. Only then does a value whose inferred roots no longer hold
     * lose that dispatch; an unrelated, uncontested struct keeps it.
     */
    public boolean dispatchAtRisk(StructSymbol struct) {
        StructSymbol here = _structTypes.get(struct.getName());
        return here != struct || methodsContested(struct.getName(), struct);
    }

    /** A generic call's result named by inference: holds while no struct it names is contested here. */
    private record GenericResult(String typeName) {
    }

    /**
     * What a value's type was inferred from by 0.10.0, which 0.9.0 left erased:
     * the roots its static identity rests on. A root is an imported value (its
     * origin must hold), an imported call named from its declared return type
     * (that type must still mean what it meant there), or a generic call's
     * inferred result (no struct it names may be contested here). The set is
     * the union over everything the value is made of: an alias, an index, a
     * field, a literal holding it, {@code pop}, {@code filter} or any other
     * builtin over it, an {@code if} or {@code match} of it, a loop item, a
     * payload. A variable keeps what it was given, and what is stored into it
     * later. An annotated variable starts afresh, as 0.9.0's route.
     *
     * <p>A value reaching a method call or a generic call's type argument is
     * trusted only while all its roots hold, so an identity once refused is
     * never recovered by repackaging. Field reads are left as they were.
     */
    public java.util.Set<Object> provenanceOf(BoundExpression expr) {
        java.util.Set<Object> roots = new java.util.LinkedHashSet<>();
        collectProvenance(expr, roots, null, 0);
        return roots;
    }

    /**
     * The variables whose storage a value may share: the ones it is made of by
     * the same steps as {@link #provenanceOf}, an alias, an element or field of
     * one, a literal or builtin over one. A number, string or bool shares none.
     */
    public Storage storageOf(BoundExpression expr) {
        Storage storage = new Storage();
        collectProvenance(expr, new java.util.LinkedHashSet<>(), storage, 0);
        return storage;
    }

    /**
     * The variables a value may be the same object as ({@code same}: the variable
     * itself, or an if or match of it), those whose objects it may be taken out
     * of ({@code element}: an element, a field, a builtin, generic or ordinary
     * call over one, a closure that captured one), and those whose objects it
     * holds inside ({@code held}: in a literal).
     */
    public static final class Storage {
        public final java.util.Set<VariableSymbol> same =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        public final java.util.Set<VariableSymbol> held =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        public final java.util.Set<VariableSymbol> element =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private boolean inside;
        private boolean derived;
    }

    private void collectProvenance(BoundExpression expr, java.util.Set<Object> roots,
                                   Storage storage, int depth) {
        if (expr == null || depth > 64) return;
        // A number, a string or a bool carries no struct identity to regain.
        Class<?> type = expr.getClassType();
        if (type == Integer.class || type == Long.class || type == Double.class || type == Float.class
                || type == Boolean.class || type == String.class || type == Character.class) {
            return;
        }
        int next = depth + 1;
        if (expr instanceof BoundVariableExpression v) {
            VariableSymbol variable = v.getVariable();
            // An imported value is a root, and so is a local whose generic struct
            // type 0.10.0 inferred. Another local is not: what it was inferred from
            // already is, which keeps a container of many locals' values to the
            // few roots they share.
            if (variable.getOriginStructs() != null && variable.getOriginVersion() < 0) {
                roots.add(variable);
            } else if (variable.getOriginStructs() != null && variable.isGenericTypeInferred()) {
                // Locals of one type stamped in one generation pass the same check:
                // one root for all of them, however many aliases or links there are.
                roots.add(new LocalType(variable));
            }
            roots.addAll(variable.getProvenance());
            if (storage != null) {
                (storage.inside ? storage.held : storage.derived ? storage.element : storage.same).add(variable);
            }
        } else if (expr instanceof BoundIndexExpression index) {
            collectDerived(index.getTarget(), roots, storage, next);
        } else if (expr instanceof BoundMemberAccessExpression member) {
            collectDerived(member.getTarget(), roots, storage, next);
        } else if (expr instanceof BoundLambdaExpression lambda) {
            // A closure may hand back what it captured, or store into it.
            if (storage != null) {
                for (VariableSymbol captured : lambda.getCapturedVariables()) {
                    collectDerived(new BoundVariableExpression(captured), new java.util.LinkedHashSet<>(), storage, next);
                }
            }
        } else if (expr instanceof BoundCallExpression call) {
            FunctionSymbol function = call.getFunction();
            if (call.getResultTypeName() != null) roots.add(new GenericResult(call.getResultTypeName()));
            // An imported call's result named from its declaration is 0.10.0's; a
            // struct or sum type result was typed by 0.9.0 already, and stays so.
            if (function.getReturnOriginStructs() != null && function.getDeclaredReturnTypeName() != null
                    && function.getReturnStructName() == null && function.getReturnUnionName() == null) {
                roots.add(function);
            }
            // A builtin or a generic function hands back what it was given; an
            // ordinary function's result is what its declaration says.
            if (BuiltinFunctions.isBuiltin(function) || !function.getTypeParameters().isEmpty()) {
                for (BoundExpression argument : call.getArguments()) collectDerived(argument, roots, storage, next);
            } else if (storage != null) {
                // An ordinary function's result is what its declaration says, but
                // it may be an argument, or held in one.
                for (BoundExpression argument : call.getArguments()) {
                    collectDerived(argument, new java.util.LinkedHashSet<>(), storage, next);
                }
            }
        } else if (expr instanceof BoundClosureCallExpression closureCall) {
            // A closure call's result named from its signature is 0.10.0's typing.
            String returned = declaredTypeNameOf(closureCall);
            if (returned != null) roots.add(new GenericResult(returned));
            collectDerived(closureCall.getClosure(), roots, storage, next);
            if (storage != null) {
                for (BoundExpression argument : closureCall.getArguments()) {
                    collectDerived(argument, new java.util.LinkedHashSet<>(), storage, next);
                }
            }
        } else if (expr instanceof BoundArrayLiteralExpression array) {
            literalElementRoot(array.getElements(), expr, roots);
            for (BoundExpression element : array.getElements()) collectHeld(element, roots, storage, next);
        } else if (expr instanceof BoundSetLiteralExpression set) {
            literalElementRoot(set.getElements(), expr, roots);
            for (BoundExpression element : set.getElements()) collectHeld(element, roots, storage, next);
        } else if (expr instanceof BoundMapLiteralExpression map) {
            for (BoundExpression key : map.getKeys()) collectHeld(key, roots, storage, next);
            for (BoundExpression value : map.getValues()) collectHeld(value, roots, storage, next);
        } else if (expr instanceof BoundStructLiteralExpression struct) {
            for (BoundExpression value : struct.getFieldValues().values()) collectHeld(value, roots, storage, next);
        } else if (expr instanceof BoundUnionLiteralExpression union) {
            for (BoundExpression argument : union.getArguments()) collectHeld(argument, roots, storage, next);
        } else if (expr instanceof BoundMatchExpression match) {
            collectProvenance(match.getTarget(), roots, storage, next);
            for (var arm : match.getArms()) collectProvenance(arm.body(), roots, storage, next);
        } else if (expr instanceof BoundConversionExpression conversion) {
            collectProvenance(conversion.getExpression(), roots, storage, next);
        } else if (expr instanceof BoundCastExpression cast) {
            collectProvenance(cast.getExpression(), roots, storage, next);
        } else if (expr instanceof BoundAssignmentExpression assignment) {
            collectProvenance(assignment.getExpression(), roots, storage, next);
        }
    }

    /** Walks what a value is taken out of: its variables' objects hold the value, not are it. */
    private void collectDerived(BoundExpression expr, java.util.Set<Object> roots, Storage storage, int depth) {
        if (storage == null || storage.derived || storage.inside) {
            collectProvenance(expr, roots, storage, depth);
            return;
        }
        storage.derived = true;
        try {
            collectProvenance(expr, roots, storage, depth);
        } finally {
            storage.derived = false;
        }
    }

    /** Walks a value held inside a literal: its variables are held in the result, not the result itself. */
    private void collectHeld(BoundExpression expr, java.util.Set<Object> roots, Storage storage, int depth) {
        if (storage == null || storage.inside) {
            collectProvenance(expr, roots, storage, depth);
            return;
        }
        storage.inside = true;
        try {
            collectProvenance(expr, roots, storage, depth);
        } finally {
            storage.inside = false;
        }
    }

    /**
     * A local whose struct type only 0.10.0 gave it, as a root: equal for every
     * local of the same type stamped against the same tables at the same
     * generation, since each passes exactly the check the first does.
     */
    private static final class LocalType {
        final VariableSymbol representative;

        LocalType(VariableSymbol representative) {
            this.representative = representative;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LocalType that
                    && that.representative.getOriginStructs() == representative.getOriginStructs()
                    && that.representative.getOriginVersion() == representative.getOriginVersion()
                    && java.util.Objects.equals(that.representative.getDeclaredTypeName(),
                            representative.getDeclaredTypeName());
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(System.identityHashCode(representative.getOriginStructs()),
                    representative.getOriginVersion(), representative.getDeclaredTypeName());
        }
    }

    /**
     * A literal typed from elements that are not all struct literals: [w] names
     * its elements' type, which 0.9.0 did not. That name is an inference whose
     * structs must not be contested.
     */
    private void literalElementRoot(List<BoundExpression> elements, BoundExpression literal, java.util.Set<Object> roots) {
        boolean allLiterals = true;
        for (BoundExpression element : elements) {
            if (!(element instanceof BoundStructLiteralExpression)) allLiterals = false;
        }
        if (allLiterals) return;
        String name = declaredTypeNameOf(literal);
        if (name != null) roots.add(new GenericResult(name));
    }

    /** Whether every root of what an expression was inferred from still holds here, by identity and methods. */
    public boolean provenanceHolds(BoundExpression expr) {
        java.util.Set<Object> roots = provenanceOf(expr);
        return identityHolds(roots) && contestFree(roots);
    }

    /** Whether each root's names still mean the declarations they meant; see {@link #provenanceOf}. */
    public boolean identityHolds(java.util.Set<Object> roots) {
        for (Object root : roots) {
            if (root instanceof VariableSymbol variable && !originHolds(variable)) return false;
            if (root instanceof LocalType local && !originHolds(local.representative)) return false;
            if (root instanceof FunctionSymbol function && !returnOriginHolds(function)) return false;
        }
        return true;
    }

    /**
     * Whether no struct a root's type reaches has contested methods: here, or for
     * an imported call, where it was declared. A contest decides which method body
     * runs, not what a name or a field means, so it is asked only of dispatch and
     * of inference.
     */
    public boolean contestFree(java.util.Set<Object> roots) {
        java.util.Set<String> contested = contestedNames();
        for (Object root : roots) {
            if (root instanceof LocalType local) root = local.representative;
            if (root instanceof VariableSymbol variable && variable.getOriginStructs() != null
                    && !contested.isEmpty() && reaches(variable.getDeclaredTypeName(), List.of(),
                            variable.getOriginStructs(), variable.getOriginUnions(), variable.getOriginInterfaces(),
                            contested)) {
                return false;
            }
            if (root instanceof FunctionSymbol function && function.getReturnOriginStructs() != null
                    && function.getDeclaredReturnTypeName() != null) {
                java.util.Set<String> stop = contested;
                if (!function.getReturnOriginContested().isEmpty()) {
                    stop = new java.util.HashSet<>(contested);
                    stop.addAll(function.getReturnOriginContested());
                }
                if (!stop.isEmpty() && reaches(function.getDeclaredReturnTypeName(), function.getTypeParameters(),
                        function.getReturnOriginStructs(), function.getReturnOriginUnions(),
                        function.getReturnOriginInterfaces(), stop)) {
                    return false;
                }
            }
            if (root instanceof GenericResult result && !uncontestedName(result.typeName())) return false;
        }
        return true;
    }

    // Set while a walk looks for one of these names; see reaches.
    private java.util.Set<String> _stopAt;

    /** Whether a type, through its origin's fields and payloads, reaches one of {@code names}. */
    private boolean reaches(String typeName, List<String> typeParameters, Map<String, StructSymbol> structs,
                            Map<String, UnionSymbol> unions, Map<String, InterfaceSymbol> interfaces,
                            java.util.Set<String> names) {
        if (typeName == null) return false;
        java.util.Set<String> previous = _stopAt;
        _stopAt = names;
        try {
            java.util.Set<String> walked = new java.util.HashSet<>();
            checkOrigin(typeName, typeParameters, structs, unions, interfaces, null, walked);
            for (String word : walked) {
                if (names.contains(word)) return true;
            }
            return false;
        } finally {
            _stopAt = previous;
        }
    }

    private long _contestedVersion = -1;
    private java.util.Set<String> _contestedNames = java.util.Set.of();

    /** The struct names contested here now, worked out once per generation. */
    private java.util.Set<String> contestedNames() {
        if (_contestedVersion != _version.value()) {
            java.util.Set<String> names = new java.util.HashSet<>();
            java.util.Set<String> candidates = new java.util.HashSet<>(_methodModules.keySet());
            for (StructSymbol struct : _importedContested) candidates.add(struct.getName());
            for (String name : candidates) {
                StructSymbol here = _structTypes.get(name);
                if (here != null && methodsContested(name, here)) names.add(name);
            }
            _contestedNames = names;
            _contestedVersion = _version.value();
        }
        return _contestedNames;
    }

    /**
     * Whether no struct a type name itself mentions is contested here. A struct
     * its fields reach is asked again where a method is called on that field.
     */
    public boolean uncontestedName(String typeName) {
        return uncontested(new java.util.HashSet<>(java.util.Arrays.asList(typeName.split("[^A-Za-z0-9_]+"))));
    }

    private static final java.util.Set<String> SCALAR_TYPE_NAMES =
            java.util.Set.of("int", "long", "float", "double", "bool", "string", "char");

    /**
     * Whether a value of type {@code held} could be stored somewhere inside a
     * value of type {@code holder}: in a field, an element or a payload, at any
     * depth through the structs those reach. A function given both may then
     * have stored one into the other. A struct of only numbers and strings holds
     * no other value; a slot of unknown, erased or parameter type might hold any.
     */
    public boolean canHold(String holder, String held) {
        if (holder == null || held == null) return true;
        java.util.Set<String> heldWords = new java.util.HashSet<>();
        for (String word : held.split("[^A-Za-z0-9_]+")) {
            if (!word.isEmpty() && !SCALAR_TYPE_NAMES.contains(word)) heldWords.add(word);
        }
        if (heldWords.isEmpty()) return false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>(slotsOf(canonicalTypeName(holder)));
        if (pending.isEmpty() && !knownType(holder)) return true;
        while (!pending.isEmpty()) {
            String slot = canonicalTypeName(pending.pop());
            if (!seen.add(slot)) continue;
            if (slot.equals("object") || !knownType(slot)) return true;
            for (String word : slot.split("[^A-Za-z0-9_]+")) {
                if (heldWords.contains(word)) return true;
            }
            pending.addAll(slotsOf(slot));
        }
        return false;
    }

    /** Whether every name in a type is a scalar or a type this file knows. */
    private boolean knownType(String typeName) {
        for (String word : typeName.split("[^A-Za-z0-9_]+")) {
            if (word.isEmpty() || SCALAR_TYPE_NAMES.contains(word)) continue;
            if (word.equals("Array") || word.equals("Map") || word.equals("Set") || word.equals("List")) continue;
            if (!_structTypes.containsKey(word) && !_unionTypes.containsKey(word) && !_interfaceTypes.containsKey(word)) {
                return false;
            }
        }
        return true;
    }

    /** The types a value of this type holds directly: its fields, elements or payloads. */
    private List<String> slotsOf(String typeName) {
        List<String> slots = new java.util.ArrayList<>();
        if (typeName.endsWith("[]")) {
            slots.add(typeName.substring(0, typeName.length() - 2));
            return slots;
        }
        String base = genericBaseName(typeName);
        String declared = base != null ? base : typeName;
        List<String> arguments = base != null ? typeArgumentsOf(typeName) : List.of();
        if (declared.equals("Array") || declared.equals("Set") || declared.equals("List") || declared.equals("Map")) {
            slots.addAll(arguments);
            return slots;
        }
        StructSymbol struct = _structTypes.get(declared);
        if (struct != null) {
            Map<String, String> bindings = new HashMap<>();
            for (int i = 0; i < struct.getTypeParameters().size() && i < arguments.size(); i++) {
                bindings.put(struct.getTypeParameters().get(i), arguments.get(i));
            }
            for (String field : struct.getFieldNames()) {
                String fieldType = struct.getFieldTypeName(field);
                if (fieldType == null) {
                    slots.add("object");
                    continue;
                }
                String bound = substitute(fieldType, struct.getTypeParameters(), bindings);
                // A parameter with no argument holds anything.
                slots.add(struct.getTypeParameters().contains(bound) ? "object" : bound);
            }
            return slots;
        }
        UnionSymbol union = _unionTypes.get(declared);
        if (union != null) {
            Map<String, String> bindings = new HashMap<>();
            for (int i = 0; i < union.getTypeParameters().size() && i < arguments.size(); i++) {
                bindings.put(union.getTypeParameters().get(i), arguments.get(i));
            }
            for (UnionSymbol.Variant variant : union.getVariants().values()) {
                for (String payload : variant.getPayloadTypeNames()) {
                    String bound = substitute(payload, union.getTypeParameters(), bindings);
                    slots.add(union.getTypeParameters().contains(bound) ? "object" : bound);
                }
            }
        }
        return slots;
    }

    /** Whether a written type is an array, map or set, which 0.9.0 typed a value by. */
    public static boolean namesACollection(String typeName) {
        return typeName.startsWith("Array<") || typeName.startsWith("Map<") || typeName.startsWith("Set<")
                || typeName.endsWith("[]");
    }

    /** Whether no struct of these names has contested methods here. */
    private boolean uncontested(java.util.Set<String> words) {
        for (String word : words) {
            StructSymbol struct = _structTypes.get(word);
            if (struct != null && methodsContested(word, struct)) return false;
        }
        return true;
    }

    /** The generation this file's type tables are at; see {@link #stamp}. */
    public long generation() {
        return _version.value();
    }

    /**
     * Records, on a local variable that names types, what those names meant at
     * {@code generation}, taken before the statement giving it its type was
     * bound. A later import, at the top of the file, inside a function, or
     * inside that very statement, may make a name mean something else; the
     * variable is then checked against what it meant, as an imported value is
     * checked against its exporter, and reads erased if any name changed. An
     * imported variable keeps its exporter's tables. {@code inferred} says the
     * name was not written in an annotation, so contested methods count too.
     */
    public void stamp(VariableSymbol var, long generation, boolean inferred) {
        if (var.getOriginStructs() != null || var.getDeclaredTypeName() == null) return;
        var.setInferredType(inferred);
        Snapshot snapshot = snapshotAt(generation);
        var.setOriginStructs(snapshot.structs());
        var.setOriginTypes(snapshot.unions(), snapshot.interfaces());
        var.setOriginVersion(generation);
    }

    private boolean checkOrigin(String typeName, List<String> typeParameters, Map<String, StructSymbol> structs,
                                Map<String, UnionSymbol> unions, Map<String, InterfaceSymbol> interfaces,
                                java.util.Set<String> consulted, java.util.Set<String> walked) {
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        // Each pending type name with the type parameters in scope where it was written.
        java.util.ArrayDeque<Map.Entry<String, List<String>>> pending = new java.util.ArrayDeque<>();
        pending.add(Map.entry(typeName, typeParameters));
        while (!pending.isEmpty()) {
            Map.Entry<String, List<String>> next = pending.pop();
            for (String word : next.getKey().split("[^A-Za-z0-9_]+")) {
                if (word.isEmpty() || next.getValue().contains(word)) continue;
                if (consulted != null) consulted.add(word);
                if (walked != null) {
                    walked.add(word);
                    if (_stopAt != null && _stopAt.contains(word)) return false;
                }
                StructSymbol theirStruct = structs.get(word);
                UnionSymbol theirUnion = unions.get(word);
                InterfaceSymbol theirInterface = interfaces.get(word);
                // A different declaration of this name here is a mismatch; none at all is not.
                if (_structTypes.get(word) != null && _structTypes.get(word) != theirStruct) return false;
                if (_unionTypes.get(word) != null && _unionTypes.get(word) != theirUnion) return false;
                if (_interfaceTypes.get(word) != null && _interfaceTypes.get(word) != theirInterface) return false;
                if (theirStruct != null && visited.add(theirStruct)) {
                    for (String field : theirStruct.getFieldNames()) {
                        String fieldType = theirStruct.getFieldTypeName(field);
                        if (fieldType != null) pending.add(Map.entry(fieldType, theirStruct.getTypeParameters()));
                    }
                }
                if (theirUnion != null && visited.add(theirUnion)) {
                    for (UnionSymbol.Variant variant : theirUnion.getVariants().values()) {
                        for (String payload : variant.getPayloadTypeNames()) {
                            pending.add(Map.entry(payload, theirUnion.getTypeParameters()));
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Marks a variable struct-derived from the value it took; its names are stamped where it is declared. */
    public void markStructDerived(VariableSymbol var, BoundExpression source) {
        var.setStructDerived(true);
    }

    /**
     * Whether an expression's type came from a generic struct's type argument,
     * so its whole written type name, at every depth, is what it holds: its
     * reads are checked the same way on both backends.
     *
     * <p>It starts at a generic struct's field or a call whose result was bound
     * through a generic struct argument, and every operation that moves a value
     * of a known type carries it: a member read (through a plain struct too),
     * an index, filter and pop, a variable or loop item (an element, a map key,
     * a set element) taking one, and an exported variable reached from another
     * module. Nothing else is: ordinary generic results stay erased as in 0.9.0.
     */
    public boolean isStructDerived(BoundExpression expr) {
        if (expr instanceof BoundMemberAccessExpression member) {
            StructSymbol owner = resolveStructType(member.getTarget());
            if (owner == null) return false;
            return !owner.getTypeParameters().isEmpty() || isStructDerived(member.getTarget());
        }
        if (expr instanceof BoundCallExpression call) {
            if (_boundThroughStruct.contains(call)) return true;
            boolean carries = call.getFunction() == BuiltinFunctions.FILTER || call.getFunction() == BuiltinFunctions.POP;
            return carries && !call.getArguments().isEmpty() && isStructDerived(call.getArguments().get(0));
        }
        if (expr instanceof BoundVariableExpression variable) {
            return variable.getVariable().isStructDerived() && originHolds(variable.getVariable());
        }
        // An if or match whose every arm gives the same struct-derived value type.
        if (expr instanceof BoundMatchExpression match) return commonArmTypeName(match) != null;
        if (expr instanceof BoundIndexExpression index) return isStructDerived(index.getTarget());
        return false;
    }

    /** Whether a type name mentions a struct that declares type parameters: Box<float>, Array<Box<int>>. */
    public boolean mentionsGenericStruct(String typeName) {
        if (typeName == null) return false;
        for (String word : typeName.split("[^A-Za-z0-9_]+")) {
            StructSymbol struct = word.isEmpty() ? null : _structTypes.get(word);
            if (struct != null && !struct.getTypeParameters().isEmpty()) return true;
        }
        return false;
    }

    /** Every binding in its trusted form. */
    public Map<String, String> trusted(Map<String, String> bindings) {
        Map<String, String> out = new java.util.HashMap<>();
        for (var entry : bindings.entrySet()) out.put(entry.getKey(), trustedTypeArgument(entry.getValue()));
        return out;
    }

    /**
     * A generic struct field's type argument as it is read: a number, bool or
     * string argument itself is kept, since a field read is checked for its
     * kind and for null on both backends; anything else in its trusted form.
     */
    private String fieldTypeArgument(String typeName) {
        // A generic struct's field is read checked on both backends, so its type
        // argument is kept whatever it is: Box<float>, Box<Array<float>>.
        return canonicalTypeName(typeName);
    }

    public String fieldTypeNameOf(BoundExpression target, String field) {
        StructSymbol owner = resolveStructType(target);
        if (owner == null) return null;
        String declared = owner.getFieldTypeName(field);
        List<String> typeParameters = owner.getTypeParameters();
        if (declared == null || typeParameters.isEmpty()) return declared;
        Map<String, String> bindings = new java.util.HashMap<>();
        String targetTypeName = declaredTypeNameOf(target);
        if (targetTypeName != null && owner.getName().equals(genericBaseName(targetTypeName))) {
            List<String> arguments = typeArgumentsOf(targetTypeName);
            for (int i = 0; i < arguments.size() && i < typeParameters.size(); i++) {
                bindings.put(typeParameters.get(i), fieldTypeArgument(arguments.get(i)));
            }
        }
        return substitute(declared, typeParameters, bindings);
    }

    private static final List<String> NUMBER_KINDS = List.of("int", "long", "float");

    /**
     * Two type names one element type can stand for: equal ones, or one generic
     * type whose arguments differ only by number kind, joined at the wider kind,
     * which a checked read widens to on both backends: Box<int> and Box<float>
     * are a Box<float>. Null when there is none.
     */
    static String joinTypeNames(String a, String b) {
        if (a == null || b == null) return null;
        a = canonicalTypeName(a);
        b = canonicalTypeName(b);
        if (a.equals(b)) return a;
        int i = NUMBER_KINDS.indexOf(a), j = NUMBER_KINDS.indexOf(b);
        if (i >= 0 && j >= 0) return NUMBER_KINDS.get(Math.max(i, j));
        String base = genericBaseName(a);
        if (base == null || !base.equals(genericBaseName(b))) return null;
        List<String> left = typeArgumentsOf(a), right = typeArgumentsOf(b);
        if (left.size() != right.size()) return null;
        List<String> joined = new java.util.ArrayList<>();
        for (int k = 0; k < left.size(); k++) {
            String one = joinTypeNames(left.get(k), right.get(k));
            if (one == null) return null;
            joined.add(one);
        }
        return base + "<" + String.join(",", joined) + ">";
    }

    /**
     * What indexing a container of the named type produces: an array's
     * element, a map's value. Null when the name declares neither.
     */
    private static String containedTypeNameOf(String typeName) {
        String element = elementTypeNameOf(typeName);
        if (element != null) return element;
        if ("Map".equals(genericBaseName(typeName))) {
            List<String> arguments = typeArgumentsOf(typeName);
            if (arguments.size() == 2) return arguments.get(1);
        }
        return null;
    }

    /**
     * The interface a collection's elements are declared as, or null when they
     * are not declared as one.
     *
     * <p>The elements of {@code imut shapes: Shape[]} are Shapes, whatever
     * structs were written into it. Taking the type from the first element
     * instead would dispatch every element to that one struct's method.
     *
     * @param collection The collection expression.
     * @return The element interface, or null.
     */
    public InterfaceSymbol resolveInterfaceElementType(BoundExpression collection) {
        if (!(collection instanceof BoundVariableExpression varExpr)) return null;
        // Through declaredTypeNameOf, so an imported value is read in its exporter's terms.
        String elementName = elementTypeNameOf(declaredTypeNameOf(varExpr));
        return elementName == null ? null : _interfaceTypes.get(elementName);
    }

    /**
     * The element type a container names, however it was written: {@code T[]},
     * {@code Array<T>}, {@code List<T>} or {@code Set<T>}.
     *
     * @param typeName The container type as written, or null.
     * @return The element type name, or null when the type names no element.
     */
    /** The key type a {@code Map<K, V>} is written with, or null for any other name. */
    public static String mapKeyTypeNameOf(String typeName) {
        if (!"Map".equals(genericBaseName(typeName))) return null;
        List<String> arguments = typeArgumentsOf(typeName);
        return arguments.size() == 2 ? arguments.get(0) : null;
    }

    /** The two type arguments of a written {@code Map<K, V>}, or null for any other name. */
    public static List<String> typeArgumentsOfMap(String typeName) {
        if (!"Map".equals(genericBaseName(typeName))) return null;
        List<String> arguments = typeArgumentsOf(typeName);
        return arguments.size() == 2 ? arguments : null;
    }

    /**
     * A type name in the one spelling generic code is matched in: {@code T[]}
     * is written {@code Array<T>}. A function type and an array of them are
     * left as they are, since their brackets belong to the signature.
     *
     * @param typeName The type name as written.
     * @return The same type, spelled with type arguments.
     */
    public static String canonicalTypeName(String typeName) {
        if (typeName == null) return null;
        typeName = typeName.trim();
        if (typeName.startsWith("fn")) return typeName;
        if (typeName.endsWith("[]")) {
            return "Array<" + canonicalTypeName(typeName.substring(0, typeName.length() - 2)) + ">";
        }
        String base = genericBaseName(typeName);
        if (base == null) return typeName;
        List<String> canonical = new java.util.ArrayList<>();
        for (String argument : typeArgumentsOf(typeName)) canonical.add(canonicalTypeName(argument));
        return base + "<" + String.join(",", canonical) + ">";
    }

    /**
     * Matches a type written over type parameters against the type an argument
     * actually has, recording what each parameter stands for.
     *
     * <p>{@code Array<T>} against {@code Array<Row>} binds {@code T} to
     * {@code Row}; {@code fn(T) -> string} against a lambda's
     * {@code fn(Row) -> string} does too. The first binding of a parameter
     * stands, and an erased {@code object} binds nothing, so a later argument
     * that knows more still can.
     *
     * @param parameterTypeName The type as the generic declaration wrote it.
     * @param argumentTypeName  The type of the value given for it.
     * @param typeParameters    The declaration's type parameters.
     * @param bindings          Where each parameter's type is recorded.
     */
    public static void unify(String parameterTypeName, String argumentTypeName,
                             java.util.Collection<String> typeParameters, Map<String, String> bindings) {
        if (parameterTypeName == null) return;
        String parameter = canonicalTypeName(parameterTypeName);
        // An argument of unknown or erased type, null among them, says nothing a
        // cast could rely on: every parameter it stands for is erased, whatever
        // another argument says. both(o, 2.5) with o: object stays erased.
        if (argumentTypeName == null || canonicalTypeName(argumentTypeName).equals("object")
                || canonicalTypeName(argumentTypeName).equals("any")) {
            for (String typeParameter : typeParameters) {
                if (mentionsAny(parameter, List.of(typeParameter))) bindings.put(typeParameter, "object");
            }
            return;
        }
        String argument = canonicalTypeName(argumentTypeName);
        if (typeParameters.contains(parameter)) {
            String earlier = bindings.get(parameter);
            if (earlier == null) {
                bindings.put(parameter, argument);
            } else if (!earlier.equals(argument)) {
                // Two arguments disagree, as in both(1, 2.5): the values keep
                // their own kinds, so the parameter is erased rather than read
                // at either one.
                bindings.put(parameter, "object");
            }
            return;
        }
        if (codeanalysis.FunctionTypeSignature.isSignature(parameter)) {
            var expected = codeanalysis.FunctionTypeSignature.parse(parameter);
            var actual = codeanalysis.FunctionTypeSignature.parse(argument);
            if (expected == null || actual == null
                    || expected.getParameterCount() != actual.getParameterCount()) return;
            for (int i = 0; i < expected.getParameterCount(); i++) {
                unify(expected.getParameterTypeNames().get(i), actual.getParameterTypeNames().get(i),
                        typeParameters, bindings);
            }
            unify(expected.getReturnTypeName(), actual.getReturnTypeName(), typeParameters, bindings);
            return;
        }
        String base = genericBaseName(parameter);
        if (base == null || !base.equals(genericBaseName(argument))) return;
        List<String> expectedArguments = typeArgumentsOf(parameter);
        List<String> actualArguments = typeArgumentsOf(argument);
        if (expectedArguments.size() != actualArguments.size()) return;
        for (int i = 0; i < expectedArguments.size(); i++) {
            unify(expectedArguments.get(i), actualArguments.get(i), typeParameters, bindings);
        }
    }

    /**
     * Writes a type name with its type parameters replaced by what they were
     * bound to. A parameter with no binding is erased to {@code object}.
     *
     * @param typeName       The type as the generic declaration wrote it.
     * @param typeParameters The declaration's type parameters.
     * @param bindings       What each parameter stands for.
     * @return The type with every parameter replaced.
     */
    public static String substitute(String typeName, java.util.Collection<String> typeParameters,
                                    Map<String, String> bindings) {
        if (typeName == null) return null;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < typeName.length()) {
            char c = typeName.charAt(i);
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < typeName.length() && Character.isJavaIdentifierPart(typeName.charAt(i))) i++;
                String word = typeName.substring(start, i);
                out.append(typeParameters.contains(word) ? bindings.getOrDefault(word, "object") : word);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Whether a type name mentions any of the given type parameters.
     *
     * @param typeName       The type name.
     * @param typeParameters The type parameters.
     * @return True when one of them appears in it.
     */
    public static boolean mentionsAny(String typeName, java.util.Collection<String> typeParameters) {
        if (typeName == null || typeParameters.isEmpty()) return false;
        return !substitute(typeName, typeParameters, Map.of()).equals(typeName);
    }

    public static String elementTypeNameOf(String typeName) {
        if (typeName == null) return null;
        if (typeName.endsWith("[]")) return typeName.substring(0, typeName.length() - 2);
        String base = genericBaseName(typeName);
        if (base == null) return null;
        List<String> arguments = typeArgumentsOf(typeName);
        boolean holdsOneElementType = "Array".equals(base) || "List".equals(base) || "Set".equals(base);
        return holdsOneElementType && arguments.size() == 1 ? arguments.get(0) : null;
    }

    /**
     * The interface an expression is reached through, or null when its
     * concrete struct is known or it is not a struct at all.
     *
     * @param target The expression.
     * @return The interface, or null.
     */
    public InterfaceSymbol resolveInterfaceType(BoundExpression target) {
        if (target instanceof BoundVariableExpression varExpr) {
            return _variableInterfaces.get(varExpr.getVariable());
        }
        if (target instanceof BoundCallExpression callExpr) {
            String returnName = callExpr.getFunction().getReturnStructName();
            if (returnName != null) return _interfaceTypes.get(returnName);
        }
        if (target instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol owner = resolveStructType(memberExpr.getTarget());
            if (owner != null) {
                String fieldTypeName = owner.getFieldTypeName(memberExpr.getMemberName());
                if (fieldTypeName != null) return _interfaceTypes.get(fieldTypeName);
            }
        }
        return null;
    }

    // --- Type tracking ---

    public void trackArrayType(VariableSymbol var, Class<?> elementType) {
        _typeInfo.put(var, VariableTypeInfo.forArray(elementType));
    }

    public void trackArrayType(VariableSymbol var, Class<?> elementType, StructSymbol structType) {
        _typeInfo.put(var, VariableTypeInfo.forArray(elementType, structType));
    }

    public void trackStructType(VariableSymbol var, StructSymbol structType) {
        _typeInfo.put(var, VariableTypeInfo.forStruct(structType));
    }

    public void trackUnionType(VariableSymbol var, UnionSymbol unionType) {
        _typeInfo.put(var, VariableTypeInfo.forUnion(unionType));
    }

    public UnionSymbol getVarUnionType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getUnionType() : null;
    }

    /**
     * Resolves the sum type of an expression, so a match over it can be checked
     * against the variants the type declares.
     *
     * @param target The expression to resolve.
     * @return The sum type, or null when the expression is not a known one.
     */
    public UnionSymbol resolveUnionType(BoundExpression target) {
        if (target instanceof BoundUnionLiteralExpression unionLit) {
            return unionLit.getUnionType();
        }
        if (target instanceof BoundVariableExpression varExpr) {
            UnionSymbol type = getVarUnionType(varExpr.getVariable());
            if (type != null) return type;
        }
        if (target instanceof BoundCallExpression callExpr) {
            String unionName = callExpr.getFunction().getReturnUnionName();
            if (unionName != null) return _unionTypes.get(unionName);
        }
        return null;
    }

    public void trackJavaClassType(VariableSymbol var, JavaClassInfo classInfo) {
        _typeInfo.put(var, VariableTypeInfo.forJavaClass(classInfo));
    }

    public void trackJavaResolvedType(VariableSymbol var, JavaResolvedType resolvedType) {
        _typeInfo.put(var, VariableTypeInfo.forJavaResolvedType(resolvedType));
    }

    public JavaResolvedType getVarJavaResolvedType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getJavaResolvedType() : null;
    }

    public Class<?> getArrayElementType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getArrayElementType() : null;
    }

    public StructSymbol getVarStructType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getStructType() : null;
    }

    public JavaClassInfo getVarJavaClassType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getJavaClassType() : null;
    }

    public StructSymbol getArrayStructElementType(VariableSymbol var) {
        VariableTypeInfo info = _typeInfo.get(var);
        return info != null ? info.getArrayElementStructType() : null;
    }

    // --- Type resolution ---

    /** An array's element class exactly as 0.9.0 resolved it, for 0.9.0's generic inference. */
    public Class<?> legacyArrayElementType(BoundExpression target) {
        if (target instanceof BoundArrayLiteralExpression arr) return arr.getElementType();
        if (target instanceof BoundVariableExpression varExpr) {
            Class<?> elemType = getArrayElementType(varExpr.getVariable());
            if (elemType != null) return elemType;
        }
        if (target instanceof BoundCallExpression callExpr) {
            if (callExpr.getFunction() == BuiltinFunctions.RANGE) return Integer.class;
            if (callExpr.getFunction() == BuiltinFunctions.SPLIT) return String.class;
            if (callExpr.getFunction() == BuiltinFunctions.FILTER) {
                return legacyArrayElementType(callExpr.getArguments().get(0));
            }
            Class<?> returnElementType = callExpr.getFunction().getReturnElementType();
            if (returnElementType != null) return returnElementType;
        }
        if (target instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol structType = resolveStructType(memberExpr.getTarget());
            if (structType != null) {
                // A generic struct's field is read at its type argument, checked, so
                // first(b.value) on a Box<Array<float>> knows it gives a float.
                String fieldTypeName = structType.getTypeParameters().isEmpty()
                        ? structType.getFieldTypeName(memberExpr.getMemberName())
                        : fieldTypeNameOf(memberExpr.getTarget(), memberExpr.getMemberName());
                Class<?> elemType = fieldTypeName != null ? lookupElementType(fieldTypeName) : null;
                if (elemType != null) return elemType;
            }
        }
        if (target instanceof BoundIndexExpression) {
            String elementName = elementTypeNameOf(declaredTypeNameOf(target));
            Class<?> elemType = elementName != null ? lookupType(elementName) : null;
            if (elemType != null) return elemType;
        }
        return Object.class;
    }

    public Class<?> resolveArrayElementType(BoundExpression target) {
        // A struct-derived array holds what its whole known type says, at any depth.
        if (isStructDerived(target)) {
            Class<?> elemType = lookupElementType(declaredTypeNameOf(target));
            if (elemType != null) return elemType;
        }
        if (target instanceof BoundArrayLiteralExpression arr) {
            return arr.getElementType();
        }
        if (target instanceof BoundVariableExpression varExpr) {
            Class<?> elemType = getArrayElementType(varExpr.getVariable());
            if (elemType != null) return elemType;
        }
        if (target instanceof BoundCallExpression callExpr) {
            if (callExpr.getFunction() == BuiltinFunctions.RANGE) return Integer.class;
            if (callExpr.getFunction() == BuiltinFunctions.SPLIT) return String.class;
            // filter keeps the element type it was given; map's elements are
            // whatever the function returned, which is erased.
            if (callExpr.getFunction() == BuiltinFunctions.FILTER) {
                return resolveArrayElementType(callExpr.getArguments().get(0));
            }
            Class<?> returnElementType = callExpr.getFunction().getReturnElementType();
            if (returnElementType != null) return returnElementType;
        }
        if (target instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol structType = resolveStructType(memberExpr.getTarget());
            if (structType != null) {
                String fieldTypeName = fieldTypeNameOf(memberExpr.getTarget(), memberExpr.getMemberName());
                if (fieldTypeName != null) {
                    Class<?> elemType = lookupElementType(fieldTypeName);
                    if (elemType != null) return elemType;
                }
            }
        }
        if (target instanceof BoundIndexExpression) {
            // groups["e"] where groups: Map<string, Array<Rec>>
            String elementName = elementTypeNameOf(declaredTypeNameOf(target));
            Class<?> elemType = elementName != null ? lookupType(elementName) : null;
            if (elemType != null) return elemType;
        }
        // An element known only by name, from a generic result, a payload or a
        // field taken into a variable, stays erased as 0.9.0 left it: its name
        // still says which struct it is, and a member read casts there.
        return Object.class;
    }

    public StructSymbol resolveStructType(BoundExpression target) {
        if (target instanceof BoundVariableExpression varExpr) {
            StructSymbol type = getVarStructType(varExpr.getVariable());
            if (type != null) return type;
        }
        if (target instanceof BoundStructLiteralExpression structLit) {
            return structLit.getStructType();
        }
        // Member access on struct: report.ipCounter → resolve nested struct type from field type name
        if (target instanceof BoundMemberAccessExpression memberExpr && memberExpr.getClassType() == SiyoStruct.class) {
            StructSymbol ownerStruct = resolveStructType(memberExpr.getTarget());
            if (ownerStruct != null) {
                String fieldTypeName = fieldTypeNameOf(memberExpr.getTarget(), memberExpr.getMemberName());
                if (fieldTypeName != null) {
                    StructSymbol fieldStruct = _structTypes.get(fieldTypeName);
                    if (fieldStruct != null) return fieldStruct;
                }
            }
        }
        // Index expression on struct array: todos[i] → resolve element struct type
        if (target instanceof BoundIndexExpression indexExpr && indexExpr.getClassType() == SiyoStruct.class) {
            String declaredName = declaredTypeNameOf(indexExpr);
            StructSymbol declared = declaredName != null ? _structTypes.get(erasedTypeName(declaredName)) : null;
            if (declared != null) return declared;
            return resolveStructTypeFromCollection(indexExpr.getTarget());
        }
        // Call expression returning struct
        if (target instanceof BoundCallExpression popped && popped.getFunction() == BuiltinFunctions.POP
                && popped.getClassType() == SiyoStruct.class && !popped.getArguments().isEmpty()) {
            return resolveStructTypeFromCollection(popped.getArguments().get(0));
        }
        if (target instanceof BoundCallExpression generic && generic.getClassType() == SiyoStruct.class
                && generic.getResultTypeName() != null && returnOriginHolds(generic.getFunction())) {
            StructSymbol st = _structTypes.get(erasedTypeName(generic.getResultTypeName()));
            if (st != null) return st;
        }
        if (target instanceof BoundCallExpression callExpr && callExpr.getClassType() == SiyoStruct.class) {
            // Check return struct name on FunctionSymbol first
            String returnStructName = callExpr.getFunction().getReturnStructName();
            if (returnStructName != null) {
                StructSymbol st = _structTypes.get(returnStructName);
                if (st != null) return st;
            }
            // Fallback: qualified name
            String funcName = callExpr.getFunction().getName();
            if (funcName.contains(".")) {
                String structName = funcName.substring(0, funcName.indexOf('.'));
                StructSymbol st = _structTypes.get(structName);
                if (st != null) return st;
            }
        }
        // A value whose declared type names a struct, erased or not: an element
        // of keep(rows, p), Some(r) on an Option<Row>, imut r = box.value.
        if (target.getClassType() == SiyoStruct.class || target.getClassType() == Object.class) {
            String declared = declaredTypeNameOf(target);
            if (declared != null) return _structTypes.get(declared);
        }
        return null;
    }

    public StructSymbol resolveStructTypeFromCollection(BoundExpression collection) {
        if (collection instanceof BoundArrayLiteralExpression arr) {
            if (!arr.getElements().isEmpty() && arr.getElements().get(0) instanceof BoundStructLiteralExpression structLit) {
                return structLit.getStructType();
            }
        }
        if (collection instanceof BoundVariableExpression varExpr) {
            StructSymbol structType = getArrayStructElementType(varExpr.getVariable());
            if (structType != null) return structType;
        }
        if (collection instanceof BoundCallExpression generic && generic.getResultTypeName() != null
                && returnOriginHolds(generic.getFunction())) {
            String elementName = elementTypeNameOf(generic.getResultTypeName());
            StructSymbol elemStruct = elementName != null ? _structTypes.get(erasedTypeName(elementName)) : null;
            if (elemStruct != null) return elemStruct;
        }
        if (collection instanceof BoundCallExpression callExpr) {
            String structName = callExpr.getFunction().getReturnElementStructName();
            if (structName != null) {
                StructSymbol structType = _structTypes.get(structName);
                if (structType != null) return structType;
            }
        }
        // An indexed container: groups["e"] where groups: Map<string, Array<Rec>>,
        // or a variable that took a generic struct's field.
        if (collection instanceof BoundIndexExpression || collection instanceof BoundVariableExpression) {
            String elementName = elementTypeNameOf(declaredTypeNameOf(collection));
            if (elementName != null) {
                StructSymbol elemStruct = _structTypes.get(elementName);
                if (elemStruct != null) return elemStruct;
            }
        }
        // Struct field array: self.todos where todos: Todo[]
        if (collection instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol ownerStruct = resolveStructType(memberExpr.getTarget());
            if (ownerStruct != null) {
                String fieldTypeName = fieldTypeNameOf(memberExpr.getTarget(), memberExpr.getMemberName());
                String elemName = elementTypeNameOf(fieldTypeName);
                if (elemName != null) {
                    StructSymbol elemStruct = _structTypes.get(elemName);
                    if (elemStruct != null) return elemStruct;
                }
            }
        }
        return null;
    }

    public JavaClassInfo resolveJavaClassInfo(BoundExpression expr) {
        // Try resolved type first (has generic info)
        JavaResolvedType resolved = resolveJavaResolvedType(expr);
        if (resolved != null) return resolved.getClassInfo();
        // Fallback
        return resolveJavaClassForSiyoType(expr.getClassType());
    }

    /**
     * Resolve full parameterized type with generic bindings from an expression.
     */
    public JavaResolvedType resolveJavaResolvedType(BoundExpression expr) {
        if (expr instanceof BoundVariableExpression varExpr) {
            JavaResolvedType rt = getVarJavaResolvedType(varExpr.getVariable());
            if (rt != null) return rt;
            JavaClassInfo info = getVarJavaClassType(varExpr.getVariable());
            if (info != null) return new JavaResolvedType(info);
            // Auto-resolve for Siyo types
            JavaClassInfo siyoInfo = resolveJavaClassForSiyoType(varExpr.getVariable().getType());
            if (siyoInfo != null) return new JavaResolvedType(siyoInfo);
        }
        if (expr instanceof BoundCastExpression castExpr) {
            return new JavaResolvedType(castExpr.getTargetClassInfo());
        }
        if (expr instanceof BoundJavaStaticFieldExpression fieldExpr) {
            // Static field: resolve the field's type as Java class
            String desc = fieldExpr.getFieldDescriptor();
            JavaClassInfo info = resolveJavaClassFromDescriptor(desc);
            if (info != null) return new JavaResolvedType(info);
        }
        if (expr instanceof BoundJavaMethodCallExpression javaCall) {
            if (javaCall.getResolvedReturnType() != null) return javaCall.getResolvedReturnType();
            if (javaCall.isConstructor() && javaCall.getClassInfo() != null) {
                return new JavaResolvedType(javaCall.getClassInfo());
            }
            if (javaCall.getResolvedSignature() != null) {
                JavaClassInfo info = resolveJavaClassFromDescriptor(javaCall.getResolvedSignature().getReturnDescriptor());
                if (info != null) return new JavaResolvedType(info);
            }
        }
        return null;
    }

    /**
     * Resolve the return type of a method call, applying generic type substitution.
     */
    public JavaResolvedType resolveMethodReturnType(JavaMethodSignature sig, JavaResolvedType ownerType) {
        if (sig == null) return null;

        // Try generic signature first
        if (sig.getGenericSignature() != null && ownerType != null) {
            java.util.Map<String, JavaResolvedType> bindings = ownerType.getTypeArgs();
            JavaGenericSignature.ResolvedReturnType resolved =
                    JavaGenericSignature.resolveReturnType(sig.getGenericSignature(), bindings);
            if (resolved != null) {
                JavaClassMetadata meta = JavaClassMetadata.load(resolved.className);
                if (meta != null) {
                    String simpleName = resolved.className.contains(".")
                            ? resolved.className.substring(resolved.className.lastIndexOf('.') + 1)
                            : resolved.className;
                    JavaClassInfo info = new JavaClassInfo(simpleName, resolved.className, meta);
                    return new JavaResolvedType(info, resolved.typeArgBindings);
                }
            }
        }

        // Fallback to erased return type
        JavaClassInfo info = resolveJavaClassFromDescriptor(sig.getReturnDescriptor());
        if (info != null) return new JavaResolvedType(info);
        return null;
    }

    public JavaClassInfo resolveJavaClassForSiyoType(Class<?> type) {
        if (type == String.class) {
            return getOrLoadJavaClass("String", "java.lang.String");
        }
        if (type == SiyoChannel.class) {
            return getOrLoadJavaClass("SiyoChannel", "codeanalysis.SiyoChannel");
        }
        if (type == SiyoMap.class) {
            return getOrLoadJavaClass("SiyoMap", "codeanalysis.SiyoMap");
        }
        if (type == SiyoSet.class) {
            return getOrLoadJavaClass("SiyoSet", "codeanalysis.SiyoSet");
        }
        return null;
    }

    public JavaClassInfo resolveJavaClassForSiyoTypeName(String name) {
        return switch (name) {
            case "String" -> getOrLoadJavaClass("String", "java.lang.String");
            case "Integer" -> getOrLoadJavaClass("Integer", "java.lang.Integer");
            case "Boolean" -> getOrLoadJavaClass("Boolean", "java.lang.Boolean");
            case "Double" -> getOrLoadJavaClass("Double", "java.lang.Double");
            case "Object" -> getOrLoadJavaClass("Object", "java.lang.Object");
            default -> null;
        };
    }

    public JavaClassInfo resolveJavaClassFromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.length() <= 1) return null; // primitives/void
        if (descriptor.equals("Ljava/lang/String;")) return null; // String is native Siyo type
        if (descriptor.equals("Ljava/lang/Object;")) return null; // Object too generic — dynamic dispatch
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            String fullName = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
            // Check if already imported
            String simpleName = fullName.contains(".") ? fullName.substring(fullName.lastIndexOf('.') + 1) : fullName;
            JavaClassInfo existing = _javaClasses.get(simpleName);
            if (existing != null) return existing;
            // Auto-load metadata for return types
            JavaClassMetadata metadata = JavaClassMetadata.load(fullName);
            if (metadata != null) {
                JavaClassInfo info = new JavaClassInfo(simpleName, fullName, metadata);
                _javaClasses.put(simpleName, info);
                return info;
            }
        }
        return null;
    }

    public JavaClassInfo getOrLoadJavaClass(String simpleName, String fullName) {
        JavaClassInfo existing = _javaClasses.get(simpleName);
        if (existing != null) return existing;
        JavaClassMetadata meta = JavaClassMetadata.load(fullName);
        if (meta != null) {
            JavaClassInfo info = new JavaClassInfo(simpleName, fullName, meta);
            _javaClasses.put(simpleName, info);
            return info;
        }
        return null;
    }

    public Class<?> lookupType(String name) {
        if (name == null) return null;
        // A function type carries its signature in the name, so it is matched
        // before the array suffix: fn()->int[] is a function returning an
        // array, while fn()[] is an array of functions.
        if (FunctionTypeSignature.isSignature(name)) {
            return SiyoClosure.class;
        }
        if (FunctionTypeSignature.isFunctionArray(name)) {
            return SiyoArray.class;
        }
        if (name.endsWith("[]")) {
            return SiyoArray.class;
        }
        // A generic type is erased to the shape it has at run time; the type
        // arguments are what the call and index sites read back.
        String generic = genericBaseName(name);
        if (generic != null) {
            return switch (generic) {
                case "Array", "List" -> SiyoArray.class;
                case "Map" -> SiyoMap.class;
                case "Set" -> SiyoSet.class;
                default -> lookupType(generic);
            };
        }
        if (_typeParameters.contains(name)) {
            return Object.class;
        }
        Class<?> builtin = switch (name) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "bool" -> Boolean.class;
            case "float" -> Double.class;
            case "string" -> String.class;
            case "fn", "func", "function" -> SiyoClosure.class;
            case "channel" -> SiyoChannel.class;
            case "map" -> SiyoMap.class;
            case "set" -> SiyoSet.class;
            case "object", "any" -> Object.class;
            default -> _structTypes.containsKey(name) ? SiyoStruct.class
                    : _unionTypes.containsKey(name) ? SiyoUnion.class
                    // A value of an interface type is a struct at run time;
                    // which struct is what the call site discovers.
                    : _interfaceTypes.containsKey(name) ? SiyoStruct.class
                    : _declaredAhead.get(name);
        };
        if (builtin != null) return builtin;
        // Imported Java classes are represented as Object in Siyo function ABI,
        // with their precise Java identity tracked separately for member/overload resolution.
        return _javaClasses.containsKey(name) ? Object.class : null;
    }

    public Class<?> lookupElementType(String typeName) {
        if (typeName == null) return null;
        if (typeName.endsWith("[]")) {
            return lookupType(typeName.substring(0, typeName.length() - 2));
        }
        // Array<int> holds what int[] holds; Map<string, int> holds its values.
        List<String> arguments = typeArgumentsOf(typeName);
        if (arguments.isEmpty()) return null;
        String base = genericBaseName(typeName);
        if (("Array".equals(base) || "List".equals(base) || "Set".equals(base)) && arguments.size() == 1) {
            return lookupType(arguments.get(0));
        }
        if ("Map".equals(base) && arguments.size() == 2) {
            return lookupType(arguments.get(1));
        }
        return null;
    }

    /**
     * The name of a generic type without its type arguments, or null when the
     * name carries none.
     *
     * @param typeName The type name as written.
     * @return The base name, or null.
     */
    public static String genericBaseName(String typeName) {
        if (typeName == null) return null;
        int open = typeName.indexOf('<');
        if (open <= 0 || !typeName.endsWith(">")) return null;
        return typeName.substring(0, open);
    }

    /**
     * A type name with its type arguments dropped, which is the name the
     * declaration was registered under.
     *
     * <p>{@code Option<int>} and {@code Option<string>} are the same declared
     * type — the parameter is erased — so both have to find it.
     *
     * @param typeName The type name as written.
     * @return The declared name.
     */
    public static String erasedTypeName(String typeName) {
        String base = genericBaseName(typeName);
        return base != null ? base : typeName;
    }

    /**
     * The type arguments of a generic type name, in order.
     *
     * <p>Nesting is respected, so {@code Map<string, Array<int>>} reads as two
     * arguments and not three.
     *
     * @param typeName The type name as written.
     * @return The type arguments, or an empty list when there are none.
     */
    public static List<String> typeArgumentsOf(String typeName) {
        if (genericBaseName(typeName) == null) return List.of();
        String inner = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
        List<String> arguments = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                arguments.add(inner.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = inner.substring(start).trim();
        if (!last.isEmpty()) arguments.add(last);
        return arguments;
    }

    /**
     * The names currently standing for a type parameter, so {@code T} inside a
     * generic function resolves rather than being reported as unknown.
     *
     * @return The type parameter names in scope.
     */
    public java.util.Set<String> getTypeParameters() {
        return _typeParameters;
    }

    /**
     * Records a type a file declares, before its declaration is registered:
     * a function above {@code struct Later} may return a {@code Later}.
     *
     * @param name The type's name.
     * @param kind What a value of it is at run time.
     */
    public void declareAhead(String name, Class<?> kind) {
        if (name != null) _declaredAhead.putIfAbsent(name, kind);
    }

    // --- Accessors ---

    public Map<String, JavaClassInfo> getJavaClasses() {
        return _javaClasses;
    }

    /** The sum types in scope, keyed by name. */
    public Map<String, UnionSymbol> getUnionTypes() {
        return _unionTypes;
    }
}
