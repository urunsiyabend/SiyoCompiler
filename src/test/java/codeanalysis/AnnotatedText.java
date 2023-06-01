package codeanalysis;

import codeanalysis.text.TextSpan;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.Stack;

/**
 * The AnnotatedText class represents a string of text with annotations.
 * It allows parsing a string of text with annotations and retrieving the text and annotations separately.
 * It also provides a method for parsing a string of text with annotations.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
class AnnotatedText {
    private final String _text;
    private final ArrayList<TextSpan> _spans;

    /**
     * Creates a new instance of the AnnotatedText class with the specified text and spans.
     *
     * @param text  The text of the annotated text.
     * @param spans The spans of the annotated text.
     */
    public AnnotatedText(String text, ArrayList<TextSpan> spans) {
        _text = text;
        _spans = spans;
    }

    /**
     * Parses the specified text with annotations.
     * The text is expected to be in the format "text [span] text [span] text".
     * The method returns an AnnotatedText object with the parsed text and spans.
     * <p>
     * The method throws an IllegalArgumentException if the text is not in the correct format.
     * The method throws an IllegalArgumentException if there are too many or too few '[' or ']' characters.
     *
     * @param text The text to parse.
     */
    public static AnnotatedText parse(String text) {
        text = unIndent(text);
        StringBuilder textBuilder = new StringBuilder();
        ArrayList<TextSpan> spans = new ArrayList<>();
        Stack<Integer> startStack = new Stack<>();
        int position = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                startStack.push(position);
            } else if (c == ']') {
                if (startStack.size() == 0) {
                    throw new IllegalArgumentException("Too many ']' in text");
                }
                int start = startStack.pop();
                TextSpan span = TextSpan.fromBounds(start, position);
                spans.add(span);
            } else {
                position++;
                textBuilder.append(c);
            }
        }

        if (startStack.size() != 0) {
            throw new IllegalArgumentException("Missing ']' in text");
        }

        return new AnnotatedText(textBuilder.toString(), spans);
    }

    /**
     * Un indents the specified text.
     * The method returns the text with the minimum indentation of non-empty lines removed.
     *
     * @return The unindented text.
     */
    public static String unIndent(String text) {
        ArrayList<String> lines = unIndentLines(text);

        return String.join("\n", lines);
    }

    /**
     * Un indents the specified text.
     * The method returns the collection of lines with the minimum indentation of non-empty lines removed.
     *
     * @return The list of lines that is unindented.
     */
    public static ArrayList<String> unIndentLines(String text) {
        ArrayList<String> lines = new ArrayList<>();

        Scanner scanner = new Scanner(text);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            lines.add(line);
        }

        int minIndentation = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.trim().length() == 0) {
                lines.set(i, "");
                continue;
            }

            var indentation = l.length() - l.trim().length();
            minIndentation = Math.min(minIndentation, indentation);
        }

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() == 0) {
                continue;
            }
            lines.set(i, lines.get(i).substring(minIndentation));
        }

        while (lines.size() > 0 && lines.get(0).length() == 0) {
            lines.remove(0);
        }

        while (lines.size() > 0 && lines.get(lines.size() - 1).length() == 0) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * Retrieves the text of the annotated text.
     *
     * @return The text of the annotated text.
     */
    public String getText() {
        return _text;
    }

    /**
     * Retrieves the spans of the annotated text.
     *
     * @return The spans of the annotated text.
     */
    public ArrayList<TextSpan> getSpans() {
        return _spans;
    }
}