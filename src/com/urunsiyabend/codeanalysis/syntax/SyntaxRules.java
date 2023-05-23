package com.urunsiyabend.codeanalysis.syntax;

/**
 * The SyntaxRules class provides methods to determine the syntax type based on certain rules or keywords.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 *
 * */
public class SyntaxRules {

    /**
     * Gets the syntax type based on a keyword.
     *
     * @param text The keyword text.
     * @return The syntax type corresponding to the keyword.
     */
    public static SyntaxType getKeywordType(String text) {
        return switch (text) {
            case "true" -> SyntaxType.TrueKeyword;
            case "false" -> SyntaxType.FalseKeyword;
            default -> SyntaxType.IdentifierToken;
        };
    }
}
