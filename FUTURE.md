# Siyo Compiler — Roadmap

## 0.1.1 — Foundations (Released)

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

## 0.2.0 — Ergonomics & Reliability (Released)

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
  — 1499 as of 0.4.0

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

## 0.3.0 / 0.3.1 — Module Layout & Tooling (Released)

- Go-style module top level: only declarations and imports are allowed at the
  top of a file, with `init()` on load and `main()` for the entrypoint
- Module resolution walks up to `siyo.toml`, so commands work from any
  subdirectory; `siyoc test` auto-discovers `tests/*_test.siyo`
- Typed arrays (`T[]`), binary file I/O, `std/path`, `std/html`, rewritten
  `std/json` with nested objects and arrays
- Diagnostics carry file, line and column
- 0.3.1 fixed postfix-call parser greediness

---

## 0.4.0 — Module Boundary Maturity (Released)

Driven by the Aja static-site-generator maturity test — see
`RELEASE_NOTES_0.4.0.md` for the full list.

- Imported enums, `impl` methods and struct identity survive module boundaries,
  including diamond-shaped import graphs; no module re-exports a transitive
  import under its own JVM owner
- `T[]` returns and parameters keep their element type, and `for-in` elements
  keep their struct identity
- Imported Java classes are usable in function, lambda and `impl` signatures;
  overload selection uses exact JVM descriptors, and an ambiguous erased
  `object` argument is a compile-time error instead of invalid bytecode
- Void-valued `match` statements no longer emit an invalid trailing `POP`
- `actor struct Name` is accepted alongside `actor Name`
- Parser list loops (parameters, arguments, fields, enum members, match arms,
  map/array/struct literals, `impl` bodies) always make progress, so malformed
  input reports diagnostics instead of exhausting the heap

---

## 0.5.0 — Module Namespaces & Located Errors (Released)

Driven by Morioh — a 3,000-line HTTP library (server, client, routing,
middleware) written in Siyo 0.4.0 to find out where the language breaks under
real use. It produced 32 reproducible defects; most are fixed here. See
`RELEASE_NOTES_0.5.0.md` for the full list.

- **A module is a namespace again.** Every function carries the file that
  declared it, so two modules may both declare `parse`, a module no longer
  exports what it imported, and a module-level variable keeps working when its
  module is reached through another module
- **The Java value boundary keeps types honest.** Erased operands compare and
  widen without truncating, Siyo and Java arrays convert in both directions,
  and primitives box when they reach an `object` parameter or struct field
- **Every failure has a source location.** An internal emitter or ASM failure is
  reported as a located diagnostic instead of a raw Java stack trace
- **Silent wrongness is now reported.** A local type annotation is checked
  (`imut x: string = 5` used to compile), and a write to a captured variable is
  a diagnostic instead of a silently discarded write
- `siyoc interpret` runs `init()` and `main()`, so the interpreter-versus-
  bytecode comparison works again for module-style programs
- 1,565 tests at release (1,581 on master today)

---

## 0.6.0 — Advanced Types (Next)

### Small and self-contained (first batch)
- **Long literals** — `Lexer.readNumberToken()` reads only int and float, so a
  value that overflows `int` is reported as an invalid number instead of
  becoming a `long`. Wide values come from `toLong()` today
- **`std/json` cannot report a parse failure** — `json.parse("not json")`
  returns an empty map, the same value as the valid document `{}`
- **A declared function type is not checked** — `fn(int) -> int` is parsed and
  discarded, so a callback with the wrong arity fails at run time
- **Hex literals** (`0xFF`)

### Error Handling
- `throw` / user-raised errors
- An error payload: `error(msg)` raises text and `catch e` binds text, so an
  error cannot carry a status code or be matched on by type
- Observable exception type in `catch` (a message-less Java exception prints as
  `null`)

### Closures & Collections
- Closure variable mutation — a write to a captured variable is a diagnostic as
  of 0.5.0; making it work is still open
- `map(arr, fn)`, `filter(arr, fn)`, `reduce(arr, fn, init)`, `forEach(arr, fn)`
  — note that today's `map` builtin is the map constructor, not a higher-order
  function
- Method chaining

### Module System
- Public/private visibility (`pub` keyword)
- Module aliasing (`import "std/math" as m`)

### Algebraic Types
- `type Result = Ok(value) | Err(msg)`
- Exhaustive match checking
- This is the largest remaining expressiveness gap: a function whose result is
  one of several shapes has to return a record carrying every field of every
  case plus flags to say which is live

### Generics
- `Array<int>`, `Map<string, int>`
- Generic functions: `fn identity<T>(x: T) -> T`
- Reflection over struct fields, so a struct can be serialised without being
  converted to a map by hand

### Interfaces / Traits
- `interface Printable { fn print() }`
- Struct implementation

### Already implemented, previously listed here as pending
- `expr as Type` casts and circular-import detection have both existed since
  0.1.0. Casts are still undocumented in `GRAMMAR.md`

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
