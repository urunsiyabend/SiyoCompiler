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
| G8 | type bug | A sum type's payload lost its written type. With `T(Array<S>)`, `T(xs) => xs[0].n` was rejected, and `Some(r)` on an `Option<Row>` bound an erased value. A number or bool payload stays erased (see review below). | `aPayloadKeepsItsWrittenGenericType`, `aGenericSumTypesPayloadIsReadAtItsTypeArgument` |
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

### Caught in review

Three rounds of independent review found that G1, G2 and G8 read values at
types nothing had checked. The result was compiled ClassCastExceptions and
NullPointerExceptions, and backends disagreeing (`1.0` against `1`), for
programs 0.9.0 ran consistently. The reproducers were:

- a missing map key through `get<K, V>(m: Map<K, V>, k: K) -> Array<V>`;
- an erased or null argument (`both(o, 2.5)` with `o: object`);
- an erased write into `wrap(1)`;
- `null` stored through an erased `Box`;
- an `Opt<Array<int>>` holding a string;
- reassigning a `Box<Array<float>>` field.

The third review also found that annotating an existing int array
`Array<float>` read `1.0` compiled and `1` interpreted.

A fourth review found that the erasing went too far for generic structs.
`mk(1.5).value * 2` raised compiled, a `Box<Map<string, int>>` field could not
be indexed, and a struct array from a generic call that an erased path had
written to failed compiled only. A type argument naming a struct only through
an annotation on an erased value (`imut o: Opt<R> = opt(5)`) failed where 0.9.0
printed `5`.

What shipped is a rule rather than patches:

- **A type argument supplies a name, not a class.** Results, payloads and
  elements keep 0.9.0's class. A struct name makes member reads possible, and
  the member read casts.
- **A generic struct keeps its type arguments.** Every read at a declared type
  (a field, an element, a map value, a payload) is checked identically on both
  backends: the interpreter applies the compiled read's widening, null check
  and kind check.
- **Uncertain arguments.** An erased, null or disagreeing argument erases the
  parameter, and a bare type-parameter result keeps 0.9.0's class.
- **Assignment and annotations.** Generic field assignment widens literals, and
  a payload literal is checked against an annotation.

The tests, which take the reviewers' cases, are
`anErasedOrNullArgumentErasesTheTypeParameter`,
`aGenericStructFieldOfTheWrongKindOrNullFailsAlikeOnBothBackends`,
`aGenericPayloadOfCollectionsReadsAsInZeroNineAndAStructOneIsChecked`,
`aGenericResultOverNumbersAndStringsIsErasedAsInZeroNine`,
`aNumberPayloadOfAGenericSumTypeReadsAsItDidInZeroNine`,
`whatZeroNineAcceptedWithoutDeclaredTypesIsUnchanged` and
`aMissingValueThroughAGenericCallOrPayloadReadsAsNullOnBothBackends` and
`aStructNameCarriesIdentityWhileTheValueStaysAsZeroNineHadIt`.

These reproducers sit outside the test suite: the review rounds' cases and the
ones found while fixing them were rerun on 0.9.0 and the branch, both backends.
Not every old probe is closed. Every one either:

- matches 0.9.0;
- is a compile-time error for a type-wrong program;
- is a generic struct case 0.9.0 could not parse, where both backends agree;
- or is a split between backends inherited from 0.9.0 and still unresolved.

The inherited splits are:

- `wrap_mutate`, which pushes a string into an int array through an erased
  alias;
- erased arithmetic (D10): `keep([1.5])[0] * 2`, a generic map's float value
  times an int, `o1`;
- a member write to a non-struct value: `array_generic_struct_badwrite`,
  `payload_bad_target_assignment`;
- a method called on an erased struct;
- the `r7` set reads.

The interpreter now follows the compiled backend at a checked read, which
changes some of these. `direct_map_null` (a missing key through a generic
`get`) now fails on both backends, where 0.9.0 failed compiled only.

### Re-review at 328a5c7

- **A generic struct's known type argument was lost.** This happened through a
  plain alias (`imut c = b`), through `filter`, and through an imported
  generic call with an alias, so `c.value * 2` raised compiled and gave `3.0`
  interpreted. The fix:
  - an alias now carries a name that mentions a generic struct, and nothing
    else 0.9.0 would not have known;
  - `filter` passes its argument's generic struct name through;
  - an element of a generic struct's field (`first(b.value)` on a
    `Box<Array<float>>`) feeds 0.9.0's bare-`T` inference.

  Tested by `aGenericStructsTypeArgumentSurvivesAnAliasFilterAndFieldElement` and
  `anImportedGenericStructKeepsItsArgumentThroughAnAlias`.

  The follow-up review found two more paths:
  - a bare `-> T` bound through a generic struct argument
    (`fn g<T>(b: Box<T>) -> T` given a `Box<float>`);
  - a local taken from a generic struct's field (`imut v = b.value`).

  A third path came up as well: a map literal as a struct's value
  (`Box { value: {"x": 1.5} }`), which inferred only `Box<map>`. It now gives
  `Box<Map<string,float>>`, with number kinds joined at the wider one.

  All three now carry the argument (`aMapLiteralGivesAGenericStructItsMapType`). A parameter written `Box<T>` also no longer
  renames an argument literal to `Box<T>`. Tested by
  `aGenericStructsArgumentReachesABareResultAndAFieldLocal`.
