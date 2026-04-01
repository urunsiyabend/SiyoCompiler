# Siyo Compiler

<p align="center">
  <img src="src/main/java/SiyoLanguage.png" alt="Siyo logo" width="180" />
</p>

A compiled programming language for the JVM with actors, channels, and Java interop.

Siyo compiles to JVM bytecode via ASM and also includes an interpreter. It features structs, enums, closures, pattern matching, structured concurrency with channels, and an actor model — all with a clean, Go-inspired syntax.

## Quick Start

```bash
# Build
mvn package

# Run a program (bytecode — default)
java -jar target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar run examples/hello.siyo

# Interpret (for debugging)
java -jar target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar interpret examples/hello.siyo

# Compile to .class
java -jar target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar compile examples/hello.siyo

# REPL
java -jar target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar repl
```

## Requirements

- Java 21+
- Maven 3.9+

## Language Overview

```siyo
// Functions and structs
struct Point { x: int, y: int }

fn distance(a: Point, b: Point) -> float {
    imut dx = toFloat(a.x - b.x)
    imut dy = toFloat(a.y - b.y)
    return dx * dx + dy * dy
}

// Closures
fn makeAdder(n: int) -> fn(int) -> int {
    return fn(x: int) -> int { x + n }
}

imut add10 = makeAdder(10)
println(toString(add10(5)))  // 15

// Pattern matching
fn describe(n: int) -> string {
    return match n {
        0 => "zero"
        1 => "one"
        _ => "other: " + toString(n)
    }
}

// Error handling
imut result = try { parseInt("abc") } catch e { -1 }

// Concurrency with channels
imut ch = channel(10)
scope {
    spawn {
        for i in range(0, 5) { ch.send(i * i) }
        ch.close()
    }
    spawn {
        for val in ch { println(toString(val)) }
    }
}

// Java interop
import java "java.net.Socket"
mut socket = Socket.new("localhost", 8080)
```

## Features

### Types
`int`, `long`, `bool`, `float` (64-bit double), `string`, arrays, structs, enums, `null`, closures, channels, maps, sets

### Control Flow
`if`/`else`, `while`, `for`, `for x in collection`, `break`, `continue`, `try`/`catch`, `match`

### Functions & Closures
Typed parameters, return types, implicit return, recursion, first-class functions, closures with captured variables, functions returning functions

### Concurrency
- **scope/spawn** — structured concurrency, all threads joined at scope end
- **Channels** — unbuffered (rendezvous) and buffered, `send`/`receive`/`close`, `for msg in ch`
- **Actors** — `actor struct` with mailbox, synchronous calls, fire-and-forget `send`

### Java Interop
`import java "fully.qualified.ClassName"` — call constructors, static methods, instance methods

### Built-in Functions (37)
Type conversion, string manipulation, array operations, I/O, collections, random, HTTP

See [GRAMMAR.md](GRAMMAR.md) for the full language specification.

## Examples

The `examples/` directory contains 29 programs:

| Example | Features |
|---------|----------|
| `hello.siyo` | Functions, structs, arrays, string concatenation |
| `fizzbuzz.siyo` | Classic FizzBuzz with match |
| `sorting.siyo` | Bubble sort algorithm |
| `calculator.siyo` | Expression parser with operator precedence |
| `linked_list.siyo` | Struct-based linked list |
| `json_parser.siyo` | Recursive descent JSON parser |
| `rest_api.siyo` | HTTP server with routing |
| `sqlite_client.siyo` | SQLite database operations |
| `key_value_store.siyo` | In-memory KV store |
| `csv_reader.siyo` | CSV file parsing |
| `test_framework.siyo` | Test runner with assertions |
| `java_interop.siyo` | Java standard library usage |
| `todo_app.siyo` | Interactive todo manager |

## Architecture

```
Source → Lexer → Parser → Binder → Lowerer → Emitter (bytecode)
                                            → Evaluator (interpreter)
```

1. **Lexer** tokenizes source text
2. **Parser** builds the syntax tree
3. **Binder** resolves names, scopes, and types
4. **Lowerer** rewrites control flow into simpler forms
5. **Emitter** generates JVM bytecode via ASM / **Evaluator** interprets directly

Both paths share the same semantic foundation, ensuring consistent behavior.

## Project Structure

```
src/main/java/
  Main.java                          CLI entry point
  codeanalysis/
    syntax/        Lexer, Parser, syntax tree nodes
    binding/       Binder, scopes, type resolution
    lowering/      Control flow lowering
    emitting/      ASM bytecode generation
    Evaluator.java Interpreter runtime
    SiyoActor.java Actor model runtime
    SiyoChannel.java Channel implementation
    SiyoRuntime.java Compiled code runtime helpers
src/test/java/     1402 tests
examples/          29 example programs
projects/          Multi-file project examples (chat, siyodb)
```

## Tests

```bash
mvn test
```

1402 tests across lexer, parser, binder, evaluator, and compilation (bytecode-vs-interpreter) suites. All passing.

## Documentation

- [GRAMMAR.md](GRAMMAR.md) — Full language grammar and type system
- [FUTURE.md](FUTURE.md) — Development roadmap
- [docs/ACTOR_DESIGN.md](docs/ACTOR_DESIGN.md) — Actor model design document

## License

This project is provided as-is for educational and experimental purposes.
