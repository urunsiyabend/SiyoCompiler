package codeanalysis.syntax;

import codeanalysis.text.TextSpan;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * The base class for syntax nodes in a code analysis system.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class SyntaxNode {
    public TextSpan _span;

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

    /**
     * Gets the span of the syntax node.
     *
     * @return The span of the syntax node.
     */
    public TextSpan getSpan() {
        ArrayList<SyntaxNode> childrens = (ArrayList<SyntaxNode>) getChildren();
        TextSpan first = childrens.get(0).getSpan();
        TextSpan last = childrens.get(childrens.size() - 1).getSpan();
        return TextSpan.fromBounds(first.getStart(), last.getEnd());
    }

    /**
     * Writes the syntax tree to the specified writer.
     *
     * @param writer The writer to write the tree to.
     * @throws IOException If an I/O error occurs.
     */
    public void writeTo(Writer writer) throws IOException {
        prettyPrint(writer, this, "", true);
    }

    /**
     * Recursively pretty prints the syntax tree.
     *
     * @param writer The writer to print the tree to.
     * @param node   The syntax node to print.
     * @param indent The indentation string.
     * @param isLast Specifies if the current node is the last child of its parent.
     */
    static void prettyPrint(Writer writer, SyntaxNode node, String indent, boolean isLast) throws IOException {
        String marker = isLast ? "└──" : "├──";

        writer.write(indent);
        writer.write(marker);
        writer.write(node.getType().toString());


        if (node instanceof SyntaxToken t && t.getValue() != null) {
            writer.write(" ");
            writer.write(t.getValue().toString());
        }

        writer.write("\n");

        indent += isLast ? "    ": "│   ";

        Iterator<SyntaxNode> iterator = node.getChildren();
        while (iterator.hasNext()) {
            SyntaxNode child = iterator.next();
            boolean last = !iterator.hasNext();
            prettyPrint(writer, child, indent, last);
        }
    }

    /**
     * Gets the value associated with the syntax node.
     *
     * @return The value associated with the syntax node.
     */
    @Override
    public String toString() {
        StringWriter stringWriter = new StringWriter();
        try {
            writeTo(stringWriter);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringWriter.toString();
    }
}