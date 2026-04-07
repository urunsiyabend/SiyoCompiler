# Siyo Language Grammar (0.2.0)

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
- **Array**: `[1, 2, 3]`, `[]` (empty literal infers element type from context)
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
    : ('mut' | 'imut') IDENTIFIER '=' expression

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

## Java Interop

```siyo
import java "java.net.Socket"
mut s = Socket.new("localhost", 8080)
```

Static methods, constructors, and instance methods are callable. Return types are mapped automatically.
