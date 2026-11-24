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

  The gate at 897ea1c found four transports that dropped provenance:

  - a map key walked by `for`;
  - an exported module variable, which `import` recreated without its type;
  - a member read through a plain struct inside a generic one (`Row` holding
    `Array<Array<float>>` inside a `Box`);
  - `pop`.

  Provenance is now carried by every operation that moves a value of a known
  type. It is a property of the variable symbol, so it crosses a module
  boundary with the symbol. A member read of a struct-derived value, `filter`,
  `pop`, an index, and a loop item that is an element, a map key or a set
  element all carry it. An exported variable keeps its type only when it is
  struct-derived. Other exported variables read as in 0.9.0.

  **Origin identity.** The final gate at 56a4a07 found two ways an imported
  value was read as the importer's struct of the same name:

  - A public `Row` held the exporter's private `P` in a field.
    `lib.values[0].item.who()` silently ran the importer's own `P.who` on both
    backends. The check at the time looked one level deep only.
  - A later `import "other"` replaced the public `P` the value was made of, after
    the import-time check had passed, and the read failed with a cast error.

  An imported struct-derived variable now carries its exporter's struct, sum
  type and interface tables. So does every alias, loop item or other variable
  that takes a value moved from it. Wherever such a value is used, the compiler
  checks every type its name mentions, and every type their fields and variant
  payloads mention in turn, against the names as they stand at that point. The
  walk is cycle-safe, so recursive types terminate. A type may not be a
  *different* declaration of that name in the importer. Two things don't count
  as a clash:

  - a name the importer cannot write at all, such as a struct reached only
    through a third module;
  - a struct's own type parameter.

  When the check fails, the value reads erased. Member access is rejected at
  compile time ("does not have members"), and a method or interface call on it
  fails on both backends. Another module's type is never read in its place.
  When the check holds, the genuine exporter types are used.

  **The check is not stale.** The gate at 6864526 found the first version cached
  the check per variable. An `import "other"` inside a function changed what `P`
  meant after a use elsewhere had already passed the check, and `a[0].who()`
  then silently printed `other` on both backends. Review of the first fix found
  the same through a struct literal (`W { v: lib.values }`), a generic wrapper, a
  lambda's declared return type, and a loop with a field local, none of which
  carried the origin. The fix is general rather than per transport:

  - **One generation per file.** The file's struct, sum type and interface
    tables share a generation that moves on every change to any of them. A
    table changes only through `put`, `putIfAbsent` and `remove`, each
    recorded. Every other way to change it, including through its key, value
    and entry views, is refused, so no change goes unseen.
  - **Locals are stamped, before their statement.** Every local variable that
    takes a type name records the generation from just before the statement
    that gives it its type. This covers a declaration, a loop item and a payload
    binding. A second review found that stamping after the initializer let a
    lambda inside it import `other` first, and `first(wrap(lib.values), call0(fn()
    { import "other" ... }))` then printed `other`.
  - **No copies.** Each table keeps every change, so a stamp reads the table as
    it stood at its generation. A copy of the tables per generation ran out of
    memory with 3,000 nested imports. A `put` that changes nothing starts no
    generation.
  - **The fast path.** A variable stamped in the current generation is trusted
    at once, which is the case for all ordinary code.
  - **The slow path.** Otherwise its name is checked against its stamp as an
    imported value is checked against its exporter, transitively and
    cycle-safe, and it reads erased if any name now means something else.
  - **Shared results.** Results are kept per (origin, type name), so aliases
    share one walk. A local's result holds only in the generation it was made
    in. An imported value's result also keeps the names its walk consulted. It
    is reused in a later generation when the file's change log since then
    touches none of them, so an unrelated nested import costs no new walk.
  - **Closure signatures.** A call through a closure reads the closure's
    signature through the same check.
  - **Exported values.** An exported value carries its exporter's tables as
    they stood where it was declared there. A function of the exporter that
    imports `other` therefore cannot make the value read as `other`'s `P`.

  `aLaterImportNeverLetsAnImportedValueReadAnotherModulesType` covers fifteen
  cases on both backends. None may print `other`: each either reads lib's own
  type or is refused, and a control reads `lib`.
  - a use primed by an unused function, and the unprimed case;
  - an alias taken before the nested import;
  - a read before and after it;
  - a function-level import declared before and after `main`;
  - a struct literal, a generic wrapper, a lambda's declared return, and a loop
    with a field local, each captured before the import;
  - an import inside a lambda bound within a declaration's initializer, a loop's
    collection and a match's target;
  - the same three statements without the imports, which read `lib`;
  - a struct local's method before (`lib`) and after (refused) the import.

  `aStructLocalsFieldStillReadsAsTrackedAfterANestedImport` keeps that local's
  field reads.

  `anImportInsideTheExportersOwnFunctionDoesNotChangeWhatItsValueMeans` covers
  the exported value.

  **Not covered: an expression spanning a nested import.** A type name an
  expression computed before an import inside its own lambda is read after it,
  with no variable to stamp. An example is `first(wrap(lib.values), call0(fn() {
  import "other" ... })).v[0].x` with other's `P { x: string }`. It fails loudly
  on both backends with a `ClassCastException`. Method dispatch inside the
  expression stays with lib's `P`, so it was not made to print `other`.

  **Inherited, unchanged.** These are the same-name families below; 0.9.0's
  analog of each prints `other` too.
  - **Methods follow the first import.** After `import "other"` and then
    `import "lib"`, the type table holds lib's `P`, but `P.who` was registered
    by other first. In 0.9.0, `lib.mkp().who()` runs other's method, and it
    still does. A name 0.10.0 infers, whether an imported value's or a call's
    result, is not trusted through such a contested `P`: it fails as 0.9.0
    did.
  - **Directly tracked struct locals, when annotated.** A struct-typed local
    whose struct symbol was tracked directly calls a method by the plain name
    `P.who` after the import, as 0.9.0 does for a value returned by a function.
    When the local's type was inferred (`imut p = a[0]` with `a = lib.values`),
    the method call is refused once its names no longer hold. That printed
    `other` until this round. Its fields still read as tracked, so `p.x + 1`
    gives `2.5` on both backends, not 0.9.0's erased arithmetic.
  - **Declared slots.** A value passed to a parameter or lambda parameter typed
    `W<Array<P>>`, or returned from a named function declared `-> W<Array<P>>`,
    takes the slot's type, read where it is used. So does `fn f(a: Array<P>)`
    in 0.9.0.
  - **An exporter's own nested import.** A module whose own function imports
    another module declaring a type of the same name has that name replaced
    throughout the module. In 0.9.0, `lib.mkp().who()` (a struct return) and
    `imut a: Array<P> = lib.mk()` (an annotation) then print `other`, and still
    do.

  **Fixed: an inferred return after an exporter's own nested import.** 0.10.0
  names an imported call's result from the function's declared return type,
  where 0.9.0 left `lib.mk()`'s `Array<P>` erased. The gate's review of this
  round found that, after lib's `side()` replaced lib's `P`, `imut a =
  lib.mk(); a[0].who()` printed `other` on both backends. In 0.9.0 it failed
  with "No matching method".

  - **The fix.** An imported function carries its exporter's tables as they
    stood before any of its bodies, and so any import inside one, was bound.
    These are views at a generation, not copies.
  - **The check.** Where the result is named (a call's type, a loop over it, a
    generic struct result), the declared return type is checked as an imported
    value is, the function's own type parameters excepted.
  - **The outcome.** When a name now means another declaration, the result is
    erased as in 0.9.0.

  Review of that fix found three more ways the new inference reached another
  declaration's method where 0.9.0 failed:
  - **A generic call over a struct return.** `lib.id(lib.mkp()).who()`, or a
    local `id`, bound `T` to the `P` that 0.9.0's struct-return route names.
  - **Collisions in the importer.** After `import "other"` and `import "lib"`
    in either order, or beside the importer's own `struct P` with methods, the
    struct table keeps one `P` while its methods, or the interpreter's lookup by
    name, belong to another.
  - **Collisions in lib.** Lib does `import "other" as o` before its own `P`.

  Two rules close them without changing 0.9.0's tables:
  - **Contested names.** Each file records, for each struct name and method,
    which files declared it, whether or not the declaration won. A name is
    contested when either of these holds:
    - two files declared the same method of it;
    - two files declared a struct of the name, and one of its methods here
      comes from another file than the current struct's.

    The backends may then run different bodies. A name that an imported value
    or an imported call's result carries is not trusted when it is contested in
    the importer, or for a call, was contested in the exporter as its
    declarations stood.

    These clash with nothing:
    - a method another file adds to the one struct of a name (`impl P { fn mine
      }` on lib's `P`);
    - a second struct of the name with no methods.

    A struct an imported module found contested is contested in the importer
    too, when it is the same struct. Its methods arrive by inheritance, and the
    interpreter finds a refused method by name. Two cases show this:
    - `mid` imports `xx` (a `P` with `zed`) and then lib, so xx's `zed` reaches
      lib's `P` in main;
    - `dupmod` declares lib's `who` again.

    A local whose type was inferred, rather than written in an annotation, is
    held to all of this when it is re-checked after an import. So is a struct
    local taken from it (`imut p = a[0]`). An annotated local is 0.9.0's route,
    is held only to what changed since, and reads as 0.9.0 read it.

    Review found that a first version, contesting any method from another file,
    wrongly rejected an extension method on lib's `P` and impls split across
    files, which 0.9.0 runs.
  - **Calls that fail the check.** Inference no longer names a call whose
    return check fails, even by 0.9.0's routes.

  `anInferredReturnTypeNeverReadsATypeTheExportersOwnImportReplaced` covers
  twenty-three cases on both backends:
  - **Erased again, failing as in 0.9.0:**
    - `imut a = lib.mk()`, `lib.mk()[0].who()`, and `lib.boxed()` returning
      `Box<Array<P>>`, after lib's own import;
    - `lib.id(lib.mkp())` and a local `id` over it;
    - the importer importing other before or after lib;
    - the importer's own `struct P`;
    - lib importing other before its own `P`;
    - the importer implementing `Named` for lib's `P` again, which lib already
      does. Compiled code ran lib's `name` and the interpreter main's.
    - a middle module through which xx's `zed` reached lib's `P` (`xx` was
      printed);
    - `dupmod` imported after an inferred local, and a struct local taken from
      it. Compiled code printed `lib` and the interpreter `dup`.

    At the previous commit each printed `other`, `main` or (run) `lib`. 0.9.0
    failed with "No matching method" on all of them.
  - **Still named, printing `lib`:** the calls with no import in lib, with an
    import in lib of a module declaring no `P`, annotated, a generic call over
    `lib.mkp()`, a methodless `P` imported before lib (`1.5 lib`), a method
    main adds to lib's `P` (`lib main-ext`), and an annotated local beside a
    duplicate impl across a nested import.
  - **Unchanged from 0.9.0, printing `other`:** `lib.mkp().who()` and the
    annotated `Array<P>` after lib's own import of `other`.

  The previous commit printed `lib` for `import "lib"` then `import "other"`,
  and beside the importer's own `struct P`. Both now fail as 0.9.0 did, since
  which `P.who` runs there depends on the order and the backend.

  **Provenance, not call shapes.** The gate at 40f6f7f found two more ways back
  to a refused identity. Both ran lib's `who` compiled and dup's interpreted,
  where 0.9.0 failed:
  - **Rebuilding the value.** `b = [p]` then `b[0].who()`, and the same through
    `pop(b)`. The array's struct was tracked by symbol and never checked.
  - **Generic inference.** `lib.id(p).who()`, or a local `id`. Inference fell
    back to p's tracked struct after its name had been refused.

  The fix is one rule. Each value carries the set of roots its static identity
  was inferred from by 0.10.0:
  - an imported value;
  - an imported call named from its declared return type (not a struct or sum
    type result, which 0.9.0 typed already);
  - a generic call's inferred result.

  The set is the union over what the value is made of. That covers an alias,
  an index, a field, a literal holding it, a builtin over it (`pop`,
  `filter`), an `if` or `match`, a loop item and a payload. A variable keeps
  its set and only grows it: assigning, storing an element or field, or
  `push`ing adds what was stored. An annotation starts afresh, as 0.9.0's route.

  Each root is asked two things:
  - **Identity:** whether its names still mean the declarations they meant. This
    governs naming and field reads, as before.
  - **Contest:** whether a struct its type reaches has methods from more than
    one declaration. A contest decides only which method body runs.

  The two places that use roots:
  - **Method calls.** A method call dispatched by struct is refused when that
    struct is contested or now means another struct, and the receiver's roots
    fail either question. `W { n: len(a) }.hello()` beside a contested `P`
    keeps its dispatch.
  - **Generic calls.** A generic call's type argument is not named from a value
    whose roots fail identity, nor by a contested struct its roots meet.

  So an identity refused once is never regained by repackaging. A number,
  string or bool carries no roots. A struct annotation (`imut q: P = ...`) keeps
  them, since 0.9.0 never typed an erased value by one; only a collection
  annotation such as `Array<P>`, 0.9.0's route, starts afresh.

  **Bind order is not run order.** Review found that a call bound before
  `import "dup"` still split. Three shapes did it:
  - a closure made earlier;
  - a function declared above `main`;
  - an import inside a function declared after `main`.

  The interpreter finds a method by name once the import has run, whatever
  order the code was bound in. Every import nested in a function, method or
  lambda of a file is therefore compiled while the file's declarations are read,
  before any body is bound. Its methods and structs are recorded then, so the
  contests it will cause hold from the start, and pass to importers through the
  file's declared-contested set. The walk follows each syntax node's fields,
  since some nodes do not list their body as a child. Nothing is declared into
  scope early, and a module that fails is reported once.

  Anticipation is transitive. A module carries what its own nested imports
  bring in: methods, structs, and structs found contested. So `import "dup2"`
  in `main`, where `dup2`'s function imports `dup`, is refused as `dup` itself
  would be. A file that is a single declaration is walked too.

  An import that is only ever written inside a function that never runs still
  counts. A same-name struct it would bring makes the name at risk for the
  whole file, so `id(r).who()` beside such a module fails as in 0.9.0, where
  40f6f7f printed the local struct's method.

  The walk caches each syntax class's fields. On a 39,000-line file,
  interpreting takes about 0.76 s against 0.57 s at 40f6f7f.

  **Compiling ahead does not reorder initialisation.** The gate at 79348c0
  found that interpreted programs initialised nested-imported modules in the
  order anticipation compiled them. For `fn side() { if true { import "a" }
  import "b" }`, a module printing as it starts gave `B A MAIN` interpreted and
  `A B MAIN` compiled. 0.9.0 and 40f6f7f give `A B MAIN` on both.

  The interpreter initialises modules in registry order, which was completion
  order, and anticipation completed some modules early. Now a module records
  the modules its import statements bound, in the order they were bound. Only
  the file being run places modules in the initialisation order, each after
  its own imports. That is the completion order the binder alone would give,
  whichever was compiled first.

  `modulesInitialiseInTheOrderTheirImportsAreBound` checks nine shapes on both
  backends against 0.9.0's exact output: flat (unused and called), nested
  first, nested second, a lambda first, `if`/`else`, deep, a diamond whose
  shared module starts once, and a nested module whose own function nests
  imports.

  **Stores are seen through aliases.** The gate at 79348c0 also found
  `imut alias = w`, `import "dup"`, `w.value = p`, `alias.value.who()`
  printing `lib` compiled and `dup` interpreted. A store added what it stored
  only to the variable written through, not to aliases taken before it.

  Provenance now lives on storage, in three relations:
  - **The same object.** `c = w`, or an `if` or `match` of it, shares one cell.
  - **Taken out of.** An element, a field, a loop item, a payload, or the
    result of a builtin, a generic or ordinary function, or a closure over `w`.
    It sees what `w` sees, and a store into it is a store into what `w` holds.
  - **Held inside.** A literal holding `w`, or `w` stored or pushed into
    another. Only the container sees what reaches `w`.

  Every relation only grows, and a root reaches a cell at most once.

  A function or closure call may store one of its values into an argument
  whose slots can hold it, so `put(w, p)` counts as `w.value = p`. This holds
  in a declaration's initializer too. The slots are fields, elements and
  payloads, through nested structs, and a slot of unknown or parameter type
  may hold anything. A closure counts as one of its values, since it may store
  what it captured. An argument whose type holds only numbers and strings, such
  as `P { x: float }`, receives nothing, so `show(fresh, xs)` leaves `fresh` as
  it was. Review found that a first version, storing every argument into every
  other, refused that.

  A first version joined every relation into one cell. Review found that
  pushing a fresh `P { x: 2.0 }` into an inferred list then refused
  `fresh.who()`, which 0.9.0 runs, and that the shared sets grew quadratically.
  Containment is now one-way. A local is no longer a root of its own, since
  what it was inferred from is. A list of 16,000 pushed elements that ran out
  of memory at 79348c0 takes 0.6 s.

  Two tests cover this on both backends:
  - `aStoreIsSeenThroughEveryAliasTakenBeforeIt`: field, index, map and `push`
    stores, a rebuilt `pop([c.value])` and an alias of an alias, with and
    without dup.
  - `aStoreReachesAliasesThroughCallsClosuresLoopsAndHolders`: a store through
    a parameter, a loop item or a holder, and an alias handed back by a
    function or closure, `imut n = put(w, p)`, and a one-argument closure that
    captured `p`. A fresh value pushed beside inferred ones, and one passed to
    `show(fresh, xs)`, still print `lib`.

  **Inherited, identical to 0.9.0: a field typed `P` read after dup's import.**
  Three routes still run lib's `who` compiled and dup's interpreted:
  - **`put1`.** `fn put1(t: W<P>) { t.value = lib.mk()[0] }`, then `put1(w)`
    and `w.value.who()`.
  - **A closure.** Its body calls `w.value.who()`, bound before
    `w.value = p`.
  - **A loop.** It stores `p` on one pass and calls `w.value.who()` on the
    next.

  They were listed here as gaps in the inference tracking. Checked against the
  published 0.9.0 (02c2262), they are not:
  - **0.9.0's own forms split identically.** With a plain `struct W { value:
    P }` (0.9.0 cannot parse `W<T>`), 0.9.0 gives each route `lib` compiled and
    `dup` interpreted. The exact `W<T>` programs fail to compile in 0.9.0 only
    for the generic struct.
  - **Inference plays no part.** With a fresh `P { x: 3.0 }` stored instead of
    an inferred value, every route splits the same way. The field's type `P`
    comes from the declaration or the literal, not from what 0.10.0 inferred.

  It is the same-name dispatch of a contested `P` that 0.9.0 has wherever a
  value is typed `P` by a declaration, as for a plain local `P { .. }`. Where
  inference does decide the outcome, the value is refused: `w.value = p` then
  `w.value.who()` fails on both backends, while the same program storing a
  fresh `P` splits as 0.9.0 does.
  `aFieldTypedPReadsTheSameWhetherItsValueWasInferredOrNot` pins the three
  routes: on each backend, the inferred value gives exactly what the fresh one
  gives.

  Outcome of each formerly deferred route, compared with 0.9.0 (02c2262).
  Outputs are compiled / interpreted. For 0.9.0, the plain-struct form is
  shown, since 0.9.0 cannot parse `W<T>`:

  | Route | 0.9.0 | Final |
  |---|---|---|
  | `put1` fetches `lib.mk()[0]` itself, nested `dup` | lib / dup | lib / dup (fresh `P`: same) |
  | Closure reads `w.value`, store after | lib / dup | lib / dup (fresh `P`: same) |
  | Loop stores, then reads on the next pass | lib / dup | lib / dup (fresh `P`: same) |
  | A plain local `P { .. }` | lib / dup | lib / dup |
  | Closure reads through `alias = w` | No matching method | No matching method |
  | Direct `alias.value.who()` after the store | No matching method | No matching method |

  **An alias of a generic struct.** 0.9.0 drops a plain struct's type when it
  is aliased, so `imut alias = w` then `alias.value.who()` fails there. 0.10.0
  keeps a generic struct's type through an alias. Through a contested `P`, that
  split even for a fresh value until this round. A local whose generic struct
  type 0.10.0 alone gave it is now a root. That covers an alias, an element or
  field, a loop item, a payload, and a generic or imported call's inferred
  result. Through a contested `P` it is refused, as 0.9.0 refuses the plain
  alias. A struct literal, a call typed by its declared return, and a closure's
  signature are typed as 0.9.0 typed them, and are unchanged.

  The same holds for a plain struct local 0.9.0 did not type at all, such as
  one taken from a field (`imut a = h.w`) or from a generic or imported call
  (`id(W { .. })`, `lib.id(W { .. })`). Review found each of these splitting
  where 0.9.0 failed. Each is now refused, as in 0.9.0. A plain struct element
  of an annotated array, which 0.9.0 typed, is unchanged.

  **A builtin over a literal of a generic holder.** The gate at 648348d found
  `imut a = pop([w])`, with `w` a `W<P>`, then `a.value.who()` after dup's
  nested import, printing `lib` compiled and `dup` interpreted. So did
  `pop(filter([w], ..))`. The plain `struct W { value: P }` twin fails in
  0.9.0, which typed an element of a literal of struct literals but not of a
  literal holding a variable. Now three kinds of typing count as only
  0.10.0's, and the local they give is a root, refused through a contested
  `P`:
  - **A builtin over a literal.** One of its arguments is a literal with an
    element that is not a struct literal: `pop([w])`, `pop([mkw()])`,
    `filter([w], ..)`.
  - **A literal of non-literals.** An array or set literal of elements that
    are not all struct literals carries its inferred element type as a root of
    its own, so `[b][0].value.who()` is refused too.
  - **A collection annotation over what 0.9.0 left untyped.** That is a
    variable (`b: Array<W> = a`), an element, an `if` or `match`, or a generic
    or closure call. 0.9.0 does not compile these. Such an annotation keeps the
    value's provenance. Over an ordinary call or a literal it starts afresh, as
    0.9.0 typed it, so `a: Array<P> = lib.mk()` keeps 0.9.0's route.
  - **A collection annotation over a builtin 0.9.0 did not type as an array.**
    0.9.0 typed a builtin's result as an array only in two cases: `filter` of
    an array, and `pop` from a variable written as an array of arrays. It
    failed with "does not have members" on these:
    - `pop` over a literal, `a: Array<W> = pop([[w]])`, which the gate at
      b270b85 found splitting;
    - `pop` over an unannotated variable;
    - `map`;
    - a builtin over one of those.

    The array these start from must be one 0.9.0 typed where it was declared:
    from an array literal, or from an ordinary call returning an array. An
    array annotated over another variable is not, and neither is `pop(..)`
    seen through `filter`. Review found all four routes splitting before that
    was tightened.

    `aCollectionAnnotationOverABuiltinFollowsZeroNinesTyping` checks sixteen
    compositions, plain and generic, against 0.9.0's own output. Where 0.9.0
    fails, each is refused on both backends. Where it runs, each gives exactly
    0.9.0's output per backend.
  - **A closure call's result.** Named from the closure's signature
    (`f = fn() -> W<P> { .. }; f().value.who()`), it carries that name as a
    root.

  `pop([W { .. }])` and `pop([W { value: p }])` behave as their 0.9.0 plain
  twins do.

  **A union of storage keeps each root once.** The same gate found 4,000
  chained aliases of a `W<P>` running out of a 256 MB heap, where 79348c0 runs
  them in 0.15 s. Every union left the cell it retired holding all the roots
  so far: 499,500 entries for 1,000 aliases, 1,999,000 for 2,000. A retired
  cell is now only a link to its new root, without roots or edges of its own.

  Each alias, and each link of a `pop([prev])` or `id(prev)` chain, had also
  become a root of its own. Such a local's root is now its type and the tables
  it was stamped against, so locals of one type stamped in one generation
  share one root: each passes exactly the check the first does. Review found
  that a first attempt, skipping aliases of checked locals, missed an alias
  taken through a collection annotation. Measured interpreted:

  | Aliases | 648348d | Final |
  |---|---|---|
  | 1,000 | 0.19 s | 0.13 s, 68 MB |
  | 2,000 | 0.40 s | 0.15 s, 85 MB |
  | 4,000 | out of memory at 256 MB | 0.18 s, 97 MB; runs with `-Xmx64m` |
  | 4,000 links of `pop([prev])` | — | 0.28 s; runs with `-Xmx64m` |

  Compiled, the 4,000 locals of one method need 128 MB, as at 79348c0.
  `aLongChainOfGenericHolderAliasesStaysWithinABoundedHeap` runs the chain with
  `-Xmx64m` interpreted and `-Xmx128m` compiled, and
  `aLongChainOfPoppedGenericHoldersStaysWithinABoundedHeap` the `pop` chain with
  `-Xmx64m`. `aUnionOfStorageKeepsEachRootOnce`
  checks directly that a chain of 2,000 unions keeps 1,999 root entries in
  total. `storageKeepsFourThousandDistinctRootsOnceAcrossAliasesAndContainers`
  checks a harder shape: 4,000 variables, each with its own root, aliased late,
  every tenth held in a container, and a fresh one held by none. It keeps 4,000
  entries, every alias and the container see all of them, a later root reaches
  all of them, and the fresh one stays empty. A retired cell now drops its
  collections rather than emptying them, so it keeps no capacity either.

  **A duplicate brought in at the top level contests nothing alone.** Review of
  this round found a false rejection. With `import "dup"` at the top level, a
  store `arr[0] = lib.mk()[0]` into an annotated `Array<P>` and then
  `arr[0].who()` was refused, though 0.9.0 prints `lib` on both backends. The
  same happened for a `Map<string, P>` and a generic field.

  A duplicate a top-level import brings in is registered before anything runs,
  the same for both backends: even 6864526, before any of these checks, prints
  `lib` for both. A method now contests a struct only in three cases:
  - **Another struct's method.** It comes from a file whose own struct of the
    name is another struct.
  - **The wrong body registered first.** It is declared twice, the struct's own
    file being one declarer, and another file's was registered first, as when
    lib does `import "other" as o` before its own `P`.
  - **A later winner.** It is declared twice, and a body other than the first
    registered can still win by name later: the file's own `impl`, or an
    import nested in a function, whose module's impl methods are carried on the
    module record.

  A nested module that only re-exports lib's `P` re-registers lib's own `who`,
  the body already winning. So `import "dup"` at the top level, beside a nested
  import of a module that adds another method, still prints `lib`, as in 0.9.0.

  Those programs print `lib` again on both backends.

  The cost is that a call bound before such an import in the same file is
  refused too, as 0.9.0 refused it. Which runs first is not known statically.
  `readBeforeAndAfterNestedImport` and `structLocalMethodBeforeAndAfterNestedImport`
  now expect that.

  `anInferredIdentityRejectedOnceIsNeverRegainedByRepackaging` covers 72 cases
  on both backends. The dimensions are two sources (`lib.mk()` and
  `lib.values`), twelve rebuildings, and dup imported before, after or not at
  all. The rebuildings are:
  - an alias, an array variable and an inline array;
  - `pop`, `filter` and `push`;
  - a generic struct field, an `if` value and a loop item;
  - imported and local `id`, and `id` of an array.

  With dup, every case fails as 0.9.0 did. Without it, none prints `dup` and
  the backends agree.

  `aContestIsHeldWhereverTheCallRunsAndOnlyAgainstItsStruct` covers thirteen
  cases on both backends:
  - `dup2` imported in a function, where `dup2`'s own function imports `dup`.
    It is refused; imported at top level it prints `lib`.
  - a closure, a lambda, and a function bound before the import, an import in
    a function declared after `main`, and a struct annotation over an inferred
    value. Each is refused.
  - a closure without the import, which prints `lib`;
  - `W` and `S` beside a contested `P`, which keep their methods, while
    `id(s).q.who()` is refused.

  **Stricter than 0.9.0.** Under such a collision, a value that 0.10.0 inferred
  and then stored in a local struct's `P` field, a local `[P { .. }]` array, or
  a local union's payload is refused. 0.9.0 split there, compiled `lib` and
  interpreted `dup`. A `P`-typed parameter and an annotated `Array<P>` keep
  0.9.0's split, as documented.

  `anAnnotatedValueKeepsZeroNinesRouteButNotGenericInference` covers the
  annotated side. An annotated `Array<P>`'s `b[0].who()` runs as in 0.9.0,
  while `lib.id(p)` and `id(p)` over it fail as 0.9.0 did. An unrecorded
  generation was also possible through a table's entries. `VersionedMapTest`
  shows that `entrySet()` entries are now immutable copies, and that every
  other way to change a table is refused or recorded.

  **Inherited: an actor in an array.** `lib.mka()[0].hi()`, with `mka`
  returning `Array<A>` for an actor `A`, throws a ClassCastException compiled
  and runs interpreted. A same-file `fn mka() -> Array<A>` does exactly that in
  0.9.0. 0.10.0 reaches it through an imported call too, which 0.9.0 left
  erased.

  Three tests bound the cost:
  - `anOriginCheckStaysFastOnALargeCyclicGraphWithManyAliases`: 500 structs
    with cross edges and 200 chained aliases, on both backends, about 0.25 s
    a run.
  - Not a test: 3,000 functions that each import a module and then read
    `lib.values` of a 2,000-struct chain take 0.34 s interpreted. Re-walking
    per generation took 3.3 s.
  - `anOriginCheckIsSharedAcrossAliasesOfALongChain`: 3,000 chained structs and
    3,000 aliases, each read. Before the walk was shared, this ran out of
    memory. It now takes about 0.25 s interpreted.
  - `manyNestedImportsKeepTheOriginCheckBounded`: 1,500 functions, each taking
    a local of a 2,000-struct chain and then importing a module of its own,
    which makes 1,500 generations. It takes about 3 s interpreted. With 3,000
    such functions it takes about 3.4 s and 450 MB, where copies ran out of memory.

  `anImportedValueIsReadAsItsOriginsTypesOrRejected` covers ten cases, each on
  both backends:
  - the private bridge, by dispatch and by field;
  - the later replacing import, and the reverse order, which reads lib's types;
  - a public bridge through `import ... as L` and an alias;
  - a recursive `Node`;
  - a struct reachable only through a third module;
  - a type parameter named like a local struct;
  - a private interface;
  - a private struct inside a private union payload.

  **Inherited, not through this route.** Two cross-module paths still read a
  value through the importer's type of the same name, exactly as 0.9.0 does
  through an ordinary `pub fn` return. The first is binding a union variant's
  payload by name, from a value whose type is unknown. The second is passing
  such a value to a parameter typed with the importer's struct. This is the
  same-name collision family (G12), and it needs module-qualified type
  identity. Deferred. The same family covers three more routes, each of which
  behaves the same way without module variables:
  - a method's declared return type naming the exporter's private struct;
  - a variant name matched through the importer's unrelated union;
  - an interface method dispatched by simple struct name, when both modules
    declare a struct of that name.

  `anExportedTypeNamingAnotherModulesPrivateStructDoesNotCross` stays.

  **Re-export.** A struct-derived value re-exported through a middle module
  keeps its type when the type names only built-in containers and numbers
  (`Array<Array<float>>`). One that names structs may be rejected, because the
  middle module's struct table is checked, and a struct private to the first
  module does not resolve there. Rejection is a compile error, never a split.

  An `if` or `match` whose every arm gives the same struct-derived type carries
  it. A set literal is named like array and map literals
  (`aSetLiteralGivesItsElementType`). A variable takes the name and provenance
  of any struct-derived initializer, whatever form it arrives in.

  **Not a transport: a declared slot.** Passing a struct-derived value to a
  function parameter declared with a concrete type
  (`fn take(xs: Array<Array<float>>)`), or storing it in a plain struct's
  field, gives it that slot's declared type under 0.9.0's rules. Arithmetic on
  its numbers then splits by backend exactly as 0.9.0 splits for the same
  program without a `Box`. That is inherited and deferred with D10.

  `aStructDerivedValueSurvivesEveryTransport` crosses the five shapes with five
  transports: exported module state, a plain-struct bridge, the bridge inside a
  `for`, `pop`, map keys for the array shapes, and an `if`/`match`. That is 28
  cases: 5 shapes by 5 transports, plus map keys for the 3 array shapes. Each is read through
  two aliases on both backends. `anOrdinaryExportedOrPoppedValueStaysAsInZeroNine`
  (identical on 0.9.0) pins the baseline.

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
