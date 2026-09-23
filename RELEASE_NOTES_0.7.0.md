# Siyo 0.7.0

0.6.0 gave the language a result that is one of several shapes and a function
type that is actually checked. It left a list of things a program still could
not say: an error could only be text, every declaration a module wrote was
exported whether or not it was meant to be, a container could not say what it
held, and no function could take "anything that can describe itself".

This release takes that whole list.

2,184 tests pass, up from 1,733. Every language change is exercised on both
backends, because a program that means one thing interpreted and another
compiled is the defect this project cares most about.

---

## An error is a value

**`throw` raises anything.**

```siyo
type Failure = NotFound(int) | Refused(string)

fn fetch(id: int) -> string {
    if id < 0 { throw NotFound(404) }
    "record " + toString(id)
}
```

Whatever is thrown is what the handler binds. `error("boom")` still raises its
message, so a handler that expected text keeps getting text.

**`catch e: Failure` gives the binding a declared type.**

```siyo
try {
    println(fetch(-1))
} catch e: Failure {
    println(match e {
        NotFound(code) => "missing: " + toString(code),
        Refused(why) => "refused: " + why
    })
}
```

The annotation is what makes the match checkable: leaving out `Refused` is a
compile error rather than a null at run time. Without an annotation the catch
variable is the erased payload, which is what an unannotated `catch e` has
always been.

**A Java exception reports its type.**

A message-less Java exception used to bind `null`, which told the reader
nothing. It now binds its type name, and one with a message still binds the
message. The interpreter and the bytecode backend agreed on neither before;
both now call the same runtime helper.

---

## A module says what it exports

**`pub` marks a declaration as exported.**

```siyo
pub fn greet(name: string) -> string { "hello " + helper(name) }

fn helper(name: string) -> string { trim(name) }
```

`greet` crosses a module boundary; `helper` does not. It applies to a function,
a struct, an enum, a sum type, an interface, an actor and a module-level
variable alike.

**A module that marks nothing exports everything.** Privacy is opted into per
module, so no module written before visibility existed changes meaning. The
first `pub` in a file is what switches that module to exporting only what is
marked.

**A private name is reported as private.**

```
'api.helper' is private to module 'api'

  help: write pub before its declaration to export it
```

Reporting it as missing would have sent the reader looking for a typo.

The standard library now uses this: every function `std` means to offer is
`pub`, and `std/testing`'s two hook variables are no longer part of its surface.

---

## A module may be reached under another name

```siyo
import "std/math" as m

fn main() { println(toString(m.abs(-4))) }
```

The alias is local to the importer — the module's code still lives where it was
emitted — and the same module may be imported under its own name and under an
alias in the same file.

**Two modules whose paths end in the same segment no longer collide.**
`left/util` and `right/util` were both emitted as a class named `Util`, so
whichever loaded first answered for both. A module's JVM class name is now
derived from its whole path.

---

## Generics

**A container says what it holds.**

```siyo
imut xs: Array<int> = [1, 2, 3]
imut m: Map<string, int> = {"a": 1, "b": 2}
imut tags: Set<string> = #{"a", "b"}

println(toString(m["a"] + m["b"]))   // 3, with no conversion
```

`Array<T>` holds what `T[]` holds. A declared map's values keep their type when
indexed, so `m["a"] + 1` needs no cast. Type arguments nest:
`Map<string, Array<int>>` reads as two arguments, and the `>>` that closes it is
split where it is written.

**A sum type is declared over a type parameter.**

```siyo
type Option<T> = Some(T) | None

fn describe(o: Option<int>) -> string {
    match o {
        Some(v) => "got " + toString(v),
        None => "nothing"
    }
}
```

One declaration serves every payload type. `Option<int>` and `Option<string>`
are the same declared type with the parameter erased, and a match over either is
still checked for exhaustiveness.

**A generic function keeps the type it was given.**

```siyo
fn identity<T>(x: T) -> T { x }
fn first<T>(xs: T[]) -> T { xs[0] }

println(toString(identity(5) + 1))     // 6
println(toString(first([7, 8, 9]) + 1))  // 8
```

The function is compiled once with its type parameters erased. The call site is
where the parameter is known, so that is where the result gets its real type —
without which every use would need a conversion.

