package codeanalysis.binding;

import codeanalysis.text.TextSpan;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

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

    /**
     * Gets an iterator over the child nodes of the syntax node.
     *
     * @return An iterator over the child nodes.
     */
    public abstract Iterator<BoundNode> getChildren();

    /**
     * Gets the span of the syntax node.
     *
     * @return The span of the syntax node.
     */
    public TextSpan getSpan() {
        ArrayList<BoundNode> children = new ArrayList<>();
        getChildren().forEachRemaining(children::add);

        TextSpan first = children.get(0).getSpan();
        TextSpan last = children.get(children.size() - 1).getSpan();
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
    static void prettyPrint(Writer writer, BoundNode node, String indent, boolean isLast) throws IOException {
        String marker = isLast ? "└──" : "├──";

        writer.write(indent);
        writer.write(marker);

        writeNode(writer, node);

        writer.write("\n");

        indent += isLast ? "    ": "│   ";

        Iterator<BoundNode> iterator = node.getChildren();
        while (iterator.hasNext()) {
            BoundNode child = iterator.next();
            boolean last = !iterator.hasNext();
            prettyPrint(writer, child, indent, last);
        }
    }

    private static void writeNode(Writer writer, BoundNode node) throws IOException {
        String text = getTextRepresentation(node);
        writer.write(text);
    }

    private static String getTextRepresentation(BoundNode node) {
        if (node instanceof BoundBinaryExpression b) {
            return b.getOperator().getType().toString() + "Expression";
        }
        if (node instanceof BoundUnaryExpression u) {
            return u.getOperator().getType().toString() + "Expression";
        }
        return node.getType().toString();
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