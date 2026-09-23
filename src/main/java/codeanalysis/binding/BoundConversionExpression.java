package codeanalysis.binding;

import java.util.Collections;
import java.util.Iterator;

/**
 * Widens a numeric value to a wider numeric type.
 *
 * <p>Arithmetic over mixed numeric types used to be rejected outright, so
 * {@code 1 + 2.5} did not compile and every mixed expression had to be written
 * with an explicit conversion. Rather than enumerate an operator for each pair
 * of types, the narrower operand is wrapped in one of these and the operator is
 * looked up again at the common type.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundConversionExpression extends BoundExpression {
    private final BoundExpression _expression;
    private final Class<?> _targetType;

    /**
     * Constructs a widening of an expression to a numeric type.
     *
     * @param expression The value to widen.
     * @param targetType The type to widen it to.
     */
    public BoundConversionExpression(BoundExpression expression, Class<?> targetType) {
        _expression = expression;
        _targetType = targetType;
    }

    /**
     * Gets the value being widened.
     *
     * @return The wrapped expression.
     */
    public BoundExpression getExpression() {
        return _expression;
    }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.ConversionExpression;
    }

    @Override
    public Class<?> getClassType() {
        return _targetType;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        return Collections.<BoundNode>singletonList(_expression).iterator();
    }
}
