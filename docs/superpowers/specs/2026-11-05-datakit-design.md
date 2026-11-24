# DataKit and Siyo 0.10.0: design

## Intent

Siyo 0.10.0 comes out of a third dogfooding program, as 0.8.0 (LogScope) and 0.9.0
(TaskFlow) did. DataKit (`projects/datakit`) is a useful command-line tool. It
validates CSV and JSON data files against a schema, then transforms, groups and
reports on them. Its data handling goes through typed, reusable pipeline functions.
What the program cannot express, or what Siyo gets wrong while it runs, becomes
the release. Each such finding gets a minimal reproducer and a regression test on
both backends. Anything not fixed is documented as deferred with its reason.

The scope rules come from the brief:

- New language and stdlib features must be additive, and only ones DataKit
  actually needs. That rules out working through the whole 0.10.0 roadmap, and it
  rules out Java wrappers in app code.
- No regressions: before an apparent limitation is called a limitation, it is
  checked against the 0.9.0 jar. A new silent regression is fixed, never deferred.
- Earlier releases taught lessons that every new feature must respect. Capture
  walks must traverse every operand, spawn safety must hold, the JSON oracle must
  be independent of the app, bad CLI values must be rejected, and an existing file
  must never be clobbered.
- Resources stay bounded: no larger stacks, and no recursion whose depth comes
  from the input.

## Findings from probing 0.9.0 (before writing the app)

| # | Finding | Decision |
|---|---------|----------|
| G1 | A generic function returning a type built on `T` loses `T` at the call site: `fn keep<T>(xs: Array<T>, ...) -> Array<T>` then `keep(rows, p)[0].name` is "Type Object does not have members". This also holds for `Map<string, Array<T>>`. Only a bare `-> T` keeps its type. | Fix. A typed pipeline API depends on it. |
| G2 | `struct Group<T> { key: string, items: Array<T> }` crashes the parser with a NullPointerException stack trace. | Fix the crash. Then add generic structs (a 0.10.0 roadmap item), because `groupBy` needs a typed result. |
| G3 | No way to print a float to a fixed number of decimals. Averages print as `0.6666666666666666` or `0.30000000000000004`. | Add `strings.formatFloat(x, digits)`. |
| G4 | `parseFloat("abc")` is `0.0`, so a bad number cannot be told from zero. This is the TaskFlow `isInt` situation again. | Add `strings.isFloat(s)`. |
| G5 | There is no CSV reader in the stdlib. | Write it in Siyo inside DataKit first, then promote it to `std/csv` if it stands on its own. |

More findings will come from writing the app. They go into `projects/datakit/PAIN_POINTS.md`.

## Language: generic structs

```siyo
struct Group<T> { key: string, items: Array<T> }

fn groupBy<T>(xs: Array<T>, key: fn(T) -> string) -> Array<Group<T>> { ... }

imut gs = groupBy(rows, fn(r: Row) -> string { r.dept })
println(gs[0].items[0].name)   // Row known statically
```

- A struct may declare type parameters. Field types may use them, including
  inside type arguments.
- Erasure works as for `type Option<T>`. `Group<Row>` and `Group<int>` are the
  same declared struct at run time, and a field of type `T` is stored erased.
- The type arguments travel with the declared type name. A value written
  `Group<Row>` reads its `items` as `Array<Row>`. Reading a field substitutes the
  arguments into the field's declared type name.
- In a struct literal, `T` is inferred from the field values where it can be. An
  annotation (`imut g: Group<Row> = Group { ... }`) says it explicitly. Where `T`
  cannot be inferred, the field reads erased, as it does today for a `map`.
- `Group` written without arguments is accepted and is erased.

## Language: generic call results keep their type (G1)

At a call to a generic function, the parameter type names are unified with the
argument type names, recursively through `Array<>`, `T[]`, `Map<,>`, `Set<>`,
generic structs and `fn(...) -> ...`. The return type name is then rewritten with
those bindings. The call's declared type name feeds every place a name already
feeds today: indexing, loops, member access and variable inference. A parameter
left unbound reads as `object`.

## Stdlib

