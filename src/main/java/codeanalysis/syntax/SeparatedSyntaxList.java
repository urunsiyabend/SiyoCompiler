package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a separated syntax list, which is a list of syntax nodes separated by tokens.
 * Used for parameter lists and argument lists where items are separated by commas.
 *
 * @param <T> The type of syntax nodes in the list.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SeparatedSyntaxList<T extends SyntaxNode> implements Iterable<T> {
    private final List<SyntaxNode> _nodesAndSeparators;

    /**
     * Creates a new instance of SeparatedSyntaxList.
     *
     * @param nodesAndSeparators The list containing nodes and separators interleaved.
     */
    public SeparatedSyntaxList(List<SyntaxNode> nodesAndSeparators) {
        _nodesAndSeparators = nodesAndSeparators;
    }

    /**
     * Gets the number of nodes in the list (excluding separators).
     *
     * @return The count of nodes.
     */
    public int getCount() {
        return (_nodesAndSeparators.size() + 1) / 2;
    }

    /**
     * Gets the node at the specified index.
     *
     * @param index The index of the node.
     * @return The node at the index.
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        return (T) _nodesAndSeparators.get(index * 2);
    }

    /**
     * Gets the separator at the specified index.
     *
     * @param index The index of the separator (between items).
     * @return The separator token, or null if there's no separator at that index.
     */
    public SyntaxToken getSeparator(int index) {
        if (index == getCount() - 1) {
            return null;
        }
        return (SyntaxToken) _nodesAndSeparators.get(index * 2 + 1);
    }

    /**
     * Gets the list containing all nodes and separators.
     *
     * @return The list of nodes and separators.
     */
    public List<SyntaxNode> getNodesAndSeparators() {
        return _nodesAndSeparators;
    }

    /**
     * Returns an iterator over the nodes in the list (excluding separators).
     *
     * @return An iterator over the nodes.
     */
    @Override
    public Iterator<T> iterator() {
        return new NodeIterator();
    }

    private class NodeIterator implements Iterator<T> {
        private int index = 0;

        @Override
        public boolean hasNext() {
            return index < getCount();
        }

        @Override
        public T next() {
            return get(index++);
        }
    }
}
