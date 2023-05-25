package codeanalysis.syntax;

import java.util.ArrayList;

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

    /**
     * Gets the text data based on syntax type.
     *
     * @param type The keyword text.
     * @return The text data corresponding to the syntax type.
     */
    public static String getTextData(SyntaxType type)
    {
        return switch (type) {
            case PlusToken -> "+";
            case MinusToken -> "-";
            case AsteriskToken -> "*";
            case SlashToken -> "/";
            case BangToken -> "!";
            case EqualsToken -> "=";
            case DoubleAmpersandToken -> "&&";
            case DoublePipeToken -> "||";
            case EqualsEqualsToken -> "==";
            case BangEqualsToken -> "!=";
            case OpenParenthesisToken -> "(";
            case CloseParenthesisToken -> ")";
            case FalseKeyword -> "false";
            case TrueKeyword -> "true";
            default -> null;
        };
    }

    /**
     * Returns the all unary operator types.
     *
     * @return All unary operator types that defined.
     */
    public static ArrayList<SyntaxType> getUnaryOperatorTypes() {
        ArrayList<SyntaxType> operatorTypes = new ArrayList<>();

        for (SyntaxType type : SyntaxType.values()) {
            if (SyntaxPriorities.getUnaryOperatorPriority(type) > 0) {
                operatorTypes.add(type);
            }
        }

        return operatorTypes;
    }

    /**
     * Returns the all binary operator types.
     *
     * @return All binary operator types that defined.
     */
    public static ArrayList<SyntaxType> getBinaryOperatorTypes() {
        ArrayList<SyntaxType> operatorTypes = new ArrayList<>();

        for (SyntaxType type : SyntaxType.values()) {
            if (SyntaxPriorities.getBinaryOperatorPriority(type) > 0) {
                operatorTypes.add(type);
            }
        }

        return operatorTypes;
    }
}
