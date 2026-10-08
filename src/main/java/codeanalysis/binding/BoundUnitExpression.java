package codeanalysis.binding;

/**
 * An expression that produces nothing: the body left behind in a match arm
 * whose value is discarded.
 *
 * <p>A match written as a statement has no value, so its arms need not agree
 * on one. Each arm's own value is moved into its statements, where an
 * expression statement discards it, and the arm's body becomes this. It is a
 * literal so every pass that walks literals handles it; its type is void, so
 * nothing is pushed for it and nothing is popped.
 */
public class BoundUnitExpression extends BoundLiteralExpression {
    public BoundUnitExpression() {
        super(null);
    }

    @Override
    public Class<?> getClassType() {
        return null;
    }
}
