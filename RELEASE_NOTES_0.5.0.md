# Siyo 0.5.0

This release is driven by evidence. Morioh — a 3,000-line HTTP library with a
server, a client, routing and middleware — was written in Siyo 0.4.0
specifically to find out where the language breaks under real use. It produced
32 reproducible defects. Most of them are fixed here.

Two themes dominated the findings, and both are addressed:

**Modules did not actually provide namespaces or state.** A module leaked the
functions it imported into its own exports. Two modules that each declared
`parse` collided; a module-level variable stopped working as soon as its module
was imported through another module. Both are the same bug, and both are gone.

**The compiler failed silently or opaquely.** Four defects produced programs
that compiled, ran, and gave a wrong answer. Eleven surfaced as raw Java stack
traces with no Siyo source location. Silent wrongness is now reported, and an
internal failure is now a located diagnostic instead of a stack trace.

1,565 tests pass, up from 1,499. Morioh's own 102 tests — which exercise real
sockets, concurrency and malformed HTTP — pass against this compiler unchanged.

---

## Modules

**A module is a namespace again.** Every function carries the file that declared
it, so two modules may both declare `parse` without colliding, and a
module-qualified call resolves to the module it names.

```siyo
// mine.siyo
fn parse(raw: string) -> string { "mine:" + raw }

// mid.siyo
import "mine"
import "std/json"
fn go(s: string) -> map { json.parse(s) }   // no longer hijacked by mine.parse
```

This also affects methods: a module function called `get` no longer shadows
`someValue.get()` elsewhere in the program.

**Module-level variables work at any import depth**, and are exported:

```siyo
// status.siyo
imut OK = 200
mut requests = 0
fn count() { requests = requests + 1 }
```
```siyo
import "status"
fn main() {
    println(toString(status.OK))        // 200 — previously not exported at all
    status.count()
    println(toString(status.requests))  // 1
}
```

Previously a module variable crashed the compiler as soon as the module was
reached through another module — `IllegalStateException: Variable not declared`.

**A module can be a facade.** Structs already travelled through imports; now
their `impl` methods travel with them, so a library can offer one entry point:

```siyo
import "morioh"

fn main() {
    mut app = morioh.newServer()
    app.get("/", handler)     // Server's methods came with the type
    app.listen(":8080")
}
```

**A compile error inside a module is reported against that module.** It used to
be attributed to the importing file, at a line that file did not have, followed
by a wall of "does not exist" errors for every symbol the failed module would
have exported. Those follow-on errors are now suppressed.

```
before:  main.siyo(6, 7): Name 'undefinedName' does not exist      (main.siyo has 5 lines)
         main.siyo(4, 15): Function 'c.hello' does not exist
after:   c.siyo(2, 22): Name 'undefinedName' does not exist
```

**`siyoc run`, `siyoc test` and `siyoc compile` resolve modules identically.**
Each walks up from the *source file* to find `siyo.toml`, so `import "server"`
now works in `examples/` exactly as it does in `tests/`.

**A file that would collide with a module it imports is rejected at compile
time.** A source file compiles to a class named after it; `client.siyo`
importing the module `client` produced two classes named `Client` and failed at
run time with `NoSuchMethodError`.

## Java interop

**Arrays cross the boundary.** A Siyo array is converted to the Java array a
method expects, and back on return:

```siyo
import java "java.io.ByteArrayOutputStream"
mut out = ByteArrayOutputStream.new()
out.write("Hi".getBytes())     // previously: VerifyError before the first instruction
```

This affected every socket and file write in the language.
`examples/web_server.siyo`, shipped and advertised in this repository, could not
serve a request; it works now.

**Erased values behave numerically.** A value returned by a dynamically
dispatched Java call is boxed; comparing it used to emit unverifiable bytecode,
or — inside a loop — crash ASM's frame computation:

```siyo
fn countBytes(inp: object) -> int {
    mut n = 0
    mut done = false
    while !done {
        imut b = inp.read()
        if b < 0 { done = true } else { n += 1 }
    }
    n
}
```

**Primitives are boxed when they reach an `object`.** `show(5)` where
`fn show(v: object)` no longer fails verification — which also means a closure
survives being stored in a Java collection.

**Nested Java classes are usable.** Both spellings resolve, and the innermost
name is what the class is called:

```siyo
import java "java.net.http.HttpRequest.BodyPublishers"
import java "java.net.http.HttpResponse.BodyHandlers"
```

Previously such a class bound to the name `HttpRequest$BodyPublishers`, which is
not an identifier, so it could never be referred to. This is why
`java.net.http` was unreachable from Siyo.

## Language

**A block's value is its tail, for every form that has one.** An `if`/`else` or
a `try`/`catch` at the end of a function is now its return value:

