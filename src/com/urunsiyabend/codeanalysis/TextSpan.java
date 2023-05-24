package com.urunsiyabend.codeanalysis;

/**
 * The TextSpan class represents a span of text in a source file.
 * It defines the start position, length, and end position of the span.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class TextSpan {
    private final int _start;
    private final int _length;

    /**
     * Creates a new instance of the TextSpan class with the specified start position and length.
     *
     * @param start  The start position of the text span.
     * @param length The length of the text span.
     */
    public TextSpan(int start, int length) {
        this._start = start;
        this._length = length;
    }

    /**
     * Retrieves the start position of the text span.
     *
     * @return The start position.
     */
    public int getStart() {
        return _start;
    }

    /**
     * Retrieves the length of the text span.
     *
     * @return The length.
     */
    public int getLength() {
        return _length;
    }

    /**
     * Retrieves the end position of the text span.
     *
     * @return The end position.
     */
    public int getEnd() {
        return getStart() + getLength();
    }
}
