package com.urunsiyabend.codeanalysis;

import java.util.Iterator;

/**
 * The base class for syntax nodes in a code analysis system.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class SyntaxNode {
    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type of the node.
     */
    public abstract SyntaxType getType();

    /**
     * Gets an iterator over the child nodes of the syntax node.
     *
     * @return An iterator over the child nodes.
     */
    public abstract Iterator<SyntaxNode> getChildren();
}