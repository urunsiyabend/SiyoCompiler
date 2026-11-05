# Siyo 0.9.0

0.8.0 came out of writing one real program. This one came out of writing a
second: TaskFlow (`projects/taskflow`), a multi-module CLI that reads JSON task
files, validates them, finds dependency cycles, plans execution deterministically
and reports status. Everything it hit is recorded with a reproducer in
`projects/taskflow/PAIN_POINTS.md`, and what was the language's fault is fixed.

The planned generics, interface and tooling work moves to 0.10.0: TaskFlow did not
need it, and the release is built from what was demonstrated.

2,418 tests pass, up from 2,243. TaskFlow is part of `mvn verify`: its CLI runs
on both backends under a C locale against goldens, and generated graphs are checked
against rules worked out independently of the application.

---

## The interpreter gave wrong answers without saying so

Four defects made a correct program print a wrong result under `siyoc interpret`,
all silently. If you ran anything recursive there, recheck it.

**A recursive call has locals of its own.** A function's locals were stored with
the globals, so a recursive call overwrote its caller's.

```siyo
fn fact(n: int) -> int {
    imut before = n
    if n == 0 { return 1 }
    imut below = fact(n - 1)
    before * below
}
```

`fact(5)` was `0`. A `for` over a recursive walk stopped after its first call,
because the loop counter was shared too.

**`break` and `continue` leave a `try`.** They raised a NullPointerException inside
the interpreter, which the program's own `catch` then handled as its own error.

**`return` from a match arm stops there.** The rest of the arm still ran, including
its side effects. `break` and `continue` in a block arm used to crash.

A closure nested in a closure, and a function declared inside a function, read outer
locals only because they were global. They now capture them lexically: a nested
function returned from its parent keeps the variables it refers to, and shares a mutable
one with its parent. A closure in a closure reading a local, reading a global or
writing a local, `scope` reading a captured variable inside a lambda, and a `for` inside
a lambda, internal compiler errors in 0.8.0, now also work compiled. A lambda also tells
an inner local from an outer variable of the same name. A nested `fn` using its parent's locals is reported as not compiled
yet; with a same-named global it used to read the global silently.

**Nested functions of one name are separate functions.** Two functions each declaring
a nested `k` shared the last one's body, and a nested `helper` replaced a top-level
`helper`, on both backends. Declaring one twice in a block with one signature is now an
error, and a nested name is no longer reachable from outside its scope, such as
`module.inner()`.

**A declared `long` is a long from the start.** `mut h: long = 0` stayed an int and
wrapped at 2^31.

**Numbers compare by value.** `5L == 5` was false, `NaN == NaN` true and `0.0 == -0.0`
false; compiled had each right.

**A stack overflow can be caught.** Compiled it could; interpreted it ended the
process. Recursion depth still differs by backend: see below.

## Declared types hold

**`Array<T>` with an empty literal.**

```siyo
mut tasks: Array<Task> = []
push(tasks, Task { id: "a" })
println(tasks[0].id)
```

This was rejected, since the element type was taken to be `Array<Task>` itself.

**`pop` returns what the array holds**, rather than an erased value.

**A generic type survives a call and a loop.** `imut s = sets()` keeps
`Map<int, Set<string>>`, and in `for batch in plan.batches { for t in batch { ... } }`
`t` is known. A `for` over a `Map<K, V>` walks `K`s.

**A number stored at a declared type widens.** A struct field, a map slot, an array
element and the elements of a literal (`fn g() -> float[] { [1, 2] }`) typed `long` or
`float` accept an `int`, as a parameter does. Compiled, the value failed when read back.

**`else if` is a value.** `imut x = if a { "p" } else if b { "q" } else { "r" }`
failed JVM verification. So did a void `if/else` ending a match arm.

A block with no value used to count as the int `0`, so a `match` whose value is used
with an arm `{}` beside `1` silently gave 0. It is now reported as mixing void and
value arms. Likewise a function whose tail `if` gives a value in one arm and falls off
the end in the other: `fn a(x: int) -> map { if x == 1 { {"k": 1} } else { {} } }` was
null compiled and the map interpreted, and is now rejected, since `{}` there is an
empty block. Write `map()`. An arm that returns or throws is fine.

