package test.codeanalysis.syntax;

import com.urunsiyabend.codeanalysis.syntax.SyntaxToken;
import com.urunsiyabend.codeanalysis.syntax.SyntaxTree;
import com.urunsiyabend.codeanalysis.syntax.SyntaxType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Iterator;

class LexerTest {
    @ParameterizedTest
    @MethodSource("getTokensData")
    public void Lexer_Lex_Tokens(SyntaxType type, String text) {
        ArrayList<SyntaxToken> tokens = (ArrayList<SyntaxToken>) SyntaxTree.parseTokens(text);

        Assertions.assertEquals(1, tokens.size());
        SyntaxToken token = tokens.get(0);
        Assertions.assertEquals(type, token.getType());
        Assertions.assertEquals(text, token.getData());
    }

    @ParameterizedTest
    @MethodSource("getTokenPairsData")
    public void Lexer_Lex_Token_Pairs(SyntaxType t1Type, String t1Text, SyntaxType t2Type, String t2Text) {
        String text = t1Text + t2Text;
        ArrayList<SyntaxToken> tokens = (ArrayList<SyntaxToken>) SyntaxTree.parseTokens(text);

        Assertions.assertEquals(2, tokens.size());

        SyntaxToken token1 = tokens.get(0);
        SyntaxToken token2 = tokens.get(1);
        Assertions.assertEquals(token1.getType(), t1Type);
        Assertions.assertEquals(token1.getData(), t1Text);
        Assertions.assertEquals(token2.getType(), t2Type);
        Assertions.assertEquals(token2.getData(), t2Text);
    }

    public static Iterator<Object[]> getTokensData() {
        return new Iterator<Object[]>() {
            private final Iterator<Token> iterator = getTokens().iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object[] next() {
                Token token = iterator.next();
                return new Object[]{token.getType(), token.getText()};
            }
        };
    }

    public static Iterator<Object[]> getTokenPairsData() {
        return new Iterator<Object[]>() {
            private final Iterator<TokenPair> iterator = getTokenPairs().iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object[] next() {
                TokenPair tokenPair = iterator.next();
                return new Object[]{tokenPair.getT1Type(), tokenPair.getT1Text(), tokenPair.getT2Type(), tokenPair.getT2Text()};
            }
        };
    }

    private static boolean requiresSeparator(SyntaxType t1Type, SyntaxType t2Type)
    {
        var t1IsKeyword = t1Type.toString().endsWith("Keyword");
        var t2IsKeyword = t2Type.toString().endsWith("Keyword");

        if (t1Type == SyntaxType.IdentifierToken && t2Type == SyntaxType.IdentifierToken)
            return true;

        if (t1IsKeyword && t2IsKeyword)
            return true;

        if (t1IsKeyword && t2Type == SyntaxType.IdentifierToken)
            return true;

        if (t1Type == SyntaxType.IdentifierToken && t2IsKeyword)
            return true;

        if (t1Type == SyntaxType.IdentifierToken && t2Type == SyntaxType.NumberToken)
            return true;

        if (t1IsKeyword && t2Type == SyntaxType.NumberToken)
            return true;

        if (t1Type == SyntaxType.NumberToken && t2Type == SyntaxType.NumberToken)
            return true;

        if (t1Type == SyntaxType.BangToken && t2Type == SyntaxType.EqualsToken)
            return true;

        if (t1Type == SyntaxType.BangToken && t2Type == SyntaxType.EqualsEqualsToken)
            return true;

        if (t1Type == SyntaxType.EqualsToken && t2Type == SyntaxType.EqualsToken)
            return true;

        if (t1Type == SyntaxType.EqualsToken && t2Type == SyntaxType.EqualsEqualsToken)
            return true;

        if (t1Type == SyntaxType.PlusToken && t2Type == SyntaxType.EqualsToken)
            return true;

        if (t1Type == SyntaxType.PlusToken && t2Type == SyntaxType.EqualsEqualsToken)
            return true;

        if (t1Type == SyntaxType.MinusToken && t2Type == SyntaxType.EqualsToken)
            return true;

        if (t1Type == SyntaxType.MinusToken && t2Type == SyntaxType.EqualsEqualsToken)
            return true;

        if (t1Type == SyntaxType.SlashToken && t2Type == SyntaxType.EqualsToken)
            return true;

        if (t1Type == SyntaxType.SlashToken && t2Type == SyntaxType.EqualsEqualsToken)
            return true;

        if (t1Type == SyntaxType.SlashToken && t2Type == SyntaxType.SlashToken)
            return true;

        if (t1Type == SyntaxType.SlashToken && t2Type == SyntaxType.AsteriskToken)
            return true;

        return false;
    }


    public static Iterable<Token> getTokens() {
        ArrayList<Token> tokens = new ArrayList<>();
        tokens.add(new Token(SyntaxType.NumberToken, "1"));
        tokens.add(new Token(SyntaxType.NumberToken, "123"));
        tokens.add(new Token(SyntaxType.IdentifierToken, "a"));
        tokens.add(new Token(SyntaxType.IdentifierToken, "abc"));
        tokens.add(new Token(SyntaxType.PlusToken, "+"));
        tokens.add(new Token(SyntaxType.MinusToken, "-"));
        tokens.add(new Token(SyntaxType.AsteriskToken, "*"));
        tokens.add(new Token(SyntaxType.SlashToken, "/"));
        tokens.add(new Token(SyntaxType.OpenParenthesisToken, "("));
        tokens.add(new Token(SyntaxType.CloseParenthesisToken, ")"));
        tokens.add(new Token(SyntaxType.TrueKeyword, "true"));
        tokens.add(new Token(SyntaxType.FalseKeyword, "false"));
        tokens.add(new Token(SyntaxType.BangToken, "!"));
        tokens.add(new Token(SyntaxType.DoubleAmpersandToken, "&&"));
        tokens.add(new Token(SyntaxType.DoublePipeToken, "||"));
        tokens.add(new Token(SyntaxType.EqualsEqualsToken, "=="));
        tokens.add(new Token(SyntaxType.BangEqualsToken, "!="));
        return tokens;
    }

    public static Iterable<TokenPair> getTokenPairs() {
        ArrayList<TokenPair> tokenPairs = new ArrayList<>();
        Iterable<Token> tokens = getTokens();

        for (Token t1 : tokens) {
            for (Token t2 : tokens) {
                if (!requiresSeparator(t1.getType(), t2.getType())) {
                    TokenPair tokenPair = new TokenPair(t1.getType(), t1.getText(), t2.getType(), t2.getText());
                    tokenPairs.add(tokenPair);
                }
            }
        }
        return tokenPairs;
    }

    protected static class Token {
        private final SyntaxType _type;
        private final String _text;

        public Token(SyntaxType type, String text) {
            _type = type;
            _text = text;
        }

        public SyntaxType getType() {
            return _type;
        }

        public String getText() {
            return _text;
        }
    }

    public static class TokenPair {
        private final SyntaxType _t1Type;
        private final String _t1Text;
        private final SyntaxType _t2Type;
        private final String _t2Text;

        public TokenPair(SyntaxType t1Kind, String t1Text, SyntaxType t2Kind, String t2Text) {
            _t1Type = t1Kind;
            _t1Text = t1Text;
            _t2Type = t2Kind;
            _t2Text = t2Text;
        }

        public SyntaxType getT1Type() {
            return _t1Type;
        }

        public String getT1Text() {
            return _t1Text;
        }

        public SyntaxType getT2Type() {
            return _t2Type;
        }

        public String getT2Text() {
            return _t2Text;
        }
    }
}