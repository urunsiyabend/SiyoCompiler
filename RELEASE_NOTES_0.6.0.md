# Siyo 0.6.0

0.5.0 ended with a list of what was still missing, and named two items as the
largest: a result that is one of several shapes had to be faked with a record
full of flags, and a declared function type was parsed and thrown away, so a
callback of the wrong shape failed at run time instead of at compile time.

Both are fixed here, and the collection library that a language with closures
should have arrived with them.

1,733 tests pass, up from 1,565. Every language change is exercised on both
backends, because a program that means one thing interpreted and another
compiled is the defect this project cares most about.

---

## Sum types

**A `type` declaration names a closed set of variants.**

```siyo
type Result = Ok(int) | Err(string)
type Option = Some(int) | None
type List = Cons(int, List) | Nil
```

A variant is constructed by writing it. A variant without a payload is a value
on its own, and any variant may be written qualified by its type:

```siyo
imut good = Ok(5)
imut empty = None
imut also = Result.Ok(5)
```

A value carries its type, its variant and its payload; two values are equal when
all three are, and a value prints the way it was written — `Ok(5)`, `None`. A sum
type is usable everywhere a struct is: as a parameter, a return type, a local
annotation, an array element, a struct field, and as the payload of another
variant, including its own, so a recursive type works.

`type` is a **contextual keyword**. A variable, field or function named `type`
still compiles; the word only introduces a declaration when followed by a name
and `=`.

A variant that the type does not declare, a payload of the wrong size or type, a
variant declared twice, and an unknown payload type are all reported.

**A sum type crosses a module boundary intact.** A type declared in one module is
constructed, matched and compared in another, keeps working two import hops
away, and is still checked for exhaustiveness in the importing file.

## Matching

**A match arm selects a variant and binds its payload.**

```siyo
fn describe(r: Result) -> string {
    match r {
        Ok(value) => "ok " + toString(value),
        Err(message) => message,
    }
}
```

A slot is discarded with `_` — `Pair(_, name)` — and a pattern may be qualified
by its type. A bound payload keeps the identity its declaration gave it, so a
struct payload is read field by field and a recursive type is walked by
recursion.

An arm whose arguments are not plain names is still a value to compare against,
so `Ok(1) => ...` keeps its old meaning and existing matches are unaffected.

**A match over a sum type is checked for exhaustiveness.** The set of variants is
closed, so a missing one is a hole the compiler can see:

```
config.siyo(4, 5): This match on 'Config' does not cover Missing

  help: add an arm for each, or a _ arm
```

A pattern for a variant of a different type, and one binding the wrong number of
slots, are reported too.

**A bare variant name that two types declare is an ambiguity.** It used to
resolve to whichever type was registered first, so importing a module whose type
shared a variant name silently changed what a construction meant. It is now
reported, with the qualified spelling to use.

## Declared function types

**A signature is checked instead of discarded.** `fn(int) -> int` used to be
parsed and dropped, so this compiled and then failed at run time:

```siyo
fn apply(f: fn(int) -> int, n: int) -> int { f(n) }
apply(fn(a: int, b: int) -> int { a + b }, 1)
```

```
apply.siyo(2, 36): Expected fn(int) -> int, but got one taking 2 arguments
```

Arity, parameter types and return type are checked at a call site, at a local
declaration and through a struct field. A call through a name with a known
signature is checked the same way, and **the call now has the declared return
type** rather than an erased `object`, so its result can be assigned and passed
on without a conversion:

```siyo
imut double: fn(int) -> int = fn(x: int) -> int { x * 2 }
imut n: int = double(4)          // no toString/parseInt round trip
```

A bare `fn` still accepts any closure. A nested type —
`fn(int) -> fn(int) -> int` — round-trips instead of being discarded, and
`fn()[]` stays an array of functions while `fn() -> int[]` is a function
returning an array.

## Collections

**map, filter, reduce and forEach.**

```siyo
imut evens = filter(ns, fn(x: int) -> bool { x % 2 == 0 })
imut squares = map(evens, fn(x: int) -> int { x * x })
imut total = reduce(squares, fn(acc: int, x: int) -> int { acc + x }, 0)
forEach(ns, fn(x: int) { println(toString(x)) })
```

