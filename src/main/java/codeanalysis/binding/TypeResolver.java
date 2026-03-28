package codeanalysis.binding;

import codeanalysis.*;;

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

    public TypeResolver(Map<String, StructSymbol> structTypes) {
        _structTypes = structTypes;
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
        if (name.endsWith("[]")) {
            return SiyoArray.class;
        }
        return switch (name) {
            case "int" -> Integer.class;
            case "bool" -> Boolean.class;
            case "float" -> Double.class;
            case "string" -> String.class;
            case "fn", "func", "function" -> SiyoClosure.class;
            default -> _structTypes.containsKey(name) ? SiyoStruct.class : null;
        };
    }

    public Class<?> lookupElementType(String typeName) {
        if (typeName.endsWith("[]")) {
            return lookupType(typeName.substring(0, typeName.length() - 2));
        }
        return null;
    }

    // --- Accessors ---

    public Map<String, JavaClassInfo> getJavaClasses() {
        return _javaClasses;
    }
}
