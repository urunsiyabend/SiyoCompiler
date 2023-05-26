package codeanalysis.syntax;

import org.junit.jupiter.api.Assertions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * The AssertingEnumerator class contains methods for asserting the syntax tree.
 * It ensures that the syntax tree is correctly implemented.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class AssertingEnumerator implements AutoCloseable {
    private final Iterator<SyntaxNode> enumerator;
    private boolean hasErrors;

    /**
     * Creates a new instance of the AssertingEnumerator class with the specified syntax node.
     *
     * @param node   The syntax node.
     */
    public AssertingEnumerator(SyntaxNode node) {
        Deque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(node);
        enumerator = flatten(stack).iterator();
    }

    /**
     * Asserts that the next node is of the specified type.
     *
     * @return True if the next node is of the specified type, otherwise false.
     */
    private boolean markFailed() {
        hasErrors = true;
        return false;
    }


    /**
     * Flattens the syntax tree by providing an iterable of flattened syntax nodes.
     *
     * @param stack The stack of syntax nodes to be flattened.
     * @return An iterable of flattened syntax nodes.
     */
    private Iterable<SyntaxNode> flatten(Deque<SyntaxNode> stack) {
        return () -> new Iterator<SyntaxNode>() {
            private Iterator<SyntaxNode> currentChildren = null;

            /**
             * Checks if there are more flattened nodes to iterate over.
             *
             * @return {@code true} if there are more flattened nodes, {@code false} otherwise.
             */
            @Override
            public boolean hasNext() {
                if (currentChildren != null && currentChildren.hasNext()) {
                    return true;
                } else {
                    return !stack.isEmpty();
                }
            }

            /**
             * Returns the next flattened node.
             *
             * @return The next flattened node.
             */
            @Override
            public SyntaxNode next() {
                if (currentChildren != null && currentChildren.hasNext()) {
                    return currentChildren.next();
                } else {
                    SyntaxNode node = stack.pop();
                    currentChildren = node.getChildren();
                    return node;
                }
            }
        };
    }


    /**
     * Closes the AssertingEnumerator instance.
     */
    @Override
    public void close() {
        if (!hasErrors) {
            Assertions.assertFalse(enumerator.hasNext());
        }
    }

    /**
     * Asserts that the next node is of the specified type.
     *
     * @param type The syntax type of the node.
     */
    public void assertNode(SyntaxType type) {
        try {
            Assertions.assertTrue(enumerator.hasNext());
            SyntaxNode current = enumerator.next();
            Assertions.assertEquals(type, current.getType());
            Assertions.assertFalse(current instanceof SyntaxToken);
        } catch (Throwable t) {
            if (markFailed()) {
                throw t;
            }
        }
    }

    /**
     * Asserts that the next node is of the specified type and has the specified text.
     *
     * @param type The syntax type of the node.
     * @param text The text of the node.
     */
    public void assertToken(SyntaxType type, String text) {
        try {
            Assertions.assertTrue(enumerator.hasNext());
            SyntaxNode current = enumerator.next();
            Assertions.assertEquals(type, current.getType());
            SyntaxToken token = (SyntaxToken) current;
            Assertions.assertEquals(text, token.getData());
        } catch (Throwable t) {
            if (markFailed()) {
                throw t;
            }
        }
    }
}
