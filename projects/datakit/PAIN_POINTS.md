# DataKit pain points

Everything below was hit while writing DataKit against Siyo 0.9.0. Each entry
has a minimal reproducer, and each was checked against the 0.9.0 jar before
being called a limitation. Fixed entries name their regression test; deferred
ones give the reason and the workaround. "Compiled" is `siyoc run`,
"interpreted" is `siyoc interpret`.

The regression tests assert the same output on both backends. They live in
`src/test/java/codeanalysis/DataKitRegressionTest.java`, and the application as
a whole is covered by `DataKitEndToEndTest.java`.

## Fixed

| # | Kind | Finding | Test |
|---|------|---------|------|
| G1 | type bug | A generic function returning a type built on `T` gave an erased result. With `fn keep<T>(xs: Array<T>, p: fn(T) -> bool) -> Array<T>`, `keep(rows, p)[0].name` was "Type Object does not have members", and `Map<string, Array<T>>` behaved the same. Only a bare `-> T` kept its type. The call now unifies each parameter's written type with its argument's, and substitutes the result. | `aGenericCallReturningAnArrayOfTKeepsT`, `aGenericCallReturningAMapOfArraysOfTKeepsT`, `aGenericResultFlowsThroughAVariableALoopAndAnotherCall`, `aGenericCallOverPlainValuesStillWorks` |
| G2 | crash + missing | `struct Group<T> { key: string, items: Array<T> }` crashed the parser with a NullPointerException stack trace. Generic structs are now supported: erased at run time, with type arguments carried by the declared type name, inferred from a literal's fields or stated by an annotation. A type token the parser had to make up no longer ends the parse. | `aGenericStructKeepsItsFieldTypes`, `aGenericStructFlowsThroughParametersLoopsAndIndexing`, `aGenericStructFieldWidensAndAssigns`, `aPlainStructAndAnErasedGenericStructStillWork`, `aGenericStructsFieldMayBeTakenIntoAVariable`, `aGenericStructCrossesAModuleBoundary`, `aMalformedTypeNameIsADiagnosticNotACrash` |
| G3 | missing stdlib | There was no way to print a float to fixed places: an average printed as `0.6666666666666666`. Added `strings.formatFloat(x, digits)`, which rounds half up from the shortest decimal form. | `formatFloatRoundsHalfUpFromTheDecimalForm`, `formatFloatRejectsADigitCountOutOfRange` |
| G4 | missing stdlib | `parseFloat("abc")` is `0.0`, and `parseFloat` also accepts `NaN`, `Infinity`, `1f` and padding, so a bad cell could not be told from zero. Added `strings.isFloat`. | `isFloatAcceptsOnlyAFiniteDecimal` |
| G5 | missing stdlib | There was no CSV reader. Added `std/csv`, written in Siyo, which follows RFC 4180. The first version returned bare rows, which DataKit could not turn into line numbers once a quoted field spanned lines, so rows now carry the line they start on (`CsvRow`). | `csvReadsQuotedFieldsLineEndingsAndAByteOrderMark`, `csvReportsMalformedQuotingWithItsLine`, `csvFormatRowQuotesWhatNeedsItAndRoundTrips` |
| G6 | type bug | A function imported from another module lost its type parameters, so `lib.ident(r).n` was rejected. A module-qualified call, and a call written as a method, did not infer a generic result. | `anImportedGenericFunctionKeepsItsResultTypeAndWidensItsArguments` |
| G7 | type rule | A module-qualified call did not widen a narrower number at a parameter: `lib.half(5)` against `half(x: float)` was rejected, though `half(5)` in the same file is accepted. | same test |
| G8 | type bug | A sum type's payload lost its written type. With `T(Array<S>)`, `T(xs) => xs[0].n` was rejected, and `Some(r)` on an `Option<Row>` bound an erased value. | `aPayloadKeepsItsWrittenGenericType`, `aGenericSumTypesPayloadIsReadAtItsTypeArgument` |
| G9 | type bug | A signature naming a type declared further down the file got a null type: `fn s() -> Later` above `struct Later`, then `s().n`, was "Type <null> does not have members". | `aSignatureMayNameATypeDeclaredLaterInTheFile` |
| G10 | type rule + diagnostics | A function turning a sum type into JSON values, `fn conv(v: V) -> object { match v { I(n) => n, S(s) => s, M => null } }`, was rejected with "cannot mix void and value arms" although no arm is void. It is now accepted where the match ends a function returning `object`. Anywhere a type is declared the arms must still agree, since accepting them there turned 0.9.0's compile error into a silent wrong value interpreted (tried and caught in review: `imut x: int = match ... { _ => "no" }` printed `no1`). The message now names the two types. | `aMatchEndingAnObjectFunctionMayGiveValuesOfDifferentTypes`, `armsOfDifferentTypesAreStillRejectedWhereATypeIsDeclared` |
| G14 | stdlib gap | `io.readFile` reads lines and joins them with `"\n"`, so a CR or CRLF inside a quoted CSV field came back as LF, and the final newline was dropped. The independent oracle caught this; the app's own tests did not. `readFile` keeps its documented behaviour, which programs rely on, and `io.readText` returns a file as written, decoded as UTF-8. | `readTextKeepsLineEndingsAndTheFinalNewline`, `DataKitEndToEndTest.aLineBreakInsideAQuotedFieldIsKeptAsWritten` |
| G13 | type rule | A payload did not widen: `I(2)` where `I` carries a `long` was "Cannot convert Integer to Long". | `aPayloadWidensToItsDeclaredNumberType` |

