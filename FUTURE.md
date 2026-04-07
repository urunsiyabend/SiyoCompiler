# Siyo Compiler — Roadmap

## 0.1.1 (Previous Release)

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
- **Strings**: interpolation (`"x = $name"` / `"x = ${expr}"`), escape sequences, full builtin set

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

### Known Limitations (0.1.x)
- No `int + double` mixed-type arithmetic (explicit conversion required)
- No map literal syntax (`{"key": val}`) — use `map()` + method calls
- No `set(1, 2, 3)` varargs — use `set()` then `.add()`
- No hex literals (`0xFF`)
- No `do-while` loops
- No nested function type annotations (`-> fn(int) -> fn(int) -> int`)
- No enum types in function parameters — use int

---

## 0.2.0 (Current Release) — Ergonomics & Reliability

### Compiler Improvements
- **Generalized type coercion**: Single `emitCoerceArg()` replaces per-builtin CHECKCAST logic. String builtins (contains, substring, indexOf, etc.) now work with Object-typed arguments from maps, actors, etc.
- **Source maps / line numbers**: Bytecode now includes LineNumberTable entries. Stack traces show source file and line numbers instead of raw JVM dumps.
- **Module-level mutable variables**: `mut` variables at module top level now work correctly in bytecode — emitted as static fields on the module class with `<clinit>` initialization.
- **Actor method return type tracking**: Calling a method on a typed actor parameter (`fn foo(s: Store)`) now preserves the method's declared return type instead of erasing to `Object`. Eliminates the `parseInt(toString(actor.method()))` workaround.
- **Bare `spawn { }` outside `scope`**: Fire-and-forget virtual threads now allowed at any nesting level. Server patterns no longer need a wrapping scope.
- **Match arm block bodies**: All preceding statements in a match-arm block are now bound (not just the trailing expression). Fixes `Name '...' does not exist` for arms with multiple statements.
- **Object indexing**: Object-typed values (e.g., from actor returns) can be indexed with a runtime list cast.
- **Mutable capture exemption for actors**: Actor handles and Object-typed values are exempt from the mutable-capture restriction in `spawn` blocks (actors are thread-safe by design).

### New Syntax
- **Map literals**: `{"key": value, "key2": value2}` and `{}` for empty maps
- **Triple-quote strings**: `"""..."""` for multi-line string literals
- **Top-level code without `{ }`**: Wrapping braces no longer required at file level
- **`if`/`else` as expression**: `mut x = if cond { "a" } else { "b" }`. Works as the trailing expression of a match arm and inside `return`. Eliminates the temp-variable + mutation workaround.

### New Features
- **`for key in map`**: Map iteration over keys
- **`os.args()`**: Command-line arguments now accessible (previously returned empty)
- **`println(42)`**: println auto-converts any type (boxing + Object overload)
- **`"text" + intVar`**: String concatenation with non-string types
- **Empty array literals**: `mut arr = []` and `return []` work correctly
- **String interpolation**: `$var` for bare identifiers, `${expr}` for arbitrary expressions, supported inside both regular and triple-quoted strings.

### Showcase Projects
- **siyocluster**: A multi-node replicated TCP key-value store (~800 LOC) with primary/replica failover, heartbeat-based dead-peer detection, and lowest-port leader election. Uses two actors (`Store`, `NodeState`), virtual-thread connection handling, and Java interop for `ServerSocket`/`Socket`. Demonstrates the actor model end-to-end and was the validation harness for several 0.2.0 fixes.
- **sitegen**: A static site generator written entirely in Siyo, demonstrating multi-file modules, file I/O, string templating, and the std library.

### Test Coverage
- **1475 unit tests** passing (lexer, parser, binder, evaluator, compilation parity)

### Pain Points Tracked for Future Releases

#### Tier 2 — Important (0.3.0)
- Nested JSON parse/stringify broken in std/json
- JDBC / complex Java interop VerifyError
- Cross-module closure dispatch uses reflection

#### Tier 3 — Nice to Have (0.3.0+)
- Module aliasing (`import "std/math" as m`)
- Closure capture mutation (currently read-only)
- Generics / parameterized types
- Type casting / `as` operator
- Default parameter values, named arguments
- Char type, destructuring, selective imports
- std/time, regex support, test discovery
- LSP server, code formatter
- `imut` → `const`/`let`/`val` keyword

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
