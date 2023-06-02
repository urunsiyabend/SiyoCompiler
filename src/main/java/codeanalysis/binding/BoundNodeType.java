package codeanalysis.binding;


/**
 * Represents the types of bound nodes in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public enum BoundNodeType {
    LiteralExpression,
    AssignmentExpression,
    VariableExpression,
    UnaryExpression,
    BinaryExpression,
    BlockStatement,
    ExpressionStatement,
    VariableDeclaration,
    IfStatement,
    WhileStatement,
    ForStatement,
    LabelStatement,
    GotoStatement,
    ConditionalGotoStatement,
}
