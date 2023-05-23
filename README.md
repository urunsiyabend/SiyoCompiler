<img src="src/com/urunsiyabend/SiyoLanguage.png" alt= “” width="200" height="200">
<hr>

# Handwritten Compiler with Java for the Siyo Programming Language

Welcome to the Siyo Compiler Project! This project aims to develop a compiler for the Siyo language.

## Overview

The Siyo Compiler Project is designed to provide a comprehensive compiler implementation for the Siyo programming language. It encompasses various components, including lexical analysis, parsing, syntax tree generation, semantic analysis, code generation, and more.

## Features

- Lexical analysis: The `Lexer` class performs lexical analysis on Siyo source code, generating tokens representing the language's lexical elements.
- Parsing: The `Parser` class parses the Siyo source code and generates a syntax tree representation of the program's structure.
- Syntax tree: The `SyntaxTree`, `SyntaxNode`, and related classes provide a hierarchical representation of the Siyo program's syntax.
- Semantic analysis: The compiler performs semantic analysis on the syntax tree to enforce language-specific rules and detect potential errors.
- Code generation: The compiler generates executable code or intermediate representation (e.g., bytecode) from the syntax tree.
- Diagnostics: The compiler reports diagnostics such as parsing errors, lexical errors, and semantic errors.

## Project Structure

The project will have following components:

- `com.urunsiyabend.codeanalysis`: This package contains classes for code analysis, including the lexer, parser, syntax tree, syntax nodes, and token types.
- `com.urunsiyabend.codegeneration (soon!)`: This package contains classes for code generation, including code emitter and bytecode generation (if applicable).
- `com.urunsiyabend.semanticanalysis (soon!)`: This package contains classes for semantic analysis, including type checking, symbol table management, and error reporting.
- `com.urunsiyabend.util (soon!)`: This package contains utility classes used throughout the project.

## Usage

To use the Siyo Compiler, follow these steps:

1. Create a new instance of `Lexer` and pass the Siyo source code as input.
2. Use the `getNextToken()` method of `Lexer` to retrieve tokens from the input source code.
3. Pass the tokens to an instance of `Parser` to generate the syntax tree.
4. Perform semantic analysis on the syntax tree using the appropriate classes and algorithms.
5. Generate executable code or intermediate representation using the code generation component.
6. Execute or further process the generated code.

Refer to the individual classes and their documentation for more detailed usage instructions.