They are implemented once in the runtime and dispatched through the closure's own
origin class, so the two backends agree and a closure from another module works.
`filter` keeps the element type it was given; `map`'s elements are whatever the
function returned. `map()` with no arguments is still the map constructor — the
two are overloads.

**A map is indexed.** `m[key]` reads and `m[key] = value` writes, with any key
type, and a missing key reads as `null`. Reading was rejected outright, and
writing compiled into a `List.set` that failed at run time, so a map had to be
used through `.get` and `.set`.

## Closures

**A closure shares the mutable locals it captures.** A capture was by value, so a
write inside a closure was invisible outside it — discarded silently before
0.5.0 and a compile error in it. The variable now lives in a one-element cell
that the closure captures, so a write on either side is seen by the other:

```siyo
mut total = 0
forEach(ns, fn(x: int) { total = total + x })
println(toString(total))
```

That makes the counter pattern work in both directions, including a counter that
outlives the function that created it. An immutable local is still captured by
value, and writing to one is still an error. A task started with `spawn` receives
the cell too, so a captured variable means the same thing in both.

## Literals

**Long and hexadecimal integers.**

```siyo
imut ms: long = 86400000L
imut mask = 0xFF
imut wide: long = 3000000000        // widens on its own
```

A decimal or hexadecimal literal that does not fit in an `int` widens to a
`long` instead of being reported as invalid, which is what the lexer did before.
A value that fits in neither is reported as out of range, and a fractional part
carrying an `L` suffix is rejected. Number diagnostics name the Siyo type —
`int`, `long`, `float` — rather than printing a Java class.

## std/json

**A parse failure is a value.** `json.parse("not json")` returned an empty map,
the same value as the valid document `{}`, so a caller could not tell the two
apart.

```siyo
match json.parse(text) {
    Parsed(settings) => use(settings),
    Invalid(why) => report(why),
}
```

The parser behind it validates instead of guessing: a missing colon, an
unterminated string or object, a trailing comma, a bad escape and trailing input
past the document are all reported with the position they were found at. It also
reads two things it used to drop — `\u` escapes and exponent notation.
`json.parseOrEmpty` keeps the old lenient behaviour where a caller wants it.

## Backend divergences fixed

- **A value-producing match that matched no arm pushed a null** whatever the
  match's type was, so a match on an `int` produced a method that failed
  verification. The fall-through value now has the match's own type. This was
  reachable before sum types, through a match with no `_` arm.
- **The interpreter bound the wrong error for a Java call that throws.**
  Reflection wraps the exception, so `catch e` bound
  `"java.lang.reflect.InvocationTargetException"` while the compiled path bound
  the real message. The cause is now unwrapped, so both paths agree.

## Still open

- **Errors carry no payload.** `error(msg)` raises text and `catch e` binds text.
  Returning a sum type is the way to carry a status code today; `throw` and a
  typed `catch` are 0.7.0.
- **No generics**, so a sum type is written per payload type: a `Result`
  carrying a map and one carrying an int are two declarations. No reflection
  over struct fields either, so a struct is converted to a map by hand before it
  is serialised.
- **No interfaces**, and **no visibility control** — every top-level declaration
  in a module is exported.
- **No module aliasing**, no method chaining on a call result, no set literals,
  no `do-while`, and no implicit `int` to `float` promotion.

## Upgrading

Three changes can turn a program that compiled under 0.5.0 into one that does
not. Each is a case where 0.5.0 accepted something that could not work:

- **`json.parse` returns a sum type.** A caller that used the map directly needs
  either a `match` or `json.parseOrEmpty`, which behaves the way `parse` used to.
- **A declared function type is now checked.** A callback whose arity or types
  did not match its declaration used to compile and fail at run time.
- **A match over a sum type must be exhaustive** or carry a `_` arm.

A write to a captured mutable variable goes the other way: 0.5.0 rejected it, and
it now compiles and does what it says.