- `strings.formatFloat(x: float, digits: int) -> string`. Fixed-point, rounding
  half up from the shortest decimal representation, so `2.675` becomes `2.68`.
  NaN and infinities print as `NaN`, `Infinity` and `-Infinity`. A digit count
  outside 0..17 raises.
- `strings.isFloat(s) -> bool`. True when `parseFloat` reads all of `s` as a
  finite decimal number in plain or exponent form. Java-only spellings such as
  `NaN`, `Infinity`, `0x1p3`, `1f` and `1d`, and surrounding whitespace, are not
  numbers.
- `std/csv`, written in Siyo: `csv.parse(text) -> CsvResult = Table(Array<Array<string>>) | BadCsv(string)`,
  `csv.formatRow(cells: Array<string>) -> string`. It follows RFC 4180. Quoted
  fields may hold commas, quotes (written doubled) and newlines, and both CRLF
  and LF line endings are read. A byte order mark is skipped. Text after a
  closing quote, or an unterminated quote, is reported with its line. A ragged
  row is not a CSV syntax error; the caller decides what to do with it.

## DataKit

```
datakit validate DATA --schema S [--json]
datakit transform DATA --schema S [--where COL=VAL]... [--select A,B] [--sort COL[:desc]] --out FILE [--format csv|json]
datakit group DATA --schema S --by COL [--sum COL] [--json]
datakit report DATA --schema S [--json]
```

- DATA is `.csv` (header row) or `.json` (an array of objects).
- The schema is JSON: `{"columns": [{"name", "type": int|float|string|bool|date, "required", "min", "max", "values": [...]}], "unknown": "reject"|"ignore"}`.
- Modules:
  - `cli`: argument parsing. A value that looks like a flag, a repeated flag, a
    bad `--sort` or `--format`, and a missing value are usage errors (exit 1).
  - `schema`: loading and checking the schema file.
  - `csv`: the reader.
  - `source`: turns DATA into raw rows of text or JSON values, with a line or index.
  - `validate`: raw rows to typed `Record`s, collecting every problem.
  - `pipeline`: generic `keep`, `sortBy`, `groupBy`, `sumBy`, `countBy`, `distinct`.
  - `stats`: column profiles.
  - `render`: text, JSON and CSV output.
  - `main`.
- Exit codes: 0 ok, 1 usage, 2 unreadable or unwritable file, 3 invalid data or
  schema.
- `--out` never overwrites. An existing path is refused, so is an output path
  equal to an input path, and nothing is written when the data is invalid.
- Text output escapes control characters. JSON output goes through
  `json.stringify`.
- Ordering is deterministic. Groups are sorted by key with ties broken by first
  appearance, and `sortBy` is stable.

## Testing

- `projects/datakit/tests/*_test.siyo` hold unit tests per module and run under
  `siyoc test`.
- `DataKitRegressionTest.java` holds every compiler and stdlib fix. Each test
  runs on both backends, in-process, the way `TaskFlowRegressionTest` does.
- `DataKitEndToEndTest.java` runs the real CLI on both backends under `LC_ALL=C`:
  - goldens for fixed fixtures;
  - an independent oracle. Java code in the test parses the CSV with its own
    RFC 4180 reader and computes groups, sums, counts, min/max and validation
    verdicts for seeded random datasets, then compares them with DataKit's JSON
    output, which is read with `StrictJson`;
  - adversarial fixtures: quotes, embedded newlines, CRLF, BOM, ragged rows,
    huge numbers, NaN-like text, control characters, duplicate headers, an empty
    file, a header-only file;
  - CLI misuse: flag-as-value, existing `--out`, `--out` equal to the input,
    unknown flags, bad numbers;
  - every JSON output is strictly valid.
- The full `mvn clean verify` passes.

## Release

The version goes to 0.10.0 in pom, the CLI version, the installers and the docs.
`RELEASE_NOTES_0.10.0.md` gets written. FUTURE.md moves the unbuilt 0.10.0 items
on, and README and GRAMMAR document the new features. Commits stay scoped: one
for each compiler fix, then stdlib, app, tests and docs. Nothing is pushed,
merged, tagged or published.
