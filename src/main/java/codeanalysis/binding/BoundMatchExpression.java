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

    public record BoundMatchArm(BoundExpression pattern, BoundExpression body, boolean isDefault,
                                    List<BoundStatement> preStatements) {
        public BoundMatchArm(BoundExpression pattern, BoundExpression body, boolean isDefault) {
            this(pattern, body, isDefault, List.of());
        }
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
