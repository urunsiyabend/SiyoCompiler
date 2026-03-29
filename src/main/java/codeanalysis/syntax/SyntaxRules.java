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
            case "imut" -> SyntaxType.ImmutableKeyword;
            case "mut" -> SyntaxType.MutableKeyword;
            case "if" -> SyntaxType.IfKeyword;
            case "else" -> SyntaxType.ElseKeyword;
            case "while" -> SyntaxType.WhileKeyword;
            case "for" -> SyntaxType.ForKeyword;
            case "fn" -> SyntaxType.FnKeyword;
            case "return" -> SyntaxType.ReturnKeyword;
            case "as" -> SyntaxType.AsKeyword;
            case "impl" -> SyntaxType.ImplKeyword;
            case "self" -> SyntaxType.SelfKeyword;
            case "scope" -> SyntaxType.ScopeKeyword;
            case "spawn" -> SyntaxType.SpawnKeyword;
            case "actor" -> SyntaxType.ActorKeyword;
            case "break" -> SyntaxType.BreakKeyword;
            case "continue" -> SyntaxType.ContinueKeyword;
            case "struct" -> SyntaxType.StructKeyword;
            case "enum" -> SyntaxType.EnumKeyword;
            case "import" -> SyntaxType.ImportKeyword;
            case "java" -> SyntaxType.JavaKeyword;
            case "new" -> SyntaxType.NewKeyword;
            case "try" -> SyntaxType.TryKeyword;
            case "catch" -> SyntaxType.CatchKeyword;
            case "in" -> SyntaxType.InKeyword;
            case "null" -> SyntaxType.NullKeyword;
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
            case PercentToken -> "%";
            case BangToken -> "!";
            case TildeToken -> "~";
            case CaretToken -> "^";
            case AmpersandToken -> "&";
            case PipeToken -> "|";
            case DoubleLessToken -> "<<";
            case DoubleGreaterToken -> ">>";
            case EqualsToken -> "=";
            case LessToken -> "<";
            case LessOrEqualsToken -> "<=";
            case GreaterToken -> ">";
            case GreaterOrEqualsToken -> ">=";
            case DoubleAmpersandToken -> "&&";
            case DoublePipeToken -> "||";
            case EqualsEqualsToken -> "==";
            case BangEqualsToken -> "!=";
            case OpenParenthesisToken -> "(";
            case CloseParenthesisToken -> ")";
            case OpenBraceToken -> "{";
            case CloseBraceToken -> "}";
            case IfKeyword -> "if";
            case ElseKeyword -> "else";
            case FalseKeyword -> "false";
            case TrueKeyword -> "true";
            case ImmutableKeyword -> "imut";
            case MutableKeyword -> "mut";
            case WhileKeyword -> "while";
            case ForKeyword -> "for";
            case FnKeyword -> "fn";
            case ReturnKeyword -> "return";
            case AsKeyword -> "as";
            case ImplKeyword -> "impl";
            case SelfKeyword -> "self";
            case ScopeKeyword -> "scope";
            case SpawnKeyword -> "spawn";
            case ActorKeyword -> "actor";
            case ColonToken -> ":";
            case CommaToken -> ",";
            case ArrowToken -> "->";
            case BreakKeyword -> "break";
            case ContinueKeyword -> "continue";
            case StructKeyword -> "struct";
            case EnumKeyword -> "enum";
            case TryKeyword -> "try";
            case CatchKeyword -> "catch";
            case InKeyword -> "in";
            case NullKeyword -> "null";
            case OpenBracketToken -> "[";
            case CloseBracketToken -> "]";
            case DotToken -> ".";
            case DoubleDotToken -> "..";
            case PlusEqualsToken -> "+=";
            case MinusEqualsToken -> "-=";
            case AsteriskEqualsToken -> "*=";
            case SlashEqualsToken -> "/=";
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
