package codeanalysis.binding;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Bound match expression: match(target) with arms [pattern → body]
 */
public class BoundMatchExpression extends BoundExpression {
    private final BoundExpression _target;
    private final List<BoundMatchArm> _arms;
    private final Class<?> _type;

    /**
     * One arm of a match.
     *
     * <p>Exactly one selector is set: {@code pattern} for an arm that compares
     * the matched value against an expression, {@code variant} for an arm that
     * selects a variant of a sum type and binds its payload, and neither for
     * the default arm.</p>
     *
     * @param pattern       The value to compare against, or null.
     * @param body          The arm's result.
     * @param isDefault     Whether this is the {@code _} arm.
     * @param preStatements Statements a block body runs before its result.
     * @param variant       The variant pattern, or null.
     */
    public record BoundMatchArm(BoundExpression pattern, BoundExpression body, boolean isDefault,
                                    List<BoundStatement> preStatements, BoundVariantPattern variant) {
        public BoundMatchArm(BoundExpression pattern, BoundExpression body, boolean isDefault) {
            this(pattern, body, isDefault, List.of(), null);
        }

        public BoundMatchArm(BoundExpression pattern, BoundExpression body, boolean isDefault,
                             List<BoundStatement> preStatements) {
            this(pattern, body, isDefault, preStatements, null);
        }
    }

    /**
     * A variant pattern: the variant an arm selects, and the variable each
     * payload slot is bound to, with a null entry where the pattern discarded
     * the slot with {@code _}.
     *
     * @param unionName   The sum type the variant belongs to.
     * @param variantName The variant selected.
     * @param bindings    The variable per payload slot, null where discarded.
     */
    public record BoundVariantPattern(String unionName, String variantName,
                                      List<codeanalysis.VariableSymbol> bindings) {
    }

    public BoundMatchExpression(BoundExpression target, List<BoundMatchArm> arms, Class<?> type) {
        _target = target;
        _arms = arms;
        _type = type;
    }

    public BoundExpression getTarget() { return _target; }
    public List<BoundMatchArm> getArms() { return _arms; }

    @Override
    public BoundNodeType getType() { return BoundNodeType.MatchExpression; }

    @Override
    public Class<?> getClassType() { return _type; }

    @Override
    public Iterator<BoundNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 1 + _arms.size() * 2; }
            @Override public BoundNode next() {
                if (idx == 0) { idx++; return _target; }
                int armIdx = (idx - 1) / 2;
                boolean isPattern = (idx - 1) % 2 == 0;
                idx++;
                var arm = _arms.get(armIdx);
                return isPattern && arm.pattern != null ? arm.pattern : arm.body;
            }
        };
    }
}
