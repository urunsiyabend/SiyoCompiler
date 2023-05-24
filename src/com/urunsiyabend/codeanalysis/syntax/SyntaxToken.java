package com.urunsiyabend.codeanalysis.syntax;

import com.urunsiyabend.codeanalysis.TextSpan;
import org.w3c.dom.Text;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a token in the code analysis system.
 * It is a syntax node with a specific type, position, data, and value.
 * Tokens are the building blocks of a syntax tree.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SyntaxToken extends SyntaxNode {
    SyntaxType type;
    int position;
    String data;
    Object value;
    public TextSpan _span;

    /**
     * Initializes a new instance of the SyntaxToken class with the specified type, position, data, and value.
     *
     * @param type     The type of the token.
     * @param position The position of the token in the source code.
     * @param data     The textual representation of the token.
     * @param value    The value associated with the token.
     */
    public SyntaxToken(SyntaxType type, int position, String data, Object value) {
        this.type = type;
        this.position = position;
        this.data = data;
        this.value = value;
        int length = (data != null && !data.equals("\0")) ? data.length() : 0;
        _span = new TextSpan(position, length);
    }

    /**
     * Gets the type of the syntax token.
     *
     * @return The type of the token.
     */
    public SyntaxType getType() {
        return type;
    }

    /**
     * Gets an iterator over the child nodes of the syntax token.
     * Since a token is a leaf node, this iterator will be empty.
     *
     * @return An empty iterator.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.emptyIterator();
    }

    /**
     * Gets the position of the syntax token in the source code.
     *
     * @return The position of the token.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets the position of the syntax token in the source code.
     *
     * @param position The position of the token.
     */
    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * Gets the textual representation of the syntax token.
     *
     * @return The textual representation of the token.
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the textual representation of the syntax token.
     *
     * @param data The textual representation of the token.
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * Gets the value associated with the syntax token.
     *
     * @return The value associated with the token.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value associated with the syntax token.
     *
     * @param value The value associated with the token.
     */
    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * Gets the text span associated with the syntax token.
     *
     * @return The text span of related syntax token.
     */
    public TextSpan getSpan() {
        return _span;
    }

    /**
     * Returns the string representation of the syntax token.
     *
     * @return The string representation of the token.
     */
    @Override
    public String toString() {
        return String.format("%s : '%s'", type, data);
    }
}
