package codeanalysis.binding;

import codeanalysis.syntax.SyntaxType;

/**
 * The BoundUnaryOperator class represents a unary operator used in binding expressions.
 * It encapsulates information about the syntax type, operator type, operand type, and result type of the operator.
 * The class provides methods to bind a unary operator based on the syntax type and operand type, and to retrieve the properties of an operator.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundUnaryOperator {
    private final SyntaxType _syntaxType;
    private final BoundUnaryOperatorType _type;
    private final Class<?> _operandType;
    private final Class<?> _resultType;


    private static final BoundUnaryOperator[] _operators = {
            new BoundUnaryOperator(SyntaxType.BangToken, BoundUnaryOperatorType.LogicalNegation, Boolean.class),
            new BoundUnaryOperator(SyntaxType.PlusToken, BoundUnaryOperatorType.Identity, Integer.class),
            new BoundUnaryOperator(SyntaxType.MinusToken, BoundUnaryOperatorType.Negation, Integer.class),
            new BoundUnaryOperator(SyntaxType.TildeToken, BoundUnaryOperatorType.OnesComplement, Integer.class),
    };

    /**
     * Constructs a BoundUnaryOperator with the specified syntax type, operator type, and operand type.
     * The result type is set to be the same as the operand type.
     *
     * @param syntaxType   The syntax type of the operator.
     * @param type         The operator type.
     * @param operandType  The type of the operand.
     */
    private BoundUnaryOperator(SyntaxType syntaxType, BoundUnaryOperatorType type, Class<?> operandType) {

        _syntaxType = syntaxType;
        _type = type;
        _operandType = operandType;
        _resultType = operandType;
    }

    /**
     * Constructs a BoundUnaryOperator with the specified syntax type, operator type, operand type, and result type.
     *
     * @param syntaxType   The syntax type of the operator.
     * @param type         The operator type.
     * @param operandType  The type of the operand.
     * @param resultType   The result type of the operation.
     */
    private BoundUnaryOperator(SyntaxType syntaxType, BoundUnaryOperatorType type, Class<?> operandType, Class<?> resultType) {

        _syntaxType = syntaxType;
        _type = type;
        _operandType = operandType;
        _resultType = resultType;
    }

    /**
     * Binds a unary operator based on the syntax type and operand type.
     *
     * @param syntaxType   The syntax type of the operator.
     * @param operandType  The type of the operand.
     * @return The BoundUnaryOperator instance if found, or null if no matching operator is found.
     */
    public static BoundUnaryOperator bind(SyntaxType syntaxType, Class<?> operandType) {
        for (var op: _operators) {
            if (op.getSyntaxType() == syntaxType && op.getOperandType() == operandType) {
                return op;
            }
        }
        return null;
    }

    /**
     * Gets the syntax type of the operator.
     *
     * @return The syntax type.
     */
    public SyntaxType getSyntaxType() {
        return _syntaxType;
    }

    /**
     * Gets the operator type.
     *
     * @return The operator type.
     */
    public BoundUnaryOperatorType getType() {
        return _type;
    }


    /**
     * Gets the type of the operand.
     *
     * @return The operand type.
     */
    public Class<?> getOperandType() {
        return _operandType;
    }

    /**
     * Gets the result type of the operation.
     *
     * @return The result type.
     */
    public Class<?> getResultType() {
        return _resultType;
    }
}
