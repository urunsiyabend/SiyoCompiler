package codeanalysis.syntax;

import codeanalysis.syntax.SyntaxPriorities;
import codeanalysis.syntax.SyntaxRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ParserTest {

    @ParameterizedTest
    @MethodSource("getBinaryOperatorPairsData")
    public void parserBinaryExpressionHonorsPrecedences(SyntaxType op1, SyntaxType op2) {
        int op1Precedence = SyntaxPriorities.getBinaryOperatorPriority(op1);
        int op2Precedence = SyntaxPriorities.getBinaryOperatorPriority(op2);
        String op1Text = SyntaxRules.getTextData(op1);
        String op2Text = SyntaxRules.getTextData(op2);
        String text = "a " + op1Text + " b " + op2Text;
        ExpressionSyntax expression = SyntaxTree.parse(text).getRoot();

        if (op1Precedence >= op2Precedence) {
            //     op2
            //    /   \
            //   op1   c
            //  /   \
            // a     b

            AssertingEnumerator e = new AssertingEnumerator(expression);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(op1, op1Text);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "b");
            e.assertToken(op2, op2Text);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "c");
        } else {
            //   op1
            //  /   \
            // a    op2
            //     /   \
            //    b     c

            AssertingEnumerator e = new AssertingEnumerator(expression);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(op1, op1Text);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "b");
            e.assertToken(op2, op2Text);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "c");
        }
    }

    @ParameterizedTest
    @MethodSource("getUnaryOperatorPairsData")
    public void parserUnaryExpressionHonorsPrecedences(SyntaxType unaryKind, SyntaxType binaryKind) {
        int unaryPrecedence = SyntaxPriorities.getUnaryOperatorPriority(unaryKind);
        int binaryPrecedence = SyntaxPriorities.getBinaryOperatorPriority(binaryKind);
        String unaryText = SyntaxRules.getTextData(unaryKind);
        String binaryText = SyntaxRules.getTextData(binaryKind);
        String text = unaryText + " a " + binaryText + " b";
        ExpressionSyntax expression = SyntaxTree.parse(text).getRoot();

        if (unaryPrecedence >= binaryPrecedence) {
            //   binary
            //   /    \
            // unary   b
            //   |
            //   a

            AssertingEnumerator e = new AssertingEnumerator(expression);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.UnaryExpression);
            e.assertToken(unaryKind, unaryText);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(binaryKind, binaryText);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "b");
        } else {
            //  unary
            //    |
            //  binary
            //  /   \
            // a     b

            AssertingEnumerator e = new AssertingEnumerator(expression);
            e.assertNode(SyntaxType.UnaryExpression);
            e.assertToken(unaryKind, unaryText);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(binaryKind, binaryText);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "b");
        }
    }

    private static Stream<Arguments> getBinaryOperatorPairsData() {
        ArrayList<Arguments> pairs = new ArrayList<>();
        for (SyntaxType op1 : SyntaxRules.getBinaryOperatorTypes()) {
            for (SyntaxType op2 : SyntaxRules.getBinaryOperatorTypes()) {
                pairs.add(arguments(op1, op2));
            }
        }
        return pairs.stream();
    }

    private static Stream<Arguments> getUnaryOperatorPairsData() {
        ArrayList<Arguments> pairs = new ArrayList<>();
        for (SyntaxType unary : SyntaxRules.getUnaryOperatorTypes()) {
            for (SyntaxType binary : SyntaxRules.getBinaryOperatorTypes()) {
                pairs.add(arguments(unary, binary));
            }
        }
        return pairs.stream();
    }
}
