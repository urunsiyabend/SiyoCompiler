package codeanalysis.syntax;

/**
 * The SyntaxPriorities class provides methods to retrieve the priority of binary and unary operators in the syntax analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SyntaxPriorities {
    /**
     * Retrieves the priority of a unary operator based on its syntax type.
     *
     * @param type The syntax type of the unary operator.
     * @return The priority of the unary operator.
     */
    public static int getUnaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken, BangToken, TildeToken -> 6;
            default -> 0;
        };
    }

    /**
     * Retrieves the priority of a binary operator based on its syntax type.
     *
     * @param type The syntax type of the binary operator.
     * @return The priority of the binary operator.
     */
    public static int getBinaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case AsteriskToken, SlashToken, PercentToken -> 5;
            case PlusToken, MinusToken -> 4;
            case EqualsEqualsToken, BangEqualsToken, LessToken, LessOrEqualsToken, GreaterToken, GreaterOrEqualsToken -> 3;
            case DoubleAmpersandToken, AmpersandToken, DoubleLessToken, DoubleGreaterToken -> 2;
            case DoublePipeToken, PipeToken, CaretToken -> 1;
            default -> 0;
        };
    }
}
