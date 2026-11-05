# Siyo 0.10.0

Like 0.8.0 and 0.9.0, this release came out of writing one real program:
DataKit (`projects/datakit`). DataKit validates CSV and JSON data files against
a schema, then transforms, groups and profiles the rows that pass. Its data
handling is a set of typed, generic pipeline functions, which is where Siyo
gave way. Everything DataKit hit is recorded with a reproducer in
`projects/datakit/PAIN_POINTS.md`. Each finding was checked against 0.9.0
before being called a limitation, and what was the language's fault is fixed.

The roadmap's interface and tooling items move to 0.11.0, since DataKit did
not need them. The generic structs it did need are here.

2,858 tests pass, up from 2,743. DataKit is part of `mvn verify`: its CLI
runs on both backends under a C locale against goldens, and against generated
data checked by an RFC 4180 reader and aggregations written in Java from the
README, independently of the app.

---

## Generic data APIs

**Generic structs.**

```siyo
struct Group<T> { key: string, items: Array<T> }

fn groupBy<T>(xs: Array<T>, key: fn(T) -> string) -> Array<Group<T>> { ... }

imut groups = groupBy(rows, fn(r: Row) -> string { r.dept })
println(groups[0].items[0].name)
```

`struct Box<T>` used to crash the parser with a Java stack trace.

- A generic struct is erased at run time, as a generic sum type is. A literal
  takes its type arguments from its fields, an annotation may state them, and a
  field reads at the value's arguments.
- `Box<float>` given `3` holds `3.0` on both backends.

**A generic call keeps its whole result type.** `fn keep<T>(xs: Array<T>, ...)
-> Array<T>` returned an erased array, so `keep(rows, p)[0].name` was rejected,
and `Map<string, Array<T>>` likewise. Only a bare `-> T` kept its type.

- A call now matches its argument types against the declaration through arrays,
  maps, sets, generic structs and function types, and substitutes the result.
- The same holds for a function from another module, for a module-qualified
  call and for a call written as a method. An imported generic function used to
  lose its type parameters altogether.

**A payload keeps its written type.** With `type R = T(Array<S>) | ...`,
`T(xs) => xs[0].n` was rejected. A generic sum type's struct, collection or
string payload is now read at the matched value's type argument, so `Some(r)`
on an `Option<Row>` binds a `Row`.

## Type rules

**A signature may name a type declared further down.** `fn s() -> Later` above
`struct Later` had a null type, and `s().n` was rejected.

**More numbers widen.** `I(2)` where `I` carries a `long` was rejected, and so
was `lib.half(5)` against another module's `half(x: float)`, though `half(5)`
in the same file was accepted. Both now widen, as a parameter does.

**A match that ends a function returning `object` may give values of different
types.** This is how a sum type is turned into JSON values:

```siyo
fn jsonValue(v: Value) -> object {
    match v { IntV(n) => n, Text(s) => s, Missing => null }
}
```

- This shape was rejected with "cannot mix void and value arms", though no arm
  is void.
- Anywhere a type is declared, the arms must still agree, and the message now
  names the two types.
- Allowing mixed arms everywhere was tried and withdrawn during development: it
  turned 0.9.0's compile error for `imut x: int = match ... { _ => "no" }` into
  a silent `no1` interpreted.

**Declared types are trusted only where they were checked.** Two rounds of
review found that the new type substitutions read values at types nothing had
checked. Programs that 0.9.0 ran consistently crashed compiled, or printed
`2.0` against `2`. What ships keeps 0.9.0's behaviour for those programs, or
reports them at compile time:

- **Number and bool payloads.** A generic sum type's number or bool payload
  stays erased, as in 0.9.0, since `Some(m[k])` may hold null. Narrow it with a
  typed variable: `imut k: int = v`. Struct, collection and string payloads are
  read at their type argument.
- **Number and bool results.** A generic call's number or bool result is
  unboxed only when its type parameter comes from a parameter or an array
  element, as in 0.9.0. One bound through a map, such as
  `get<K, V>(m: Map<K, V>, k: K) -> V`, stays erased, so a missing key reads as
  null on both backends.
- **Arguments that disagree.** `both(1, 2.5)` erases `T` rather than taking the
  first argument's type.
- **Arrays.** An array declared by an annotation or by a generic result
  (`wrap(1)` is an `Array<int>`) cannot be given a value of another kind:
  `push(xs, "two")` is reported. An array only inferred from a literal, a plain
  `float[]` payload, and a field taken into a variable read as in 0.9.0.
- **Generic struct fields.** A field is checked on assignment, so `b.value = 2.5`
  on a `Box<int>` is reported. A value of the wrong kind that reaches a field
  through an erased `Box` fails when read, on both backends.
- **Returning a number from a function returning `object`.** `return n` there
  failed JVM verification in 0.9.0. It is now boxed.

## Standard library

**`std/csv`**, written in Siyo:

- `csv.parse(text)` follows RFC 4180 and gives `Table(rows)` or
  `BadCsv("line N: ...")`. Each `CsvRow` carries the line it starts on, which a
  quoted line break makes differ from its position.
- `csv.formatRow(cells)` quotes only what needs it.

**`io.readText(path)`** returns a file exactly as written. `io.readFile` reads
lines and joins them with `"\n"`, so a CR inside a quoted CSV field came back as
LF. DataKit's oracle caught that. `readFile` is unchanged.

**`strings.formatFloat(x, digits)`** writes fixed decimals, rounding half up as
the number reads: `formatFloat(2.675, 2)` is `2.68`.

**`strings.isFloat(s)`** says whether `s` is a finite decimal number and
nothing else. `parseFloat` answers `0.0` for what it cannot read, and accepts
`NaN`, `Infinity` and `1f`.

---

## Still missing

Found by DataKit and left for later (see PAIN_POINTS.md):

- **A struct reached through another module.** A struct field typed with a
  struct from a module the reader did not import cannot be walked: `for g in
  p.items { g.key }`. Import that module too.
- **Type names from two modules.** Two imported modules that export a type of
  one name collide, and the error messages are confusing. Module-qualified type
  names are the fix.
- **Small gaps:**
  - `toFloat` does not take a `long`. Numeric `as` is planned.
  - `=> return x` is not a match arm. Write `=> { return x }`.
  - `1e20` is not a float literal.
  - `os.exit` and `error` are not known to diverge.

- A generic struct variable reassigned to a literal of other type arguments
  (`mut b = Box { value: 1 }` then `b = Box { value: "s" }`) is not reported at
  compile time; it fails when the field is read, on both backends.
- A type parameter named like a struct in the same file hides that struct, for
  generic structs as already for generic functions and sum types.

Still open from before: interface default methods and bounds, `Empty { }`,
numeric `as`, a mixed array literal annotated `object[]`, a recursion depth that
differs by backend, a `try` expression as an operand compiled, and the closure
conversion gaps listed in 0.9.0.
