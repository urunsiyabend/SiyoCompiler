package codeanalysis.text;

/**
 * The TextLine class represents a line of text in a source file.
 * It defines the start position, length, and end position of the line.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class TextLine {
    private final SourceText _text;
    private final int _start;
    private final int _length;
    private final int _lengthIncludingLineBreak;

    /**
     * Creates a new instance of the TextLine class with the specified start position, length, and length including line break.
     *
     * @param text The source text.
     * @param start The start position of the text line.
     * @param length The length of the text line.
     * @param lengthIncludingLineBreak The length of the text line including line break.
     */
    public TextLine(SourceText text, int start, int length, int lengthIncludingLineBreak) {
        _text = text;
        _start = start;
        _length = length;
        _lengthIncludingLineBreak = lengthIncludingLineBreak;
    }

    /**
     * Retrieves the source text.
     *
     * @return The source text.
     */
    public SourceText getText() {
        return _text;
    }

    /**
     * Retrieves the start position of the text line.
     *
     * @return The start position.
     */
    public int getStart() {
        return _start;
    }

    /**
     * Retrieves the length of the text line.
     *
     * @return The length.
     */
    public int getLength() {
        return _length;
    }

    /**
     * Retrieves the end position of the text line.
     *
     * @return The end position.
     */
    public int getEnd() {
        return _start + _length;
    }

    /**
     * Retrieves the length of the text line including line break.
     *
     * @return The length including line break.
     */
    public int getLengthIncludingLineBreak() {
        return _lengthIncludingLineBreak;
    }

    /**
     * Retrieves the span of the text line.
     *
     * @return The span.
     */
    public TextSpan getSpan() {
        return new TextSpan(_start, _length);
    }

    /**
     * Retrieves the span of the text line including line break.
     *
     * @return The span including line break.
     */
    public TextSpan getSpanIncludingLineBreak() {
        return new TextSpan(_start, _lengthIncludingLineBreak);
    }

    /**
     * Retrieves the string representation of the text line.
     *
     * @return The text.
     */
    @Override
    public String toString() {
        return _text.toString(getSpan());
    }
}
