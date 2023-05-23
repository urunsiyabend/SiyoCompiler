# com.urunsiyabend.codeanalysis.binding

This package contains classes related to code analysis in the `com.urunsiyabend.codeanalysis.binding` namespace.

## Files

- `Binder`: Binds the syntax tree nodes to create bound expressions.
- `BoundBinaryExpression`: Represents a bound binary expression in the code analysis process.
- `BoundBinaryOperatorType`: Represents the types of binary operators in the code analysis process.
- `BoundExpression`: Represents a bound expression in the code analysis process.
- `BoundLiteralExpression`: Represents a bound literal expression in the code analysis process.
- `BoundNode`: Represents a bound node in the code analysis process.
- `BoundNodeType`: Represents the types of bound nodes in the code analysis process.
- `BoundUnaryExpression`: Represents a bound unary expression in the code analysis process.
- `BoundUnaryOperatorType`: Represents the types of unary operators in the code analysis process.

## Usage

To utilize the code analysis functionality, follow these steps:

1. Create an instance of `Binder` with the appropriate syntax tree.
2. Use the `bindExpression()` method of `Binder` to bind the expressions in the syntax tree.
3. Optionally, iterate over the diagnostics using the `diagnostics()` method of `Binder` to check for any binding errors.
4. Access the bound expressions and their properties to perform further analysis or evaluation.

For more details on the classes and their usage, refer to the individual class documentation.

