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
    private final CompilationUnitSyntax _root;
    private final DiagnosticBox _diagnostics;
    private final SourceText _text;

    /**
     * Initializes a new instance of the SyntaxTree class with the specified diagnostics, root expression syntax, and end-of-file token.
     *
     * @param text        The source text of the syntax tree.
     */
    private SyntaxTree (SourceText text) {
        Parser parser = new Parser(text);


        _text = text;
        _diagnostics = parser.getDiagnostics();
        _root = parser.parseCompilationUnit();
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
    public CompilationUnitSyntax getRoot() {
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
        return new SyntaxTree(text);
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
