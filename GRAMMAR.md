# Siyo Language Grammar (0.3.0)

## Lexical Grammar

### Keywords

| Keyword | Description |
|---------|-------------|
| `true` / `false` | Boolean literals |
| `mut` | Mutable variable declaration |
| `imut` | Immutable variable declaration |
| `if` / `else` | Conditional |
| `while` | While loop |
| `for` / `in` | For loop / for-in iteration |
| `fn` | Function / lambda declaration |
| `return` | Return statement |
| `break` / `continue` | Loop control |
| `try` / `catch` | Error handling |
| `match` | Pattern matching expression |
| `struct` | Struct type declaration |
| `enum` | Enum type declaration |
| `import` | Module / Java class import |
| `scope` / `spawn` | Structured concurrency |
| `send` | Actor fire-and-forget message |
| `actor` | Actor struct marker |
| `null` | Null literal |

### Operators

#### Unary Operators

| Operator | Description |
|----------|-------------|
| `+` | Identity |
| `-` | Negation |
| `!` | Logical NOT |
| `~` | Bitwise NOT |

#### Binary Operators

| Operator | Description |
|----------|-------------|
| `+` `-` `*` `/` `%` | Arithmetic |
| `==` `!=` | Equality (all types) |
| `<` `<=` `>` `>=` | Ordering (int, long, double, string) |
| `&&` `\|\|` | Logical AND / OR (short-circuit) |
| `&` `\|` `^` | Bitwise AND / OR / XOR |
| `<<` `>>` | Bit shifts |

#### Compound Assignment

`+=`, `-=`, `*=`, `/=`

### Operator Precedence

| Precedence | Operators | Description |
|------------|-----------|-------------|
| 6 (highest)| `+` `-` `!` `~` (unary) | Unary |
| 5 | `*` `/` `%` | Multiplicative |
| 4 | `+` `-` | Additive |
| 3 | `==` `!=` `<` `<=` `>` `>=` | Relational |
| 2 | `&&` `&` `<<` `>>` | AND / shifts |
| 1 (lowest) | `\|\|` `\|` `^` | OR / XOR |

### Literals

- **Integer**: `42`, `-7`
- **Float**: `3.14`, `0.5`
- **Long**: returned by Java interop (e.g. `System.currentTimeMillis()`)
- **String**: `"hello"`, with escapes `\n` `\t` `\\` `\"` `\$`
- **Triple-quoted string**: `"""multi-line\nliteral"""` — preserves newlines verbatim, no escape interpretation needed for `"` inside the body
- **String interpolation**: `"hello $name"` (bare identifier) and `"sum is ${a + b}"` (arbitrary expression). `\$` escapes a literal `$`. Works inside both regular and triple-quoted strings.
- **Boolean**: `true`, `false`
- **Null**: `null`
- **Array**: `[1, 2, 3]`, `mut xs: string[] = []` (typed empty literal)
- **Map**: `{"key": value, "other": 42}`, `{}` for empty
- **Set**: `set()` then `.add(x)` (no literal form yet)

### Delimiters

`(` `)` `{` `}` `[` `]` `:` `,` `->` `.`

## Syntactic Grammar

### Statements

```
compilation_unit  : statement* EOF

statement
    : block_statement
    | variable_declaration
    | if_statement
    | while_statement
    | for_statement
    | for_in_statement
    | function_declaration
    | struct_declaration
    | enum_declaration
    | return_statement
    | break_statement
    | continue_statement
    | try_catch_statement
    | scope_expression
    | import_statement
    | send_statement
    | expression_statement

variable_declaration
    : ('mut' | 'imut') IDENTIFIER (':' type_annotation)? '=' expression

if_statement
    : 'if' expression block ('else' (if_statement | block))?

// `if`/`else` is also valid in expression position; both branches must be
// blocks ending in an expression. Example:
//     mut label = if score > 50 { "pass" } else { "fail" }

while_statement : 'while' expression block
for_statement   : 'for' variable_declaration expression expression block
for_in_statement: 'for' IDENTIFIER 'in' expression block
                | 'for' IDENTIFIER 'in' map_expression block   // iterates keys

function_declaration
    : 'fn' IDENTIFIER '(' parameter_list? ')' type_clause? block

struct_declaration
    : 'struct' IDENTIFIER '{' field_list '}'

enum_declaration
    : 'enum' IDENTIFIER '{' IDENTIFIER (',' IDENTIFIER)* '}'

try_catch_statement
    : 'try' block 'catch' IDENTIFIER block

scope_expression
    : 'scope' block          // spawned threads joined at block end

send_statement
    : 'send' expression      // fire-and-forget actor message

import_statement
    : 'import' STRING
    | 'import' 'java' STRING

return_statement : 'return' expression?
break_statement  : 'break'
continue_statement: 'continue'
```