- **The interpreter skipped a value's declared return shape.** For
  `fn wrong<T>(x: T) -> Box<T>` returning an erased string, `len(wrong(1))`
  was `failed` compiled and `3` interpreted. The interpreter now applies the
  compiled return cast, under its condition (an erased value returned from a
  function declaring a type). It also checks a generic call's result read as
  the compiled unboxing reads it. Tested by
  `aValueOfTheWrongShapeFailsAtADeclaredReturnOnBothBackends`. This also turns
  0.9.0's split for `fn f() -> int` returning an erased string into a failure
  on both backends. A lambda's declared return is checked the same way
  (`aLambdasDeclaredReturnIsCheckedOnBothBackendsButNotAnActorsMethod`).

  An actor's method is left unchecked when interpreted, as in 0.9.0, where a
  failed check would end the method silently and leave the caller waiting.
  Compiled, a method declared to return one type that returns an erased value
  of another compiles; its cast fails in the actor's mailbox, and the compiled
  caller is left waiting. 0.9.0 and both intermediate snapshots behave exactly
  the same (final-gate `actor_bad_return`, `actor_nested_bad_return`). It is an
  inherited split, recorded here; actor runtime error reporting is out of this
  release's scope.

  The final gate found one more path to a generic struct's argument. A
  `Box<T>` getter returning an array or map, `g(b: Box<T>) -> T` or
  `h(b: Box<Array<T>>) -> Array<T>` given a `Box<Array<float>>`, knew the result
  type, but the element type was lost, so `v[0] * 2` raised compiled and gave
  `3.0` interpreted. A call bound through a generic struct argument now gives
  its elements that type. So does an alias of its result, and nothing else:
  plain generic functions over numbers (`keep([1.5])`) are erased as in 0.9.0.
  A `Box` made by a lambda declared to return one
  (`fn(x: float) -> Box<Array<float>>`) carries the type the same way: the
  lambda's signature keeps a generic struct's return name, and a call through
  it names its result. Tested by
  `aBoxGetterReturnsItsArgumentsElementsMapsAndScalars` and
  `aBoxMadeByALambdaKeepsItsArgumentForAGetter`.

  The gate at 4b358e3 found three more paths that split by backend: an alias
  of a getter's map result, an alias of a `filter` of it, and a nested array
  from it. Separate markers kept missing paths, so they are replaced by one
  provenance rule. An expression is *struct-derived* when its type came from a
  generic struct's type argument. That covers:

  - a generic struct's field;
  - a call bound through a generic struct argument;
  - a `filter` of a struct-derived array;
  - an index into a struct-derived container;
  - a variable (annotated or not) or `for` item that takes one.

  Only for such expressions, the whole known type name, at every depth,
  gives element and map value types. An array or map literal is named
  element by element for inference, so `[[1.5]]` is an `Array<Array<float>>`.
  Ordinary generic results stay erased as in 0.9.0
  (`anOrdinaryGenericOverNumbersStaysErasedAsInZeroNine`, identical on 0.9.0).

  `aGenericStructsArgumentReachesEveryReadOfEveryShape` checks the rule as a
  matrix rather than by example. It crosses 5 sources (a field, a getter, an
  annotated box, a lambda-made box, an imported getter) with 5 shapes (an array,
  a map, an array of arrays, a map of arrays, an array of maps). Each program
  reads the value directly, through two aliases, an annotated variable and its
  alias, `filter` and its alias, and `for` items, on both backends.

  Inherited from 0.9.0 and deferred: a lambda parameter whose type has a comma
  inside type arguments (`fn(x: Map<string, float>)`) is read as two
  parameters, a compile error on both backends.
- **Security: `io.writeNew` could delete someone else's file.** When a write
  failed, it deleted the path by name. If the file it created had been renamed
  aside, that deleted whatever another process had put at the path since. The
  reviewer's `siyo-failure-cleanup.py`, rerun, replaced the file during a write
  that exceeded RLIMIT_FSIZE; the replacement survived in all 4 attempts on
  both backends. Creation is exclusive, but publishing the whole content is not
  atomic: a failed write leaves the partial file it created.

The same review found `strings.isFloat`'s regular expression quadratic, taking
about 5 s for 32,000 digits followed by `x`. It is now a single linear pass,
tested by `isFloatIsLinearOnALongRunOfDigits`. It also found that
`csv.formatRow([""])` wrote a blank line that read back as nothing, which
`aLoneEmptyCellRoundTripsAndABlankLineIsMarked` now covers.

Two rounds of these fixes, and the checks above, were each overtaken by the
next review. Generic substitution in this compiler is name-based, with no
checked value types behind it, so the safe course was to trust less.

## DataKit's own bugs, found in testing

