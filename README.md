# The Siyo Programming Language

<p align="center">
  <img src="src/main/java/SiyoLanguage.png" alt="Siyo" width="160" />
</p>

<p align="center">
  <strong>A compiled language for the JVM with actors, channels, and native Java interop.</strong>
</p>

<p align="center">
  <a href="https://github.com/urunsiyabend/SiyoCompiler/actions"><img src="https://github.com/urunsiyabend/SiyoCompiler/actions/workflows/maven.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/java-21%2B-blue" alt="Java 21+">
  <img src="https://img.shields.io/badge/version-0.1.0-green" alt="v0.1.0">
  <img src="https://img.shields.io/badge/tests-1402%20passing-brightgreen" alt="Tests">
</p>

---

Siyo compiles directly to JVM bytecode. No intermediate language, no transpilation — your code runs on the same VM as Java and Kotlin, with full access to the Java ecosystem.

It ships with an actor model, Go-style channels, closures, pattern matching, structs, and a growing standard library. The compiler is ~19K lines of Java and includes both a bytecode backend (ASM) and a tree-walking interpreter for debugging.

## What it looks like

**Concurrent key-value store with actors:**

```siyo
actor Store { data: map }

impl Store {
    fn new() -> Store { Store { data: map() } }

    fn set(self, key: string, value: string) -> string {
        self.data.set(key, value)
        "OK"
    }

    fn get(self, key: string) -> string {
        if self.data.has(key) { return toString(self.data.get(key)) }
        "(nil)"
    }
}

mut store = spawn Store.new()
println(store.set("lang", "siyo"))   // OK
println(store.get("lang"))           // siyo
send store.set("async", "fire-and-forget")
```

**REST API with SQLite (runs on JVM):**

```siyo
import java "java.sql.DriverManager"
import java "java.net.ServerSocket"

fn dbInsert(name: string, email: string) -> int {
    mut conn = DriverManager.getConnection("jdbc:sqlite:app.db")
    mut stmt = conn.createStatement()
    stmt.execute("INSERT INTO users (name, email) VALUES ('" + name + "','" + email + "')")
    mut id = stmt.executeQuery("SELECT last_insert_rowid()").getInt(1)
    stmt.close()
    conn.close()
    id
}

mut server = ServerSocket.new(3000)
println("Listening on :3000")
// ... handle requests with full HTTP parsing
```

**Closures and higher-order functions:**

```siyo
fn makeMultiplier(factor: int) -> fn(int) -> int {
    return fn(x: int) -> int { x * factor }
}

imut triple = makeMultiplier(3)
imut times10 = makeMultiplier(10)
println(toString(triple(7)))    // 21
println(toString(times10(7)))   // 70

mut words = ["banana", "apple", "cherry"]
sort(words, fn(a: string, b: string) -> int {
    if a < b { return -1 }
    if a > b { return 1 }
    return 0
})
// ["apple", "banana", "cherry"]
```

**Structured concurrency with channels:**

```siyo
imut results = channel(10)

scope {
    spawn {
        for i in range(0, 100) {
            results.send(i * i)
        }
        results.close()
    }
    spawn {
        for val in results {
            println(toString(val))
        }
    }
}
// all threads joined automatically at scope end
```

## Install

**Requirements:** Java 21+, Maven 3.9+

```bash
git clone https://github.com/urunsiyabend/SiyoCompiler.git
cd SiyoCompiler
mvn package -DskipTests
```

This produces `target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar` — a single fat JAR with no external dependencies.

## Usage

```bash
JAR=target/siyo-compiler-0.1.0-SNAPSHOT-shaded.jar

# Run (compiles to bytecode, executes — default mode)
java -jar $JAR run program.siyo

# Compile to .class file
java -jar $JAR compile program.siyo

# Interpret (tree-walking, useful for debugging)
java -jar $JAR interpret program.siyo

# REPL
java -jar $JAR repl

# With Java classpath (e.g. SQLite JDBC driver)
java -jar $JAR -cp lib/sqlite-jdbc.jar run server.siyo
```

## Language Features

| Feature | Description |
|---------|-------------|
| **Types** | `int`, `long`, `bool`, `float`, `string`, arrays, structs, enums, `null` |
| **Variables** | `mut x = 10`, `imut y = "constant"`, `x += 1` |
| **Control flow** | `if`/`else`, `while`, `for`, `for x in collection`, `break`, `continue` |
| **Functions** | Typed params, return types, implicit return, recursion, forward refs |
| **Closures** | `fn(x: int) -> int { x * 2 }`, variable capture, factory pattern |
| **Structs** | `struct Point { x: int, y: int }`, field access, mutation, pass-by-ref |
| **Enums** | `enum Direction { N, E, S, W }` |
| **Pattern matching** | `match expr { 1 => "one", _ => "other" }` |
| **Error handling** | `try { ... } catch e { ... }`, `error("msg")`, try-as-expression |
| **Concurrency** | `scope`/`spawn`, channels (buffered & unbuffered), `for msg in ch` |
| **Actors** | `actor struct`, `spawn Actor.new(...)`, sync calls, async `send` |
| **Java interop** | `import java "java.net.Socket"`, constructors, static & instance methods |
| **Modules** | `import "file"` |
| **String interpolation** | `"Hello, {name}! You are {age} years old."` |

