# Siyo Compiler

<p align="center">
  <img src="src/main/java/SiyoLanguage.png" alt="Siyo logo" width="180" />
</p>

Siyo Compiler is an experimental programming language and compiler project written in Java. The repository has grown well beyond a lexer/parser prototype: it now includes an interpreter, a JVM bytecode compiler, a REPL, file-based module loading, structs, enums, arrays, and a large automated test suite.

## Current Status

- Interpreter pipeline: `parse -> bind -> lower -> evaluate`
- JVM bytecode compiler: compiles `.siyo` files into `.class` files
- REPL for interactive execution and diagnostics
- Functions with typed parameters, return types, implicit return, recursion, and forward declarations
- Types: `int`, `bool`, `float`, `string`, `null`
- Composite types: arrays, structs, enums
- Control flow: `if/else`, `while`, classic `for`, `for in`, `break`, `continue`
- Error handling: `try { ... } catch e { ... }`
- File-based modules with `import "module"`
- Built-in functions: `len`, `toString`, `parseInt`, `parseFloat`, `toInt`, `toFloat`, `print`, `println`, `input`, `range`, `push`, `substring`, `contains`, `error`

## Example Siyo Program

```siyo
import "math"

fn greet(name: string) -> string {
    "Hello, " + name + "!"
}

struct Point {
    x: int,
    y: int
}

mut p = Point { x: 3, y: 4 }
mut nums = [1, 2, 3, 4, 5]
mut sum = 0

for n in nums {s
    sum += n
}

try {
    println(greet("World"))
    println("sum = " + toString(sum))
    println("abs(-42) = " + toString(math.abs(-42)))
} catch e {
    println("error: " + e)
}
```

## Project Structure

- `src/main/java/Main.java`: CLI entry point, REPL, file execution, and compile flow
- `src/main/java/codeanalysis/syntax`: lexer, parser, and syntax tree nodes
- `src/main/java/codeanalysis/binding`: semantic binding, scope management, and type checking
- `src/main/java/codeanalysis/lowering`: lowers high-level constructs into simpler bound nodes
- `src/main/java/codeanalysis/Evaluator.java`: interpreter runtime
- `src/main/java/codeanalysis/emitting/Emitter.java`: ASM-based JVM bytecode generation
- `src/test/java`: tests for lexer, parser, binder, evaluator, and compiler behavior
- `examples`: sample Siyo programs covering the implemented feature set
- `docs`: generated Javadoc output

## Requirements

- Java 17
- Maven

The project targets Java 17 and uses ASM for bytecode generation.

## Build and Run

Build the project:

```bash
mvn package
```

Start the REPL:

```bash
java -cp target/classes Main repl
```

Run a `.siyo` file with the interpreter:

```bash
java -cp target/classes Main run examples/hello.siyo
```

The CLI also accepts a file path directly:

```bash
java -cp target/classes Main examples/hello.siyo
```

Compile a `.siyo` file to JVM bytecode:

```bash
java -cp target/classes Main compile examples/hello.siyo
```

If the program imports modules, the compiler also emits `.class` files for those dependencies.

## Language Features

### Primitive and Core Types

- `int`
- `bool`
- `float`
- `string`
- `null`

### Variables

- `mut` and `imut`
- assignment: `x = 10`
- compound assignment: `+=`, `-=`, `*=`, `/=`

### Control Flow

- `if / else`
- `while`
- classic `for`
- `for item in collection`
- `break`
- `continue`
- `try / catch`

### Functions

- typed parameters
- optional return type
- implicit return from the last expression
- recursion
- forward function references

### Data Structures

- array literals: `[1, 2, 3]`
- indexing: `arr[0]`
- struct declarations: `struct Point { x: int, y: int }`
- struct literals: `Point { x: 1, y: 2 }`
- field access and mutation: `p.x`, `p.x = 10`
- enum declarations and members: `enum Color { Red, Green, Blue }`

### Modules

The repository includes file-based module support:

```siyo
import "math"

println(toString(math.max(10, 20)))
```

See [examples/modules/main.siyo](/C:/Users/uruns/IdeaProjects/SiyoCompiler/examples/modules/main.siyo) and [examples/modules/math.siyo](/C:/Users/uruns/IdeaProjects/SiyoCompiler/examples/modules/math.siyo).

## Examples

The `examples` directory reflects the current language surface well:

- `hello.siyo`: functions, structs, arrays, loops, string concatenation
- `sorting.siyo`: sorting algorithm example
- `linked_list.siyo`: struct-based linked list traversal
- `stack.siyo`: data structure implementation
- `todo_list.siyo`: enums, structs, and arrays together
- `grade_calculator.siyo`: `for in` loops and enums
- `word_counter.siyo`: string processing
- `error_handling.siyo`: `try/catch`, `error`, and bounds failures
- `modules/*`: imports and module-qualified function calls

## Architecture Overview

The compiler pipeline is organized as follows:

1. `Lexer` tokenizes the source text.
2. `Parser` builds the syntax tree.
3. `Binder` resolves names, scopes, and types.
4. `Lowerer` rewrites control flow into simpler bound forms.
5. `Evaluator` interprets the program or `Emitter` generates JVM bytecode.

This split keeps interpreter and compiler behavior aligned on the same semantic foundation.

## Test Status

The repository includes a broad JUnit test suite. The latest reports under `target/surefire-reports` show:

- `LexerTest`: 557 tests
- `ParserTest`: 396 tests
- `EvaluatorTest`: 218 tests
- `SyntaxRulesTest`: 96 tests
- `BinderTest`: 47 tests
- `CompilationTest`: 29 tests
- `ParserStatementTest`: 16 tests
- `SourceTextTest`: 3 tests

Total: 1362 tests, all passing in the latest available reports.

## Notes

- `GRAMMAR.md` appears to lag behind the current implementation.
- `FUTURE.md` is still useful as a roadmap, but it does not fully match the repository's present state.
- The most reliable source of truth is the combination of `src/main/java`, `src/test/java`, and `examples`.

## Additional Material

The repository also includes generated Javadoc under `docs/` and a few package-level README files inside the source tree. This top-level README is intended to describe the current project state accurately.
