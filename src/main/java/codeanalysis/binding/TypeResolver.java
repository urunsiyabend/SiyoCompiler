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
        if (!(target instanceof BoundVariableExpression varExpr)) return null;
        String declaredName = varExpr.getVariable().getDeclaredTypeName();
        if (!"Map".equals(genericBaseName(declaredName))) return null;
        List<String> arguments = typeArgumentsOf(declaredName);
        return arguments.size() == 2 ? lookupType(arguments.get(1)) : null;
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
                String fieldTypeName = structType.getFieldTypeName(memberExpr.getMemberName());
                if (fieldTypeName != null) {
                    Class<?> elemType = lookupElementType(fieldTypeName);
                    if (elemType != null) return elemType;
                }
            }
        }
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
                String fieldTypeName = ownerStruct.getFieldTypeName(memberExpr.getMemberName());
                if (fieldTypeName != null) {
                    StructSymbol fieldStruct = _structTypes.get(fieldTypeName);
                    if (fieldStruct != null) return fieldStruct;
                }
            }
        }
        // Index expression on struct array: todos[i] → resolve element struct type
        if (target instanceof BoundIndexExpression indexExpr && indexExpr.getClassType() == SiyoStruct.class) {
            return resolveStructTypeFromCollection(indexExpr.getTarget());
        }
        // Call expression returning struct
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
        if (collection instanceof BoundCallExpression callExpr) {
            String structName = callExpr.getFunction().getReturnElementStructName();
            if (structName != null) {
                StructSymbol structType = _structTypes.get(structName);
                if (structType != null) return structType;
            }
        }
        // Struct field array: self.todos where todos: Todo[]
        if (collection instanceof BoundMemberAccessExpression memberExpr) {
            StructSymbol ownerStruct = resolveStructType(memberExpr.getTarget());
            if (ownerStruct != null) {
                String fieldTypeName = ownerStruct.getFieldTypeName(memberExpr.getMemberName());
                if (fieldTypeName != null && fieldTypeName.endsWith("[]")) {
                    String elemName = fieldTypeName.substring(0, fieldTypeName.length() - 2);
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
                    : null;
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

    // --- Accessors ---

    public Map<String, JavaClassInfo> getJavaClasses() {
        return _javaClasses;
    }

    /** The sum types in scope, keyed by name. */
    public Map<String, UnionSymbol> getUnionTypes() {
        return _unionTypes;
    }
}
