package codeanalysis.binding;

import codeanalysis.BuiltinFunctions;
import codeanalysis.JavaClassInfo;
import codeanalysis.JavaClassMetadata;
import codeanalysis.SiyoArray;
import codeanalysis.SiyoStruct;
import codeanalysis.StructSymbol;
import codeanalysis.VariableSymbol;

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
            // range() returns int array
            if (callExpr.getFunction() == BuiltinFunctions.RANGE) {
                return Integer.class;
            }
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
        return null;
    }

    public JavaClassInfo resolveJavaClassInfo(BoundExpression expr) {
        if (expr instanceof BoundVariableExpression varExpr) {
            JavaClassInfo info = getVarJavaClassType(varExpr.getVariable());
            if (info != null) return info;
            // Auto-resolve Java class for native Siyo types
            return resolveJavaClassForSiyoType(varExpr.getVariable().getType());
        }
        if (expr instanceof BoundJavaMethodCallExpression javaCall) {
            if (javaCall.isConstructor() && javaCall.getClassInfo() != null) {
                return javaCall.getClassInfo();
            }
            if (javaCall.getResolvedSignature() != null) {
                return resolveJavaClassFromDescriptor(javaCall.getResolvedSignature().getReturnDescriptor());
            }
        }
        // For literal expressions and other typed expressions
        return resolveJavaClassForSiyoType(expr.getClassType());
    }

    public JavaClassInfo resolveJavaClassForSiyoType(Class<?> type) {
        if (type == String.class) {
            return getOrLoadJavaClass("String", "java.lang.String");
        }
        return null;
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