### Expressions

```
expression : assignment_expression

assignment_expression
    : IDENTIFIER '=' expression
    | IDENTIFIER compound_op expression
    | binary_expression

binary_expression
    : unary_expression (binary_op unary_expression)*

unary_expression
    : unary_op unary_expression
    | postfix_expression

postfix_expression
    : primary_expression ('[' expression ']' | '.' IDENTIFIER | '(' argument_list? ')')*

primary_expression
    : NUMBER | FLOAT | STRING | TRIPLE_QUOTED_STRING | 'true' | 'false' | 'null'
    | IDENTIFIER
    | '(' expression ')'
    | '[' expression_list? ']'                       // array literal (empty allowed)
    | '{' (STRING ':' expression (',' ...)*)? '}'    // map literal (empty allowed)
    | struct_literal
    | lambda_expression
    | match_expression
    | if_expression
    | try_expression
    | spawn_expression

if_expression
    : 'if' expression block 'else' block             // both branches required, value = last expr in block

lambda_expression
    : 'fn' '(' parameter_list? ')' type_clause? block

match_expression
    : 'match' expression '{' match_arm* '}'

match_arm
    : expression '=>' expression
    | '_' '=>' expression

try_expression
    : 'try' block 'catch' IDENTIFIER block

spawn_expression
    : 'spawn' block          // bare `spawn { ... }` is allowed outside `scope { }`
                             // and runs as a fire-and-forget virtual thread.
    | 'spawn' call_expression// `spawn Actor.new(...)` to start an actor

struct_literal
    : IDENTIFIER '{' (IDENTIFIER ':' expression ',')* '}'
```

## Type System

### Built-in Types

| Type | Java Mapping | Description |
|------|-------------|-------------|
| `int` | `Integer` | 32-bit signed integer |
| `long` | `Long` | 64-bit signed integer |
| `float` | `Double` | 64-bit floating point |
| `bool` | `Boolean` | Boolean |
| `string` | `String` | Immutable string |
| `int[]` / `string[]` | `SiyoArray` | Dynamic array |
| `fn` | `SiyoClosure` | First-class function |
| `channel` | `SiyoChannel` | Thread-safe channel |
| `map` | `SiyoMap` | Key-value map |
| `set` | `SiyoSet` | Unique value set |
| `object` / `any` | `Object` | Any type |

### Type Rules

- Arithmetic operators work on `int`, `long`, `double` (same-type operands)
- String supports `+` (concatenation), `==`, `!=`, `<`, `>`, `<=`, `>=`
- `null` supports `==` and `!=` with any type
- Assignment requires compatible types; `imut` variables cannot be reassigned
- Function return type checked against declared type
- Array elements must be homogeneous
- Channel receive returns `Object`

## Built-in Functions

### Type Conversion
`toString(any)`, `toInt(double)`, `toInt(string)`, `toDouble(int)`, `toFloat(int)`, `toLong(int)`, `parseInt(string)`, `parseFloat(string)`, `parseLong(string)`

### String
`len(string)`, `substring(s, start, end)`, `contains(s, sub)`, `indexOf(s, sub)`, `startsWith(s, prefix)`, `endsWith(s, suffix)`, `replace(s, old, new)`, `trim(s)`, `toUpper(s)`, `toLower(s)`, `split(s, delim)`, `chr(int)`, `ord(string)`