### Standard Library — 37 built-in functions

**Conversion:** `toString`, `toInt`, `toDouble`, `toFloat`, `toLong`, `parseInt`, `parseFloat`, `parseLong`
**Strings:** `len`, `substring`, `contains`, `indexOf`, `startsWith`, `endsWith`, `replace`, `trim`, `toUpper`, `toLower`, `split`, `chr`, `ord`
**Arrays:** `push`, `pop`, `removeAt`, `sort`, `range`
**Collections:** `map`, `set`, `channel`
**I/O:** `print`, `println`, `input`, `error`
**Other:** `random`, `httpGet`, `httpPost`, `canRead`

## Examples

The repository includes 29 example programs and 2 multi-file projects:

| Category | Examples |
|----------|---------|
| **Algorithms** | Bubble sort, binary search, linked list, stack |
| **Applications** | Todo app, calculator, grade calculator, guessing game |
| **Data processing** | CSV reader, JSON parser, word counter |
| **Networking** | REST API (with SQLite), HTTP client/server, NIO server |
| **Java interop** | File I/O, JDBC, sockets |
| **Concurrency** | SiyoDB (actor-based TCP database), chat server |

```bash
java -jar $JAR run examples/fizzbuzz.siyo
java -jar $JAR run examples/json_parser.siyo
java -jar $JAR -cp lib/sqlite-jdbc.jar run examples/rest_api_v2.siyo
```

## How it works

```
Source (.siyo) → Lexer → Parser → Binder → Lowerer ─┬→ Emitter → JVM Bytecode
                                                     └→ Evaluator (interpreter)
```

Both the bytecode compiler and interpreter share the same frontend (lexer through lowerer), guaranteeing identical semantics. The compiler emits standard `.class` files using the ASM library, targeting Java 21's virtual threads for concurrency.

| Component | Role |
|-----------|------|
| **Lexer** | Tokenizes source into a stream of tokens |
| **Parser** | Builds a concrete syntax tree |
| **Binder** | Resolves names, types, scopes; desugars `for-in` and channel iteration |
| **Lowerer** | Rewrites control flow (loops → labels + gotos, break/continue → jumps) |
| **Emitter** | Generates JVM bytecode via ASM (classes, methods, fields, exception tables) |
| **Evaluator** | Tree-walking interpreter (reference implementation) |

## Project layout

```
src/main/java/
├── Main.java                           Entry point & CLI
└── codeanalysis/
    ├── syntax/                         Lexer, Parser, AST nodes
    ├── binding/                        Binder, BoundScope, TypeResolver
    ├── lowering/                       Lowerer (control flow rewriting)
    ├── emitting/                       ASM bytecode emitter
    ├── Evaluator.java                  Interpreter
    ├── SiyoActor.java                  Actor runtime (mailbox, event loop)
    ├── SiyoChannel.java                Channel (SynchronousQueue / LinkedBlockingQueue)
    ├── SiyoRuntime.java                Compiled-code runtime helpers
    ├── BuiltinFunctions.java           37 stdlib functions
    └── ...                             Types, diagnostics, symbols
src/test/java/                          1402 tests
examples/                               29 standalone examples
projects/                               Multi-file projects (siyodb, chat)
```

## Testing

```bash
mvn test
```

1402 tests across 8 suites — lexer, parser, binder, evaluator, compilation (bytecode-vs-interpreter parity), syntax rules, and source text handling. The compilation test suite verifies that every program produces identical output in both the bytecode and interpreter paths.

## Documentation

- **[GRAMMAR.md](GRAMMAR.md)** — Complete language grammar, type system, and built-in reference
- **[FUTURE.md](FUTURE.md)** — Roadmap from 0.1.0 through 1.0.0
- **[docs/ACTOR_DESIGN.md](docs/ACTOR_DESIGN.md)** — Actor model design rationale

## Known limitations (0.1.0)

These are tracked for future releases — see [FUTURE.md](FUTURE.md):

- No implicit `int + double` promotion (use `toDouble(n)`)
- No map/set literal syntax — use `map()` / `set()` + method calls
- No hex literals, no `do-while`
- Closure captures are read-only
- No generics, no interfaces

## Contributing

This is an experimental language project. Issues and discussions are welcome at [github.com/urunsiyabend/SiyoCompiler/issues](https://github.com/urunsiyabend/SiyoCompiler/issues).

## License

This project is provided as-is for educational and experimental purposes.
