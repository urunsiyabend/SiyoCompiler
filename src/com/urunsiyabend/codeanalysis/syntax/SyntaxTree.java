package com.urunsiyabend.codeanalysis.syntax;

import com.urunsiyabend.codeanalysis.DiagnosticBox;
import jdk.jshell.Diag;

import java.util.ArrayList;
import java.util.Iterator;

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

    /**
     * Initializes a new instance of the SyntaxTree class with the specified diagnostics, root expression syntax, and end-of-file token.
     *
     * @param diagnostics The list of diagnostics produced during parsing.
     * @param root        The root expression syntax node of the syntax tree.
     * @param EOFToken    The end-of-file token indicating the completion of parsing.
     */
    public SyntaxTree (DiagnosticBox diagnostics, ExpressionSyntax root, SyntaxToken EOFToken) {
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
     * Parses the specified source code text and returns the corresponding syntax tree.
     *
     * @param text The source code text to be parsed.
     * @return The syntax tree representing the parsed source code.
     */
    public static SyntaxTree parse(String text) {
        Parser parser = new Parser(text);
        return parser.parse();
    }

    /**
     * Parses the specified source code text and returns the corresponding tokens.
     *
     * @param text The source code text to be parsed.
     * @return The tokens representing the parsed source code.
     */
    public static Iterable<SyntaxToken> parseTokens(String text) {
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
