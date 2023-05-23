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
     * Retrieves the priority of a binary operator based on its syntax type.
     *
     * @param type The syntax type of the binary operator.
     * @return The priority of the binary operator.
     */
    public static int GetBinaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken -> 1;
            case AsteriskToken, SlashToken -> 2;
            default -> 0;
        };
    }

    /**
     * Retrieves the priority of a unary operator based on its syntax type.
     *
     * @param type The syntax type of the unary operator.
     * @return The priority of the unary operator.
     */
    public static int GetUnaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken -> 3;
            default -> 0;
        };
    }
}