**A struct's fields are readable.**

```siyo
fn describe(v: object) -> string {
    mut out = typeName(v) + ":"
    for f in fields(v) { out = out + " " + f + "=" + toString(field(v, f)) }
    return out
}
```

`fields`, `field`, `setField`, `toMap` and `typeName` mean a serialiser is
written once rather than per struct. `json.stringify(toMap(user))` is the whole
of serialising a struct.

---

## Interfaces

```siyo
interface Shape {
    fn area() -> int
    fn name() -> string
}

struct Square { side: int }
struct Rect { w: int, h: int }

impl Shape for Square {
    fn area(self) -> int { self.side * self.side }
    fn name(self) -> string { "square" }
}

impl Shape for Rect {
    fn area(self) -> int { self.w * self.h }
    fn name(self) -> string { "rect" }
}

fn main() {
    imut shapes: Array<Shape> = [Square { side: 3 }, Rect { w: 2, h: 5 }]
    for s in shapes { println(s.name() + " " + toString(s.area())) }
}
```

A method could previously only be called on a value whose concrete struct the
compiler already knew, so no function could take "anything that can describe
itself".

**Dispatch is on the struct, and it is closed.** Every struct implementing an
interface is known when the call is emitted, so the call compares against the
receiver's struct name and then makes an ordinary static call — no reflection,
and the verifier still sees a concrete method on each branch. A compiled struct
now carries its own name to make that comparison possible, which also means a
compiled struct prints the way an interpreted one does.

**Conformance is checked.**

```
'Point' does not implement 'Printable': it has no method 'describe'

  help: add fn describe to the impl block
```

A method with the wrong arity or the wrong return type is reported the same way,
and a call through an interface no struct implements is reported rather than
left to fail at run time.

An interface crosses a module boundary when it is `pub`, and the implementors it
picked up travel with it.

---

## Ergonomics

**A narrower number widens to meet a wider one.**

```siyo
println(toString(1 + 2.5))          // 3.5
println(toString(1 < 2.5))          // true

fn half(x: float) -> float { x / 2.0 }
println(toString(half(5)))          // 2.5

fn three() -> float { 3 }           // 3.0
```

Mixed arithmetic used to be rejected outright, and enumerating an operator per
pair of types had covered int/long only partly and int/float not at all. The
narrower operand is now widened and the operator looked up at the common type,
which covers every numeric mix at once — including the `int * long` that was
missing. Widening also applies at a parameter and at a return;
`fn f() -> float { 3 }` used to compile to a class that failed verification.

Narrowing is still written out: passing a `float` where an `int` is declared is
an error.

**`do { } while c`.**

```siyo
mut i = 0
do { i = i + 1 } while i < 5
```

`break` and `continue` behave inside one as they do in any loop.

**Set literals.**

```siyo
imut tags = #{"red", "green", "red"}
println(toString(len(tags)))   // 2
```

`len` is now defined for a map and a set as well as a string and an array; it
used to fail at run time on the other two, and the two backends disagreed about
what it accepted.

**A builtin is written as a method on its first argument.**

```siyo
println("  hello  ".trim().toUpper())
println(toString(xs.filter(isEven).len()))
```

`x.f(a)` is `f(x, a)` when `f`'s first parameter takes `x`. A chain no longer
has to be unwound into nested calls the moment a builtin appears in it. An
imported Java object keeps its own methods: a Siyo function of the same name
does not shadow them.

---

## Two backend divergences fixed

- A compiled struct printed as a raw field map (`{x=1, y=2}`) while an
  interpreted one printed `Point { x: 1, y: 2 }`. Both now print the latter.
- A value-producing `try` expression left a boxed value on the stack where its
  declared type was expected, so `imut n = try { 1 } catch e { 2 }` produced a
  class that failed verification.

---

## Still missing

- A struct is not generic: `struct Box<T>` is not accepted.
- An interface declares methods and nothing else — no default bodies, and an
  interface cannot bound a type parameter.
- `as` casts a Java object; it does not convert between numeric types.
- A struct literal with no fields (`Empty { }`) is not recognised, and a map
  literal written as a function body's tail value is read as a block.
