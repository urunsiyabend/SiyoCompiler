# Siyo Compiler — Roadmap

## 0.1.0 (Current Release)

### Language Features
- **Types**: `int`, `long`, `bool`, `float` (double), `string`, arrays, structs, enums, `null`
- **Variables**: `mut` / `imut`, compound assignment (`+=`, `-=`, `*=`, `/=`)
- **Control Flow**: `if`/`else`, `while`, `for`, `for...in`, `break`, `continue`
- **Error Handling**: `try`/`catch` statements and expressions, `error()` builtin
- **Functions**: typed params, return types, implicit return, recursion, forward declarations
- **Closures**: `fn(x: int) -> int { x * 2 }`, captured variables, functions returning functions
- **Pattern Matching**: `match expr { pattern => result, _ => default }`
- **Structs**: declaration, literals, field access/mutation, pass by reference
- **Enums**: declaration, member access (`Color.Red`), integer backing
- **Concurrency**: `scope`/`spawn` (structured), channels (buffered/unbuffered), `for msg in ch`
- **Actors**: `actor struct`, `spawn Actor.new(...)`, synchronous calls, `send` (fire-and-forget)
- **Java Interop**: `import java "class"`, constructors, static/instance methods
- **Modules**: `import "file"` for Siyo modules
- **Strings**: interpolation (`"x = {expr}"`), escape sequences, full builtin set

### Built-in Functions (37)
`len`, `toString`, `parseInt`, `parseFloat`, `parseLong`, `toInt` (double/string), `toFloat`, `toDouble`, `toLong`, `print`, `println`, `input`, `error`, `range`, `push`, `pop`, `removeAt`, `sort`, `substring`, `contains`, `indexOf`, `startsWith`, `endsWith`, `replace`, `trim`, `toUpper`, `toLower`, `split`, `chr`, `ord`, `map`, `set`, `channel`, `random`, `httpGet`, `httpPost`, `canRead`

### Pipeline
- **Interpreter**: parse → bind → lower → evaluate
- **JVM Bytecode Compiler**: parse → bind → lower → emit (ASM, targets Java 21)
- **CLI**: `siyoc run file.siyo` | `siyoc compile file.siyo` | `siyoc interpret file.siyo` | `siyoc repl`

### Test Coverage
- **1402 unit tests** (lexer, parser, binder, evaluator, compilation)
- **43 compilation tests** verifying bytecode matches interpreter output
- **29 example programs**

### Known Limitations (0.1.0)
- No `int + double` mixed-type arithmetic (explicit conversion required)
- No map literal syntax (`{"key": val}`) — use `map()` + method calls
- No `set(1, 2, 3)` varargs — use `set()` then `.add()`
- No hex literals (`0xFF`)
- No `do-while` loops
- No nested function type annotations (`-> fn(int) -> fn(int) -> int`)
- No function calls inside string interpolation (`"{f(x)}"`)
- No enum types in function parameters — use int

---

## 0.2.0 — Type System & Ergonomics

### Mixed-Type Arithmetic
- `int + double` auto-promotion
- `int + long` auto-widening
- Implicit numeric conversions where safe

### Map & Set Literals
- `{"key": value}` map literal syntax
- `{1, 2, 3}` set literal syntax
- String indexing for maps: `m["key"]`

### Hex & Binary Literals
- `0xFF`, `0b1010`

### Do-While
- `do { ... } while condition`

### Improved Error Messages
- Source location in runtime errors
- Suggestion-based diagnostics

---

## 0.3.0 — Closures & Functional

### Full Higher-Order Functions
- Pass closures to functions and receive them back
- Nested function type annotations
- Closure variable mutation (currently read-only capture)

### Collection Operations
- `map(arr, fn)`, `filter(arr, fn)`, `reduce(arr, fn, init)`
- `forEach(arr, fn)`
- Method chaining

### String Interpolation Enhancement
- Allow function calls inside `{}`
- Multi-line strings

---

## 0.4.0 — Module System

### Proper Packages
- Directory-based module resolution
- Public/private visibility (`pub` keyword)
- Circular import detection
- Standard library modules

---

## 0.5.0 — Advanced Types

### Generics
- `Array<int>`, `Map<string, int>`
- Generic functions: `fn identity<T>(x: T) -> T`

### Interfaces / Traits
- `interface Printable { fn print() }`
- Struct implementation

### Algebraic Types
- `type Result = Ok(value) | Err(msg)`
- Exhaustive match checking

---

## 1.0.0 — Production Ready

### Tooling
- `siyoc fmt` — formatter
- `siyoc check` — type checker without running
- LSP server for editor integration
- Package manager

### Performance
- Optimizing compiler passes
- Inline caching for method dispatch
- Escape analysis for struct allocation

### Self-Hosting
- Compiler written in Siyo

---

## Development Principles

- All features must have tests in both interpreter and bytecode paths
- Compilation tests verify bytecode output matches interpreter
- Diagnostics over crashes — report errors, don't throw
- Clean pipeline: Syntax → Binding → Lowering → Emission/Evaluation
- Interpreter remains reference implementation
