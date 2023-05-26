package codeanalysis.syntax;

import codeanalysis.DiagnosticBox;
import codeanalysis.text.SourceText;

import java.util.ArrayList;

/**
 * Represents a syntax tree in the code analysis system.
 * It consists of an expression syntax node as the root and an end-of-file token.
 * The syntax tree provides access to diagnostics and allows parsing of source code text.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class SyntaxTree {
    private final ExpressionSyntax _root;
    private final SyntaxToken _eofToken;
    private final DiagnosticBox _diagnostics;
    private final SourceText _text;

    /**
     * Initializes a new instance of the SyntaxTree class with the specified diagnostics, root expression syntax, and end-of-file token.
     *
     * @param text        The source text of the syntax tree.
     * @param diagnostics The list of diagnostics produced during parsing.
     * @param root        The root expression syntax node of the syntax tree.
     * @param EOFToken    The end-of-file token indicating the completion of parsing.
     */
    public SyntaxTree (SourceText text, DiagnosticBox diagnostics, ExpressionSyntax root, SyntaxToken EOFToken) {
        _text = text;
        _diagnostics = diagnostics;
        _root = root;
        _eofToken = EOFToken;
    }

    /**
     * Retrieves an iterator over the diagnostics produced during parsing.
     *
     * @return An iterator over the diagnostics.
     */
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Gets the root expression syntax node of the syntax tree.
     *
     * @return The root expression syntax node.
     */
    public ExpressionSyntax getRoot() {
        return _root;
    }


    /**
     * Gets the source text of the syntax tree.
     *
     * @return The source text.
     */
    public SourceText getText() {
        return _text;
    }

    /**
     * Parses the specified source code text and returns the corresponding syntax tree.
     *
     * @param text The source code text to be parsed.
     * @return The syntax tree representing the parsed source code.
     */
    public static SyntaxTree parse(String text) {
        SourceText sourceText = SourceText.from(text);
        return parse(sourceText);
    }

    /**
     * Parses the specified source code text and returns the corresponding syntax tree.
     *
     * @param text The source code text to be parsed.
     * @return The syntax tree representing the parsed source code.
     */
    public static SyntaxTree parse(SourceText text) {
        Parser parser = new Parser(text);
        return parser.parse();
    }

    /**
     * Parses the specified source code text and returns the corresponding tokens.
     *
     * @param text The text to be parsed.
     * @return The tokens representing the parsed source code.
     */
    public static Iterable<SyntaxToken> parseTokens(String text) {
        SourceText sourceText = SourceText.from(text);
        return parseTokens(sourceText);
    }

    /**
     * Parses the specified source code text and returns the corresponding tokens.
     *
     * @param text The source code text to be parsed.
     * @return The tokens representing the parsed source code.
     */
    public static Iterable<SyntaxToken> parseTokens(SourceText text) {
        Lexer lexer = new Lexer(text);
        ArrayList<SyntaxToken> tokens = new ArrayList<>();

        while (true) {
            SyntaxToken token = lexer.getNextToken();
            if (token.getType() == SyntaxType.EOFToken)
                break;
            tokens.add(token);
        }

        return tokens;
    }
}
