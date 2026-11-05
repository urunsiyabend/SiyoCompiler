package codeanalysis.binding;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every bound node a node directly holds.
 *
 * <p>{@link BoundNode#getChildren()} is written for printing a tree and is empty
 * or partial on a dozen composite nodes, so a walk built on it misses whatever
 * those hold. Listing the exceptions by hand kept missing some: a variable used
 * only as a union argument or a cast operand went uncaptured. This reads a node's
 * fields instead, so a new node or a new operand is covered without a change here.
 * What a field may hold is pinned by {@code CaptureTraversalTest}.
 */
public final class BoundOperands {
    private BoundOperands() {
    }

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            return fields;
        }
    };

    /**
     * @param node The node.
     * @return The nodes it holds, directly or through a list, a map or a record.
     */
    public static List<BoundNode> of(BoundNode node) {
        List<BoundNode> operands = new ArrayList<>();
        collectFields(node, operands);
        return operands;
    }

    private static void collectFields(Object holder, List<BoundNode> operands) {
        try {
            for (Field field : FIELDS.get(holder.getClass())) {
                collect(field.get(holder), operands);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collect(Object value, List<BoundNode> operands) {
        if (value instanceof BoundNode node) {
            operands.add(node);
        } else if (value instanceof Iterable<?> values) {
            for (Object element : values) collect(element, operands);
        } else if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) collect(element, operands);
        } else if (value instanceof Record record) {
            collectFields(record, operands);
        }
    }
}
