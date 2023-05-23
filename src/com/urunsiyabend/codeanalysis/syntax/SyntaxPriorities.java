package com.urunsiyabend.codeanalysis.syntax;

public class SyntaxPriorities {
    public static int GetBinaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken -> 1;
            case AsteriskToken, SlashToken -> 2;
            default -> 0;
        };
    }

    public static int GetUnaryOperatorPriority(SyntaxType type) {
        return switch (type) {
            case PlusToken, MinusToken -> 3;
            default -> 0;
        };
    }
}
