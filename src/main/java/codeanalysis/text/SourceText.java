package codeanalysis.text;

import java.util.ArrayList;

/**
 * Represents source code to be compiled.
 * It consists of a list of text lines and the entire text.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SourceText {
    private final ArrayList<TextLine> _lines;
    private final String _text;

    /**
     * Initializes a new instance of the SourceText class with the specified text.
     *
     * @param text The text of the source code.
     */
    public SourceText(String text) {
        _text = text;
        _lines = parseLines(this, text);
    }


    /**
     * Retrieves line index of the character at the specified position.
     *
     * @param position The position of the character.
     * @return The line index.
     */
    public int getLineIndex(int position) {
        int lower = 0;
        int upper = _lines.size() - 1;

        while (lower <= upper) {
            var index = lower + (upper - lower) / 2;
            var start = _lines.get(index).getStart();

            if (position == start) {
                return index;
            }
            else if (start > position) {
                upper = index - 1;
            }
            else {
                lower = index + 1;
            }
        }

        return lower - 1;
    }

    /**
     * Parses source code into lines and returns the list of lines.
     *
     * @param sourceText The source text.
     * @param text The text of the source code.
     * @return The list of text lines.
     */
    private ArrayList<TextLine> parseLines(SourceText sourceText, String text) {
        ArrayList<TextLine> result = new ArrayList<>();
        int lineStart = 0;
        int position = 0;
        while (position < text.length()) {
            int lineBreakWidth = getLineBreakWidth(text, position);
            if (lineBreakWidth == 0) {
                position++;
            }
            else {
                addLine(result, sourceText, position, lineStart, lineBreakWidth);

                position += lineBreakWidth;
                lineStart = position;
            }
        }

        if (position >= lineStart) {
            addLine(result, sourceText, position, lineStart, 0);
        }
        return result;
    }

    /**
     * Retrieves the character at the specified position.
     *
     * @param position The position of the character.
     * @return The character.
     */
    public char charAt(int position) {
        return _text.charAt(position);
    }

    /**
     * Retrieves the character length of the source code.
     *
     * @return The character length.
     */
    public int length() {
        return _text.length();
    }

    /**
     * Adds line to the list of lines.
     *
     * @param result The reference of list to the instantiated line to.
     * @param sourceText The source text.
     * @param position The position of the character.
     * @param lineStart The start position of the line.
     * @param lineBreakWidth The width of the line break.
     */
    private static void addLine(ArrayList<TextLine> result, SourceText sourceText, int position, int lineStart, int lineBreakWidth) {
        var lineLength = position - lineStart;
        var lineLengthIncludingBreak = lineLength + lineBreakWidth;
        var line = new TextLine(sourceText, lineStart, lineLength, lineLengthIncludingBreak);
        result.add(line);
    }

    /**
     * Creates a new instance of the SourceText class with the specified text.
     *
     * @param text The text of the source code.
     * @return The new instance of the SourceText class.
     */
    public static SourceText from(String text) {
        return new SourceText(text);
    }

    /**
     * Retrieves the width of the line break.
     *
     * @param text The text of the source code.
     * @param i The position of the character.
     * @return The width of the line break.
     */
    private int getLineBreakWidth(String text, int i) {
        char c = text.charAt(i);
        char l = i + 1 >= text.length() ? '\0' : text.charAt(i + 1);

        if (c == '\r' && l == '\n') {
            return 2;
        }
        else if (c == '\r' || c == '\n') {
            return 1;
        }
        return 0;
    }

    /**
     * Retrieves the list of text lines.
     * The list is created by parsing the source code.
     *
     * @return The list of text lines.
     */
    public ArrayList<TextLine> getLines() {
        return _lines;
    }

    /**
     * Retrieves the string representation of the source code.
     *
     * @return The entire text.
     */
    @Override
    public String toString() {
        return _text;
    }

    /**
     * Retrieves the string representation of the source code between the specified positions.
     *
     * @param start The start position of the text.
     * @param end The end position of the text.
     * @return The text between the specified positions.
     */
    public String toString(int start, int end) {
        return _text.substring(start, end);
    }

    /**
     * Retrieves string representation of the source code between the specified text spans.
     *
     * @param span The text span.
     * @return The text between the specified text spans.
     */
    public String toString(TextSpan span) {
        return toString(span.getStart(), span.getStart() + span.getLength());
    }
}
