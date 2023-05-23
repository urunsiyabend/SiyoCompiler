package com.urunsiyabend.codeanalysis.syntax;

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
    public static int GetUnaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken, BangToken -> 5;
            default -> 0;
        };
    }

    /**
     * Retrieves the priority of a binary operator based on its syntax type.
     *
     * @param type The syntax type of the binary operator.
     * @return The priority of the binary operator.
     */
    public static int GetBinaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case AsteriskToken, SlashToken -> 4;
            case PlusToken, MinusToken -> 3;
            case DoubleAmpersandToken-> 2;
            case DoublePipeToken -> 1;
            default -> 0;
        };
    }
}
