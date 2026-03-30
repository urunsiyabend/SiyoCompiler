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

        // Actor dispatch: if target is SiyoActor, check for direct methods first (stop, isStopped),
        // then route user-defined methods through actor.call()
        if (target instanceof SiyoActor actor) {
            // Check if method exists directly on SiyoActor (lifecycle methods)
            for (var m : SiyoActor.class.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length - 1) {
                    Object[] methodArgs = new Object[args.length - 1];
                    System.arraycopy(args, 1, methodArgs, 0, methodArgs.length);
                    return m.invoke(actor, methodArgs);
                }
            }
            // User-defined actor method → route through mailbox
            Object[] methodArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, methodArgs, 0, methodArgs.length);
            return actor.call(methodName, methodArgs);
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

                    Object result;
                    try {
                        result = method.invoke(target, methodArgs);
                    } catch (IllegalAccessException iae) {
                        // Module-private class (e.g. SocketOutputStream) — find on public superclass/interface
                        java.lang.reflect.Method accessible = findAccessibleMethod(targetClass, methodName, paramTypes);
                        if (accessible != null) {
                            result = accessible.invoke(target, methodArgs);
                        } else {
                            method.setAccessible(true);
                            result = method.invoke(target, methodArgs);
                        }
                    }

                    // Convert Java return types to Siyo types
                    if (result instanceof Long) return result; // preserve as Long
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

    /**
     * Find a method on a public superclass or interface when the declaring class is module-private.
     */
    private static java.lang.reflect.Method findAccessibleMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
        // Search public interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return iface.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }
        // Search public superclasses
        Class<?> sup = clazz.getSuperclass();
        while (sup != null && sup != Object.class) {
            if (java.lang.reflect.Modifier.isPublic(sup.getModifiers())) {
                try {
                    return sup.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
            sup = sup.getSuperclass();
        }
        return null;
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
