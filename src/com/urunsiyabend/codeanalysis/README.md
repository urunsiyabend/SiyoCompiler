# com.urunsiyabend.codeanalysis

This package contains classes related to code analysis in the `com.urunsiyabend` namespace.

## Files

- `BinaryExpressionSyntax`: Represents a binary expression in the syntax tree.
- `Evaluator`: Evaluates the syntax tree and computes the result.
- `ExpressionSyntax`: Represents an expression in the syntax tree.
- `Lexer`: Performs lexical analysis on the input text and generates tokens.
- `LiteralExpressionSyntax`: Represents a literal expression in the syntax tree.
- `ParanthesizedExpressionSyntax`: Represents a parenthesized expression in the syntax tree.
- `Parser`: Parses the input text and generates the syntax tree.
- `README.md` (you are here): Provides an overview of the `com.urunsiyabend.codeanalysis` package.
- `SyntaxNode`: Represents a node in the syntax tree.
- `SyntaxToken`: Represents a token in the syntax tree.
- `SyntaxTree`: Represents the syntax tree generated from the input text.
- `SyntaxType`: Represents the type of a syntax token or syntax node.

## Usage

To utilize the code analysis functionality, follow these steps:

1. Create an instance of `Lexer` with the input text.
2. Use the `getNextToken()` method of `Lexer` to retrieve tokens from the input text.
3. Pass the tokens to an instance of `Parser` to generate the syntax tree.
4. Use the `getRoot()` method of `SyntaxTree` to access the root node of the syntax tree.
5. Optionally, iterate over the diagnostics using the `diagnostics()` method of `SyntaxTree` to check for any parsing errors.
6. To evaluate the syntax tree, create an instance of `Evaluator` with the root node of the syntax tree.
7. Use the `evaluate()` method of `Evaluator` to compute the result.

For more details on the classes and their usage, refer to the individual class documentation.