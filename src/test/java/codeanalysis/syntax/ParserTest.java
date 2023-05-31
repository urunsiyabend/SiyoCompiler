package codeanalysis.syntax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;


/**
 * The ParserTest class contains tests for the parser.
 * It ensures that the parser correctly parses the syntax tree.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ParserTest {


    /**
     * Test if binary operators are parsed correctly.
     *
     * @param op1 The first operator.
     * @param op2 The second operator.
     */
    @ParameterizedTest
    @MethodSource("getBinaryOperatorPairsData")
    public void parserBinaryExpressionHonorsPrecedences(SyntaxType op1, SyntaxType op2) {
        int op1Precedence = SyntaxPriorities.getBinaryOperatorPriority(op1);
        int op2Precedence = SyntaxPriorities.getBinaryOperatorPriority(op2);
        String op1Text = SyntaxRules.getTextData(op1);
        String op2Text = SyntaxRules.getTextData(op2);
        String text = "a " + op1Text + " b " + op2Text;
        StatementSyntax statementSyntax = SyntaxTree.parse(text).getRoot().getStatement();

        if (op1Precedence >= op2Precedence) {
            //     op2
            //    /   \
            //   op1   c
            //  /   \
            // a     b

            AssertingEnumerator e = new AssertingEnumerator(statementSyntax);
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

            AssertingEnumerator e = new AssertingEnumerator(statementSyntax);
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


    /**
     * Test if unary operators are parsed correctly.
     *
     * @param unaryType The syntax type of the unary operator.
     * @param binaryType The syntax type of the binary operator.
     */
    @ParameterizedTest
    @MethodSource("getUnaryOperatorPairsData")
    public void parserUnaryExpressionHonorsPrecedences(SyntaxType unaryType, SyntaxType binaryType) {
        int unaryPrecedence = SyntaxPriorities.getUnaryOperatorPriority(unaryType);
        int binaryPrecedence = SyntaxPriorities.getBinaryOperatorPriority(binaryType);
        String unaryText = SyntaxRules.getTextData(unaryType);
        String binaryText = SyntaxRules.getTextData(binaryType);
        String text = unaryText + " a " + binaryText + " b";
        StatementSyntax expression = SyntaxTree.parse(text).getRoot().getStatement();

        if (unaryPrecedence >= binaryPrecedence) {
            //   binary
            //   /    \
            // unary   b
            //   |
            //   a

            AssertingEnumerator e = new AssertingEnumerator(expression);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.UnaryExpression);
            e.assertToken(unaryType, unaryText);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(binaryType, binaryText);
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
            e.assertToken(unaryType, unaryText);
            e.assertNode(SyntaxType.BinaryExpression);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "a");
            e.assertToken(binaryType, binaryText);
            e.assertNode(SyntaxType.NameExpression);
            e.assertToken(SyntaxType.IdentifierToken, "b");
        }
    }

    /**
     * Provides test data for binary operators.
     *
     * @return A stream of arguments for the binary operator test.
     */
    private static Stream<Arguments> getBinaryOperatorPairsData() {
        ArrayList<Arguments> pairs = new ArrayList<>();
        for (SyntaxType op1 : SyntaxRules.getBinaryOperatorTypes()) {
            for (SyntaxType op2 : SyntaxRules.getBinaryOperatorTypes()) {
                pairs.add(arguments(op1, op2));
            }
        }
        return pairs.stream();
    }

    /**
     * Provides test data for unary operators.
     *
     * @return A stream of arguments for the unary operator test.
     */
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