### Reproducers

```siyo
// G1: "Type Object does not have members"
fn keep<T>(xs: Array<T>, p: fn(T) -> bool) -> Array<T> { ... }
println(keep(rows, fn(r: Row) -> bool { true })[0].name)

// G2: NullPointerException in Parser.parseOptionalTypeArguments
struct Box<T> { value: T }

// G6, G7: lib.siyo:  pub fn ident<T>(x: T) -> T { x }   pub fn half(x: float) -> float { x / 2 }
println(lib.ident(R { n: "a" }).n)      // Type Object does not have members
println(toString(lib.half(5)))          // expects Double but was given Integer

// G8: "Type Object does not have members"
type R = T(Array<S>) | One(S)
match r { T(xs) => println(xs[0].n), One(s) => println(s.n) }

// G9: "Type <null> does not have members"
fn s() -> Later { Later { n: 1 } }
struct Later { n: int }
fn main() { println(toString(s().n)) }

// G10: "All match arms must return the same type; cannot mix void and value arms"
fn conv(v: V) -> object { match v { I(n) => n, S(s) => s, M => null } }

// G13: "Cannot convert type Integer to Long"
type V = I(long) | F(float)
println(show(I(2)))
```

## DataKit's own bugs, found in testing

| Finding | Fix | Test |
|---------|-----|------|
| Problems came out in the order the checks found them, so a ragged line 5 was listed before line 3's cell problems. | Problems carry a row number and are sorted stably, using the app's own `pipeline.sortBy`. | `validate_test.siyo`, golden `validate_bad_cells.txt` |
| A required column missing from the CSV header was also reported once per row. | Reported once, against the header. | `missing_column` golden |
| In JSON, an unknown key rejected the first record that had it but not later ones. | Like an unknown CSV column, it is reported once and rejects no row. The data is still invalid. | `unknownColumnsAreTheFilesProblem` |
| A bound printed as `1.0` for an int column. | Bounds print in the column's type. | `boundsAndValuesAreChecked` |
| A schema column declared twice was missed when its first declaration was itself invalid. | Names are checked before each column is read. | golden `schema_bad.txt` |

## Deferred

| # | Kind | Finding | Why deferred | Workaround in DataKit |
|---|------|---------|--------------|-----------------------|
| G11 | type gap | A struct field whose type is a struct from a module the reader did not import cannot be walked. With `b.P { top: Array<a.G> }`, a file importing only `b` gets "Type Object does not have members" from `for g in p.top { g.key }`, though `p.one.key` works. Also in 0.9.0. | Types are resolved by name in the importer's table. A fix needs struct identity by symbol, or a visibility rule for types reached through another module, both decisions beyond this release. It is a compile error, not a wrong result. | `render.siyo` and `stats_test.siyo` import `pipeline` for `Group`. |
| G12 | type gap | Two imported modules exporting a type of one name collide silently: the later import's type replaces the earlier one in the importer. For sum types this gives "Name 'Good' does not exist"; for structs, "Struct 'S' has no field 'a'". Also in 0.9.0. | It needs module-qualified type names (LogScope D1). It is a compile error, if a confusing one. | `query`'s result type is `Outcome<T>`, not a second `Parsed`. |
| D1 | ergonomics | `toFloat` takes only an `int`, so a `long` needs `n * 1.0`. | Numeric `as` conversion is the planned answer (FUTURE.md). | `n * 1.0` in `model.number`. |
| D2 | ergonomics | `=> return x` is not an arm; it is a parse cascade of "Unexpected token ReturnKeyword". | Write `=> { return x }`. Accepting it is a parser change with no other need. | Blocks. |
| D3 | lexer | `1e20` is not a float literal: it reads as `1` followed by the name `e20`. | Rare in programs. `parseFloat` and `strings.isFloat` read exponents. | `100000000000000000000.0` |
| D4 | type rule | `os.exit` is not known to diverge, so a function that calls `fail(...)` on one path still needs a value on that path. This is the same as LogScope D4 for `error`. | Needs a never type. | Unreachable placeholder values after `fail`. |
| D5 | type rule | `[1, 1.5]` is rejected as a mixed literal, and `Empty { }` is unsupported. Both are known (TaskFlow D7, FUTURE.md). | Not needed. | Not applicable. |
| D6 | naming | A local variant named like one an import exports, such as `Invalid` beside `std/json`'s `Invalid`, must be written qualified. The diagnostic says so. | A language rule, documented since 0.6.0. | Named `Rejected`. |
| D7 | output | Column padding counts UTF-16 units, so a non-ASCII group key misaligns its table row. This is TaskFlow D9 again. | Display width is its own problem. | Only text output is affected. |

A CSV file's bytes are decoded as UTF-8. Invalid bytes become U+FFFD, as in
TaskFlow D9.