### Array
`len(array)`, `push(arr, val)`, `pop(arr)`, `removeAt(arr, idx)`, `sort(arr, comparator)`

### I/O
`print(val)`, `println(val)`, `input(prompt)`, `error(msg)`

### Collections
`map()`, `set()`, `channel()`, `channel(capacity)`, `range(start, end)`

### Other
`random(max)`, `httpGet(url)`, `httpPost(url, body)`, `canRead(reader)`

## Concurrency

### Scope / Spawn
```siyo
scope {
    spawn { /* task 1 */ }
    spawn { /* task 2 */ }
}
// all spawned threads joined here
```

### Bare Spawn
```siyo
// Outside any scope { } — fire-and-forget virtual thread, never joined.
spawn {
    while true { handleRequest() }
}
```

### Channels
```siyo
imut ch = channel()     // unbuffered (rendezvous)
imut ch = channel(10)   // buffered

ch.send(value)          // blocks until taken / space available
ch.receive()            // blocks until value available
ch.close()              // signal no more values

for msg in ch { ... }   // iterate until closed
```

### Maps
```siyo
mut prices = {"apple": 100, "pear": 120}
prices.set("plum", 90)
println(toString(prices.get("apple")))

for key in prices {       // iterates keys
    println("$key = ${prices.get(key)}")
}
```

### Actors
```siyo
actor struct Counter { count: int }
fn Counter.increment(n: int) -> int { self.count += n; self.count }

mut c = spawn Counter.new(Counter { count: 0 })
imut result = c.increment(5)   // synchronous call
send c.increment(1)            // fire-and-forget
```

## Modules

### Module file format

A `.siyo` file is a sequence of top-level items. No outer `{ }` wrapper is needed (it is accepted for backward compatibility but no longer idiomatic).

```siyo
mut state = 0          // module-level variable, initialized at load time

fn init() {            // runs once when the module is first loaded (eagerly on import)
    state = 1
}

fn greet(name: string) {
    println("hello " + name)
}
```

#### Top-level rules (Go-style)

Only these forms are allowed at the top of a file:
- `fn`, `struct`, `actor`, `impl`, `enum` declarations
- `import` and `import java` statements
- `mut` / `imut` variable declarations

Any bare statement at the top level — `println(...)`, `if ...`, `for ...`, loose expressions — is a **compile error**: `top-level statement not allowed; move into init() or main()`.

Two zero-arg functions have special runtime semantics:

- **`fn init()`** — runs once when the module is first loaded. Eagerly triggered at import time (the importing class's `main` / `<clinit>` force-loads each imported module class, which fires the imported module's own `<clinit>`, which runs its `init()`). For a given file, the order is: imported modules' `init()`s (in import order, depth-first) → this module's top-level variable initializers → this module's `init()`.
- **`fn main()`** — runs only when the module is the entrypoint (compiled directly via `siyoc run file.siyo` or as the project main). Never runs when the module is imported.

A file that is run as the entrypoint and defines both functions runs `init()` first, then `main()`. A file with neither is a no-op when run as the entrypoint.

Functions and top-level variables are exported as `moduleName.symbol` when imported from another file.

### Import

```siyo
import "std/io"          // stdlib module
import "utils"           // relative to the importing file's directory, or src/
import java "java.io.File"  // Java class import
```

### Module resolution order

When `import "name"` is resolved, the compiler searches in this order:
1. `name.siyo` relative to the importing file's directory
2. `name/index.siyo` relative to the importing file's directory
3. `src/name.siyo` relative to the project root (siyo.toml location)
4. `name.siyo` in the working directory
5. `std/name.siyo` from the bundled stdlib (classpath)

### Function references

Named functions can be used as first-class values:

```siyo
fn double(x: int) -> int { return x * 2 }
mut ops: fn[] = [double, double]
```

## Java Interop

```siyo
import java "java.net.Socket"
mut s = Socket.new("localhost", 8080)
```

