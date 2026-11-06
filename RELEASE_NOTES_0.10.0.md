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

2,937 tests pass, up from 2,743. DataKit is part of `mvn verify`: its CLI
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
  maps, sets, generic structs and function types, and names its result with
  them: struct and sum type arguments carry through; number, bool and string
  ones are erased as in 0.9.0 (see the rule below).
- The same holds for a function from another module, for a module-qualified
  call and for a call written as a method. An imported generic function used to
  lose its type parameters altogether.

**A payload keeps its written type name.** With `type R = T(Array<S>) | ...`,
`T(xs) => xs[0].n` was rejected. A payload now carries its written type, and a
generic sum type's struct argument, so `Some(r) => r.name` on an `Option<Row>`
reads a `Row`.

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

**A type argument names a value; it does not change it.** Four rounds of
independent review found that substituting a value's class from its type
arguments made programs 0.9.0 ran consistently crash compiled, or print `1.0`
against `1`. The triggers were:

- a missing map key, or a null or erased argument;
- a value written through an erased alias;
- a disagreeing argument;
- an annotation on an existing array.

What ships is a rule, not a patch per finding:

- **Results, payloads and elements.** A generic call's result, a payload and an
  array element keep the class 0.9.0 gave them. A struct or sum type argument
  supplies its name, which is how `keep(rows, p)[0].name`, `g.items[0].name` and
  `Some(r) => r.name` read members. The member read casts, so a value of
  another kind fails there on both backends.
- **Number, bool and string arguments.** Results and payloads built from them
  are erased as in 0.9.0. `wrap(1)` holds erased values, and `Some(v)` on an
  `Option<int>` binds an erased `v`.
- **Uncertain arguments.** An erased argument, `null`, or two that disagree
  (`both(1, 2.5)`) erase the parameter.
- **Bare results.** A bare type-parameter result keeps 0.9.0's class:
  `identity(5) + 1` still adds.
- **Generic structs keep their type arguments.** This holds for scalars and
  containers alike: `mk(1.5)` is a `Box<float>`, `[mk(0.5)]` is an
  `Array<Box<float>>`, and `b.value["a"]` on a `Box<Map<string, int>>` is an
  int. Every read at a declared type is checked the same way on both backends:
  a field, an array element or map value, or a payload. The interpreter
  applies the compiled read's widening, null check and kind check, so a value
  stored through an erased `Box` fails alike instead of reading wrongly in one.
- **Field assignment.** Assigning to a generic struct's field is checked and
  widens a literal as construction does: `b.value = [3, 4]` on a
  `Box<Array<float>>` holds floats.
- **A payload is checked against an annotation.** `imut o: Opt<R> = Some("abc")`
  is reported.
- **Annotated arrays take only their kind.** An array declared by an annotation
  cannot be given a value of another kind: `push(xs, "two")` is reported.
- **Returning a number from a function returning `object`.** This failed JVM
  verification in 0.9.0, and the value is now boxed.

A generic struct's type argument also survives:

- a plain alias (`imut c = b`);
- `filter`;
- an imported generic call;
- a field taken into a local;
- a bare `-> T` bound through a `Box<T>` parameter.

A value returned from a function or lambda declaring a type is checked by the
interpreter as the compiled return cast checks it, so a `Box<T>` function
returning an erased string fails on both backends. An actor's method is left
as 0.9.0 had it: compiled, a method returning an erased value of the wrong type
fails its cast in the actor's mailbox and leaves the caller waiting, exactly as
in 0.9.0. A `Box<T>` getter (`g(b: Box<T>) -> T`, `-> Array<T>`) gives an
array's or map's element type from the struct's argument, whether the `Box`
was written, inferred, or made by a lambda declared to return one. One rule
covers every path. A value whose type came from a generic struct's argument
(a field, a getter bound through a `Box`, and anything moved from one: a member
read, even through a plain struct, an index, `filter`, `pop`, a variable, a loop
item, map key or set element, an `if` or `match` of such values, an exported
module variable whose structs are the same ones in the importer) keeps its
whole known type at every depth, through aliases and imports. Passing one to a
function parameter or a plain struct field declared with a concrete type gives
it that slot's type under 0.9.0's rules, as any value. Ordinary generic results otherwise stay erased. The one
visible change outside generic structs is that a generic call over a nested
literal of struct rows, `firstOf([xs, xs])` with `xs = [R { n: 1 }]`, now
knows its elements are `R`s. That was a compile error ("Object does not have
members") in 0.9.0, and it now runs the same on both backends.

The interpreter's checked reads also change some of 0.9.0's own divergences.
Where 0.9.0 already split by backend, because an element or value read at a
declared number type was null or of another kind (an `Array<int>` holding 2.5,
a `Map<string, int>` value that is null), the interpreter now fails as compiled
code always did. An independent sweep of 840 generated programs found no change
to compiled output and no new split. In 446 of them, which 0.9.0 already split,
the interpreter now follows compiled. `m["absent"] == null` is still true on
both. Not every inherited split is closed: `wrap_mutate` (a string pushed into
an int array through an erased alias), erased arithmetic such as
`keep([1.5])[0] * 2`, and a member write to a non-struct value still differ by
backend, exactly as in 0.9.0. A list of generic structs (`[Box { value: 1 }, Box { value: 2.5 }]`) is an
`Array<Box<float>>`: number kinds join at the wider one. Arithmetic on an erased value still
follows 0.9.0's rule: compiled, it is read at the other operand's type, so
`o * 2` with `o` an erased `1.5` raises compiled and gives `3.0` interpreted. A
generic struct whose type argument is erased (never known) reaches that rule.

`DataKitRegressionTest` runs the reviewers' reproducers on both backends. Each
reads as it did in 0.9.0, or, for a type-wrong program or a new feature, fails
the same way on both or is reported at compile time.

## Standard library

**`std/csv`**, written in Siyo:

- `csv.parse(text)` follows RFC 4180 and gives `Table(rows)` or
  `BadCsv("line N: ...")`. Each `CsvRow` carries the line it starts on, which a
  quoted line break makes differ from its position.
- `csv.formatRow(cells)` quotes only what needs it, and writes a lone empty
  cell as `""` so it reads back.
- `CsvRow.blank` marks a line with nothing on it. A quoted `""` is a record,
  not a blank line.

**`io.writeNew(path, content)`** creates a file that must not exist,
exclusively. An existing file, or a symbolic link even if dangling, is never
opened. Exclusive creation is not atomic publication: a write that fails
part-way leaves the partial file it created. It is never removed by name, since
the path may by then hold another process's file. Checking that a path is free and then writing it can be raced into
clobbering whatever a link points at, and DataKit's `--out` did exactly that
until review.

**`io.readText(path)`** returns a file exactly as written. `io.readFile` reads
lines and joins them with `"\n"`, so a CR inside a quoted CSV field came back as
LF. DataKit's oracle caught that. `readFile` is unchanged.

**`strings.formatFloat(x, digits)`** writes fixed decimals, rounding half up as
the number reads: `formatFloat(2.675, 2)` is `2.68`.

**`strings.isFloat(s)`** says whether `s` is a finite decimal number and
nothing else, in one linear pass. A first version used a regular expression
that took seconds on a long run of digits. `parseFloat` answers `0.0` for what it cannot read, and accepts
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
  - `toFloat` does not take a `long`, and unary minus is not defined for a
  `long` (`-x`): write `0L - x`. Numeric `as` is planned.
- A lambda parameter typed with a comma inside type arguments
  (`fn(x: Map<string, float>)`) is read as two parameters, as in 0.9.0.
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