| Finding | Fix | Test |
|---------|-----|------|
| Problems came out in the order the checks found them, so a ragged line 5 was listed before line 3's cell problems. | Problems carry a row number and are sorted stably, using the app's own `pipeline.sortBy`. | `validate_test.siyo`, golden `validate_bad_cells.txt` |
| A required column missing from the CSV header was also reported once per row. | Reported once, against the header. | `missing_column` golden |
| In JSON, an unknown key rejected the first record that had it but not later ones. | Like an unknown CSV column, it is reported once and rejects no row. The data is still invalid. | `unknownColumnsAreTheFilesProblem` |
| A bound printed as `1.0` for an int column. | Bounds print in the column's type. | `boundsAndValuesAreChecked` |
| A schema column declared twice was missed when its first declaration was itself invalid. | Names are checked before each column is read. | golden `schema_bad.txt` |
| **Security.** `--out` checked that the path was free, then wrote it with a truncating, link-following write. A race that swapped in a symbolic link clobbered the link's target on both backends, on the first attempt. | `io.writeNew` creates the file atomically (`CREATE_NEW`) and never opens an existing file or link. The check before it only fails early. | `anOutputPathThatIsALinkIsNeverFollowed`, `writeNewNeverOpensWhatIsAlreadyThere`, the reviewer's `race.py` (24 attempts, 0 clobbered) |
| Floats were written to 17 decimal places, so `1e-18` and `2e-18` became `0.0`. `transform` wrote zeros, `group` merged them into one group, and `report` counted one distinct value. | `model.plain` moves the digits of the shortest representation that reads back as the float, with no rounding. Group keys use it, with `-0.0` equal to `0.0`. | `floatsSurviveTransformGroupAndReportExactly` (checked bit for bit against Java's `Double`), `plainNeverUsesAnExponent`, `tinyFloatsAreSeparateGroupsAndAnInfiniteSumIsAnError` |
| Int bounds were held as floats, so `min: 9007199254740993` accepted `9007199254740992`. | A bound keeps its written value, and an integer bound stays an exact int64 `IntV`. | `integerBoundsAreComparedExactly` (checked with `BigInteger`), `integerBoundsAreExact` |
| A quoted empty record (`""`) was skipped as a blank line, so a required column passed with 0 rows. | `std/csv` marks physically blank lines (`CsvRow.blank`), and only those are skipped. | `everyRejectedRecordIsCountedAndAQuotedEmptyRecordIsChecked`, `aQuotedEmptyRecordIsNotABlankLine` |
| A JSON record that was not an object was left out of `rows` and `invalidRows` when it came first: `[1]` gave 0 and 0. | Row problems are told apart from header and file problems by place, not by line number. | same, `aJsonRecordThatIsNotAnObjectIsCountedWhereverItIs` |
| A float sum past the largest float gave `Infinity`, or `null` in JSON, with exit 0. | It is an error, exit 3. | `aFloatSumPastTheLargestFloatIsAnErrorNeverInfinity` |
| A bound of the other number kind was compared as floats: an int column with `max 9007199254740992.0` accepted 9007199254740993. | `model.compareLongFloat` compares a long with a float exactly: it orders by the rounded long, and recovers an equal float's integer value from its 32- and 16-bit parts. | `aLongAndAFloatCompareExactly`, the fourth review's `imaxf` and `fminI` probes |
| The README promised every problem but output listed 100. | The README documents the cap, and that JSON's `problemCount` counts every problem. | `problemsBeyondAHundredAreCountedNotListed` |

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
| D8 | type rule | Reassigning a generic struct variable to a literal of other type arguments is not reported at compile time; the field read fails on both backends. A struct type parameter named like a struct hides it, as function and sum type parameters already did. A generic call over numbers gives erased values (`wrap(1)`), which a typed variable narrows. | Found in review. The rest of generic struct checking is in place, and trusting number type arguments is what review showed to be unsound here. | `pipeline_test` narrows the ints it sorts. |
| D9 | ergonomics | Unary minus is not defined for a `long`: `-x` is "Unary operator '-' is not defined for type Long". The same in 0.9.0. | Not needed by DataKit; it belongs with numeric `as`. | `0L - x` in tests. |
| D10 | parity gap | Arithmetic on an erased value is read at the other operand's type compiled: `o * 2` with `o` an erased `1.5` raises compiled and gives `3.0` interpreted. The same in 0.9.0. A generic struct whose type argument is never known (`Box` written bare, an erased value, or a list mixing `Box<int>` with `Box<string>`) has erased fields and reaches it. A list mixing number kinds joins at the wider kind and does not (`aListOfGenericStructsKeepsTheirTypeArgument`). | Typing erased arithmetic dynamically changes what 0.9.0 programs compute. A generic struct's type argument is carried where it is written, inferred from a literal, returned by a generic call, aliased, filtered or listed; a plain generic function over numbers (`keep([1.5])`) is erased as in 0.9.0 and reaches the rule as 0.9.0 did. | Fields are read through known type arguments. |
| D7 | output | Column padding counts UTF-16 units, so a non-ASCII group key misaligns its table row. This is TaskFlow D9 again. | Display width is its own problem. | Only text output is affected. |

A CSV file's bytes are decoded as UTF-8. Invalid bytes become U+FFFD, as in
TaskFlow D9.
