package codeanalysis;

import java.lang.invoke.*;

/**
 * Bootstrap methods for invokedynamic in Siyo bytecode.
 * Handles method calls on dynamically-typed values (compile-time Object).
 * First call resolves method via runtime type → MethodHandle cached by JVM.
 */
public class SiyoDynamic {

    /**
     * Bootstrap method for dynamic method dispatch.
     * Called by JVM on first invokedynamic execution.
     * Returns a CallSite that caches the resolved MethodHandle.
     */
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String methodName,
                                      MethodType callSiteType) throws Exception {
        // Create a mutable CallSite — allows relinking if target type changes
        MutableCallSite callSite = new MutableCallSite(callSiteType);

        // Create the fallback handler that resolves on first call
        MethodHandle fallback = MethodHandles.lookup()
                .findStatic(SiyoDynamic.class, "resolve",
                        MethodType.methodType(Object.class, MutableCallSite.class, String.class, Object[].class))
                .bindTo(callSite)
                .bindTo(methodName)
                .asCollector(Object[].class, callSiteType.parameterCount())
                .asType(callSiteType);

        callSite.setTarget(fallback);
        return callSite;
    }

    /**
     * Resolve method at runtime using the actual type of the first argument (target).
     * After resolution, updates the CallSite with a direct MethodHandle for future calls.
     */
    public static Object resolve(MutableCallSite callSite, String methodName, Object[] args) throws Throwable {
        if (args.length == 0) {
            throw new RuntimeException("Dynamic call requires at least one argument (target)");
        }

        Object target = args[0];
        if (target == null) {
            throw new RuntimeException("Cannot call method '" + methodName + "' on null");
        }

        Class<?> targetClass = target.getClass();
        Object[] methodArgs = new Object[args.length - 1];
        System.arraycopy(args, 1, methodArgs, 0, methodArgs.length);

        // Find the method on the actual runtime type
        for (var method : targetClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == methodArgs.length) {
                try {
                    // Coerce args
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < methodArgs.length; i++) {
                        if (methodArgs[i] instanceof SiyoArray && paramTypes[i].isArray()) {
                            methodArgs[i] = siyoArrayToJavaArray((SiyoArray) methodArgs[i], paramTypes[i].getComponentType());
                        }
                    }

                    Object result = method.invoke(target, methodArgs);

                    // Convert Java return types to Siyo types
                    if (result instanceof Long l) return l.intValue();
                    if (result instanceof Short s) return (int) s;
                    if (result instanceof Byte b) return (int) b;
                    if (result instanceof Float f) return f.doubleValue();
                    if (result instanceof Character c) return String.valueOf(c);
                    if (result != null && result.getClass().isArray()) {
                        int len = java.lang.reflect.Array.getLength(result);
                        java.util.List<Object> elements = new java.util.ArrayList<>();
                        for (int i = 0; i < len; i++) {
                            Object elem = java.lang.reflect.Array.get(result, i);
                            if (elem instanceof Byte bv) elements.add((int) bv);
                            else elements.add(elem);
                        }
                        return new SiyoArray(elements, Object.class);
                    }
                    return result;
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
        }

        throw new RuntimeException("No matching method: " + targetClass.getName() + "." + methodName
                + " with " + methodArgs.length + " args");
    }

    private static Object siyoArrayToJavaArray(SiyoArray arr, Class<?> componentType) {
        Object javaArr = java.lang.reflect.Array.newInstance(componentType, arr.length());
        for (int i = 0; i < arr.length(); i++) {
            Object elem = arr.get(i);
            if (componentType == byte.class) java.lang.reflect.Array.setByte(javaArr, i, ((Number) elem).byteValue());
            else if (componentType == int.class) java.lang.reflect.Array.setInt(javaArr, i, ((Number) elem).intValue());
            else java.lang.reflect.Array.set(javaArr, i, elem);
        }
        return javaArr;
    }
}