**An expression with no value is reported where one is needed:** an initializer, an
argument, an interpolation. Some of these failed JVM verification.

**`break` and `continue` cannot leave a lambda or a spawn.** They were accepted, an
internal error compiled and a jump out of the closure interpreted.

**A typed slot may be compared to null.** `counts["no"] == null` on a
`Map<string, int>` crashed compiled.

## Small additions

**`for` walks a set**, in insertion order, and any erased value by what it turns out
to be. It crashed on both backends.

**A map literal may be a block's tail value.**

```siyo
fn taskJson(t: Task) -> map {
    {"id": t.id, "estimate": t.estimate}
}
```

A brace followed by `key :` opens a map, since no statement starts that way. `{ }`
is still an empty block.

**Anything can be interpolated.** `"deps: $deps"` with an array, a map or a struct
was an operator error reported at line 1, column 1.

**`strings.isInt(s)`** says whether `parseInt` can read a string. `parseInt` itself
still answers 0 for anything else, which is also a number.

## JSON

**`json.parseValue(text)`** reads any document, since a real file may be an array:

```siyo
match json.parseValue(text) {
    Document(v) => println(typeOf(v)),
    Malformed(why) => println(why),
}
```

`json.parse` still wants an object.

**The parser rejects what RFC 8259 rejects.** It accepted a leading zero, a raw
control character in a string, a non-ASCII digit and `\u+123`.

**A duplicate key is an error.** The last value silently won, so a task file with two
`deps` lost one. If a program relied on that, `json.parse` now gives `Invalid` for such
a document and `json.parseOrEmpty` an empty map.

**Hostile nesting is reported.** Beyond 512 levels a document is malformed, instead
of a stack overflow whose message differed by backend.

**Also:** a byte order mark is ignored, an integer too wide for a long reads as a
float, and an error in a multi-line document names its line and column.

**`json.stringify` writes a struct by its fields and a set as an array**, rather than
as the quoted text of how they print. An actor is still its name.

## Standard library performance

`io.readFile` and `strings.join` built their result by repeated concatenation, so a
3.6 MB file took 1m48s. `strings.repeat` too. `readFile` also dropped leading blank
lines, which it now keeps.

---

## Still missing

Found by TaskFlow and deliberately left for later (see PAIN_POINTS.md):

- Recursion depth differs by backend on the default stack: `deep(10000)` works
  compiled, `deep(1000)` overflows interpreted. Both raise a catchable
  `StackOverflowError`. A program whose depth comes from its input should keep its
  own stack. A 512 MB program stack was tried and withdrawn: runaway recursion went
  from 50 MB to over 2 GB, and `spawn` and `siyoc compile` output did not get it.
- A `try` expression where the JVM's operand stack is not empty, such as
  `"x" + try { ... } catch e { ... }`, fails verification compiled. Assign it to a
  variable first.
- `imut v = m["missing"]` on a `Map<string, int>`: compiled NullPointerException,
  interpreted `null`. Ask with `m.has(k)` first.
- An uncaught `throw` prints a Java stack trace, a different one per backend.
- Closure conversion has gaps compiled. A `fn` in a function that uses that function's
  locals is reported as not compiled yet. Any loop around `scope { spawn { } }` is an
  internal compiler error. A lambda reading a top-level `mut` or capturing `self`, one
  created in a `spawn`, and one capturing a `mut` parameter fail at load or run time. All
  work interpreted; PAIN_POINTS.md D10 lists what does compile.
- A float read at a declared long through an erased value raises compiled and holds
  the float interpreted.
- A value-returning function that reaches its end with no value gives the type's
  default compiled and the last value evaluated interpreted.

And still open from before: a module-qualified type annotation, a mixed array literal
annotated `object[]`, `error(msg)` not diverging, no date/time module, generic
structs, interface default methods and bounds, `Empty { }`, and numeric `as`.
