package codeanalysis.syntax;

import org.junit.jupiter.api.Assertions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class AssertingEnumerator implements AutoCloseable {

    private final Iterator<SyntaxNode> enumerator;
    private boolean hasErrors;

    public AssertingEnumerator(SyntaxNode node) {
        Deque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(node);
        enumerator = flatten(stack).iterator();
    }

    private boolean markFailed() {
        hasErrors = true;
        return false;
    }

    private Iterable<SyntaxNode> flatten(Deque<SyntaxNode> stack) {
        return () -> new Iterator<SyntaxNode>() {
            private Iterator<SyntaxNode> currentChildren = null;

            @Override
            public boolean hasNext() {
                if (currentChildren != null && currentChildren.hasNext()) {
                    return true;
                } else {
                    return !stack.isEmpty();
                }
            }

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


    @Override
    public void close() {
        if (!hasErrors) {
            Assertions.assertFalse(enumerator.hasNext());
        }
    }

    public void assertNode(SyntaxType kind) {
        try {
            Assertions.assertTrue(enumerator.hasNext());
            SyntaxNode current = enumerator.next();
            Assertions.assertEquals(kind, current.getType());
            Assertions.assertFalse(current instanceof SyntaxToken);
        } catch (Throwable t) {
            if (markFailed()) {
                throw t;
            }
        }
    }

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
