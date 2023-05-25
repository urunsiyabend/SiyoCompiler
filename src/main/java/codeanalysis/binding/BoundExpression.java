package codeanalysis.binding;

/**
 * Abstract class for representing bound expressions in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class BoundExpression extends BoundNode {
    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    public abstract Class<?> getClassType();
}