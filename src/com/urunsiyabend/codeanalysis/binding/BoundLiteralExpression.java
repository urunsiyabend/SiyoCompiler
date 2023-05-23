package com.urunsiyabend.codeanalysis.binding;

/**
 * Represents a bound literal expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundLiteralExpression extends BoundExpression {
    private Object value;

    /**
     * Constructs a new instance of the BoundLiteralExpression class.
     *
     * @param value The value of the literal expression.
     */
    public BoundLiteralExpression(Object value) {
        setValue(value);
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.LiteralExpression;
    }

    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    @Override
    public Class<?> getClassType() {
        return (Class<?>) getValue();
    }

    /**
     * Gets the value of the literal expression.
     *
     * @return The value of the literal expression.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value of the literal expression.
     *
     * @param value The value to set for the literal expression.
     */
    public void setValue(Object value) {
        this.value = value;
    }
}