```siyo
fn reason(code: int) -> string {
    if code == 200 { "OK" } else { "?" }     // previously returned null
}
```

**A `try` used as an expression may contain control flow.** It previously
aborted code generation with `UnsupportedOperationException`.

**Enum members may carry explicit values**, which is what modelling a wire
protocol needs. A member without one continues from the previous:

```siyo
enum Status { OK = 200, CREATED = 201, NOT_FOUND = 404, GONE }   // GONE is 405
```

`enum E { A = 1 }` previously compiled and silently miscounted every ordinal
after the first.

**A local type annotation is binding.** It gives the variable its type and the
initializer is checked against it:

```siyo
imut x: string = 5          // error: Cannot convert type Integer to String
imut n: int = erase(41)     // narrows an erased value — this is what it is for
imut wide: long = 5         // widens
```

The annotation used to be parsed and discarded, so `imut x: string = 5`
compiled and `x` was an int.

**Closures.** A struct field may be declared `fn`, a closure held in an erased
value is callable, and a closure-valued field can be called directly:

```siyo
struct Route { pattern: string, handler: fn }

mut r = Route { pattern: "/", handler: fn(x: int) -> int { x * 2 } }
println(toString(r.handler(21)))
```

Calling a field that does not hold a function is now a compile error naming the
field and its struct, rather than a run-time failure naming the struct's
internal representation.

A lambda may also stand as a block's tail expression — `fn make(f: int) -> fn
{ fn(x: int) -> int { x * f } }` — and a captured variable now works inside an
array, map or struct literal.

**A write to a captured variable is an error.** Captures are by value, so the
write could never be seen; it used to be discarded in silence and the program
ran on with a wrong answer.

```
Cannot assign to 'counter': it is captured by a closure and captured variables are read-only
```

**`send` is contextual.** It is only meaningful as a statement prefix, so it no
longer reserves the name everywhere — `res.send(body)` is a legal method again.

**`null` compares against every reference type**, collections included:

```siyo
imut m = find("missing")
if m == null { ... }        // previously: operator '==' is not defined for SiyoMap
```

**`toInt` accepts a `long`.** Narrowing a `System.currentTimeMillis()` no longer
needs a round trip through text.

## Concurrency

**`spawn` capture diagnostics say what is true.** An `imut` binding to a struct
was reported as a "Mutable variable", and writing through a captured struct
skipped the check entirely and crashed the emitter. Both now produce an accurate
message:

```
'c' cannot be captured by a spawn block: a struct's contents are mutable and
would be shared between threads

  help: consider one of these alternatives:
    - keep the state in an actor and call it from the task
    - send a copy over a channel: ch.send(c)
    - capture only the scalar values the task needs
```

The help text no longer suggests `let`, which is not a Siyo keyword.

## Tooling and diagnostics

**`siyoc interpret` runs `init()` and `main()`.** Since 0.3.0 introduced the
Go-style module top level, the interpreter had evaluated only the declarations
in a file and exited printing `0` — every module-style program was a silent
no-op, and the interpreter-versus-compiler comparison the README recommends for
debugging was unavailable.

**An internal compiler failure is a diagnostic.** Eleven of the defects found by
Morioh reached the user as a raw Java stack trace from inside the emitter or
ASM, with no Siyo line number. They are now reported with a source location and
a note that a reduced program is the useful thing to report:

```
tryexpr.siyo(4, 9): Internal compiler error while emitting function 'f':
UnsupportedOperationException: Cannot emit statement: IfStatement

  This is a compiler bug. A reduced program that reproduces it is the
  most useful thing to report.
```

---

## Still open

These were found by the same exercise and are not fixed here:

- **No sum types.** A function whose result is one of several shapes has to
  return a record carrying every field of every case plus flags to say which is
  live. This is the largest remaining expressiveness gap.
- **Errors carry no payload.** `error(msg)` raises text and `catch e` binds text,
  so an error cannot carry a status code or be matched on by type.
- **`std/json` cannot report a parse failure.** `json.parse("not json")` returns
  an empty map — the same value as the valid document `{}`.
- **A declared function type is not checked.** `fn(int) -> int` is parsed and
  discarded, so a handler with the wrong arity fails at run time.
- **No generics or struct reflection**, so a struct must be converted to a map by
  hand before it can be serialised.
- **No long literals.** Wide values come from `toLong()`.

## Upgrading

Two changes can turn a program that compiled under 0.4.0 into one that does not.
Both are cases where 0.4.0 accepted something meaningless:

- A **write to a captured variable** is now an error. It previously compiled and
  did nothing.
- A **local type annotation** is now checked. `imut x: string = 5` previously
  compiled with `x` an int.

Everything else in this release either fixes a crash or accepts a program that
0.4.0 rejected.
