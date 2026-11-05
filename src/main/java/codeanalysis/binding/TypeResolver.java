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
    private final Map<String, StructSymbol> _structTypes;
    private final Map<String, UnionSymbol> _unionTypes;
    private final Map<String, InterfaceSymbol> _interfaceTypes = new HashMap<>();
    private final Map<VariableSymbol, InterfaceSymbol> _variableInterfaces = new HashMap<>();
    private final java.util.Set<String> _typeParameters = new java.util.LinkedHashSet<>();
    // Types a file declares, known by kind before each declaration is registered,
    // so a signature may name a struct or sum type written further down.
    private final Map<String, Class<?>> _declaredAhead = new java.util.HashMap<>();

    public TypeResolver(Map<String, StructSymbol> structTypes, Map<String, UnionSymbol> unionTypes) {
        _structTypes = structTypes;
        _unionTypes = unionTypes;
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
        // filter keeps its elements, and so the generic struct they are.
        if (expr instanceof BoundCallExpression filtered && filtered.getFunction() == BuiltinFunctions.FILTER
                && !filtered.getArguments().isEmpty()) {
            String name = declaredTypeNameOf(filtered.getArguments().get(0));
            return mentionsGenericStruct(name) ? name : null;
        }
        if (expr instanceof BoundCallExpression call && call.getFunction() == BuiltinFunctions.POP
                && !call.getArguments().isEmpty()) {
            return elementTypeNameOf(declaredTypeNameOf(call.getArguments().get(0)));
        }
        if (expr instanceof BoundCallExpression generic && generic.getResultTypeName() != null) {
            return generic.getResultTypeName();
        }
        if (expr instanceof BoundCallExpression call && call.getFunction().getTypeParameters().isEmpty()) {
            return call.getFunction().getDeclaredReturnTypeName();
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
        String elementName = elementTypeNameOf(varExpr.getVariable().getDeclaredTypeName());
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
                && generic.getResultTypeName() != null) {
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
        if (collection instanceof BoundCallExpression generic && generic.getResultTypeName() != null) {
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
