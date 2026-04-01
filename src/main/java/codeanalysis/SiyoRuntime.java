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

        // Find the closureDispatch$ method on the caller's class
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        Class<?> callerClass = walker.walk(frames ->
                frames.skip(1).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));

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
