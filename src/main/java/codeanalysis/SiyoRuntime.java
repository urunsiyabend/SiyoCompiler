package codeanalysis;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Comparator;
import java.util.List;

/**
 * Runtime support methods for compiled Siyo bytecode.
 */
public class SiyoRuntime {
    /** Program arguments, set by the entry main method. */
    public static volatile String[] programArgs = new String[0];

    /** Returns program arguments as a SiyoArray. */
    public static SiyoArray getProgramArgs() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        for (String arg : programArgs) list.add(arg);
        return new SiyoArray(list, String.class);
    }


    /**
     * Sorts a list using a Siyo closure as comparator.
     * The closure is Object[]{Integer(lambdaId), Object[]{captured}}.
     * Calls closureDispatch$ on the class that defined the closure.
     */
    @SuppressWarnings("unchecked")
    public static void sortList(List<?> list, Object closureObj) {
        Object[] closure = (Object[]) closureObj;
        int lambdaId = (Integer) closure[0];
        Object[] captured = (Object[]) closure[1];

        // Use origin class name from closure[2] if available, otherwise fallback to StackWalker
        Class<?> callerClass;
        if (closure.length > 2 && closure[2] instanceof String className) {
            try {
                callerClass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("sort: class not found: " + className, e);
            }
        } else {
            StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
            callerClass = walker.walk(frames ->
                    frames.skip(1).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        }

        try {
            MethodHandle dispatch = MethodHandles.lookup().findStatic(callerClass, "closureDispatch$",
                    MethodType.methodType(Object.class, int.class, Object[].class, Object[].class));

            ((List<Object>) list).sort((a, b) -> {
                try {
                    Object result = dispatch.invoke(lambdaId, captured, new Object[]{a, b});
                    return (result instanceof Integer i) ? i : 0;
                } catch (Throwable e) {
                    return 0;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("sort: cannot invoke closure comparator", e);
        }
    }

    /**
     * Dispatch a closure call across class boundaries.
     * Used when a closure created in class A is invoked by class B (e.g., module functions).
     */
    public static Object dispatchClosure(String className, int lambdaId, Object[] captured, Object[] args) {
        try {
            // Try context classloader first, then calling class's classloader
            Class<?> cls = null;
            ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader != null) {
                try { cls = Class.forName(className, true, ctxLoader); } catch (ClassNotFoundException ignored) {}
            }
            if (cls == null) {
                // Fallback: walk the stack to find the caller's classloader
                StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
                ClassLoader callerLoader = walker.walk(frames ->
                        frames.skip(1).findFirst().map(f -> f.getDeclaringClass().getClassLoader()).orElse(null));
                if (callerLoader != null) {
                    cls = Class.forName(className, true, callerLoader);
                }
            }
            if (cls == null) {
                cls = Class.forName(className);
            }
            MethodHandle dispatch = MethodHandles.lookup().findStatic(cls, "closureDispatch$",
                    MethodType.methodType(Object.class, int.class, Object[].class, Object[].class));
            return dispatch.invoke(lambdaId, captured, args);
        } catch (Throwable e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("closure dispatch failed for " + className, e);
        }
    }

    /** parseInt that returns 0 on invalid input (matches interpreter behavior). */
    public static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** parseDouble that returns 0.0 on invalid input (matches interpreter behavior). */
    public static double safeParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
