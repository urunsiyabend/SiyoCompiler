package codeanalysis.binding;

/**
 * Represents a bound node in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class BoundNode {
    /**
     * Gets the type of the bound node.
     *
     * @return The type of the bound node.
     */
    public abstract BoundNodeType getType();
}