Static methods, constructors, and instance methods are callable. Return types are mapped automatically.

## Standard Library

### `std/io`

```siyo
import "std/io"

io.exists(path)             // true if path exists (file or dir)
io.isFile(path)             // true if regular file
io.isDir(path)              // true if directory
io.readFile(path)           // returns string (text)
io.writeFile(path, content) // writes text
io.appendFile(path, content)
io.readLines(path)          // returns string[]
io.listDir(path)            // returns string[] (non-recursive)
io.walk(dir)                // returns string[] of all files recursively
io.readBytes(path)          // returns int[] (0–255 per byte)
io.writeBytes(path, bytes)  // writes int[] as binary
io.copyFile(src, dst)       // binary-safe file copy
io.mkdir(path)
io.delete(path)
```

### `std/path`

```siyo
import "std/path"

path.join(a, b)     // join two path segments
path.parent(p)      // parent directory
path.basename(p)    // last component (e.g. "file.siyo")
path.stem(p)        // basename without extension (e.g. "file")
path.extension(p)   // extension including dot (e.g. ".siyo")
path.sep()          // OS path separator
```

### `std/strings`

Functions from `std/strings` (import to use the qualified name, or call built-ins directly):

```siyo
import "std/strings"

strings.join(arr, sep)       // join string array
strings.repeat(s, n)         // repeat string n times
strings.padLeft(s, w, ch)    // left-pad to width
strings.padRight(s, w, ch)   // right-pad to width
strings.trimStart(s)         // strip leading whitespace
strings.trimEnd(s)           // strip trailing whitespace
strings.lastIndexOf(s, sub)  // index of last occurrence, -1 if absent
strings.chars(s)             // split into individual characters
strings.lines(s)             // split by newline
```

Built-in string functions (no import needed): `len`, `substring`, `contains`, `indexOf`, `startsWith`, `endsWith`, `replace`, `trim`, `toUpper`, `toLower`, `split`, `chr`, `ord`.

### `std/html`

```siyo
import "std/html"

html.escape(s)      // escape & < > "
html.escapeAttr(s)  // escape & < > " ' newlines
```

### `std/json`

```siyo
import "std/json"

json.parse(s)       // parse JSON string → map (nested objects/arrays fully decoded)
json.stringify(v)   // serialize map/array/scalar → JSON string
```

### `std/testing`

```siyo
import "std/testing"

testing.assert(cond, msg)
testing.assertEqual(actual, expected, msg)
testing.assertEq(actual, expected, msg)      // alias for assertEqual
testing.assertThrows(body, expectedSubstring)
testing.beforeEach(fn() { ... })             // run before each test in run()
testing.afterEach(fn() { ... })              // run after each test in run()
testing.run("suite name", [test1, test2])    // pass functions by reference
```

### `std/math`

Common math functions (`math.abs`, `math.sqrt`, `math.pow`, `math.floor`, `math.ceil`, `math.min`, `math.max`, etc.).

## Tooling

### `siyoc` CLI

```
siyoc run <file>            run a .siyo file (bytecode)
siyoc run                   run src/main.siyo in the current project
siyoc test                  auto-discover and run tests:
                              1. src/test.siyo (if present)
                              2. tests/*_test.siyo (alphabetical order)
siyoc test <file>           run a specific test file
siyoc new <name>            scaffold a new project
siyoc --version / -v        print version string
siyoc --help / -h           print usage
```

### Test auto-discovery

Place test files in a `tests/` directory with names ending in `_test.siyo`. Each file is compiled and run independently.

```
project/
  siyo.toml
  src/
    main.siyo
  tests/
    parser_test.siyo
    eval_test.siyo
```

### `siyo.toml` project config

`siyoc run` and `siyoc test` walk up the directory tree to find `siyo.toml`, so they work from any subdirectory of the project.

```toml
[project]
name = "myapp"
version = "0.1.0"
main = "src/main.siyo"

[dependencies]
# maven coordinates → local jar cache
```
