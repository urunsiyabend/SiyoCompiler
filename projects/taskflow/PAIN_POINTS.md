# TaskFlow pain points

Everything below was hit while writing TaskFlow against Siyo 0.8.0. Each entry
has a minimal reproducer, what each backend did, and whether it was fixed (with
the regression test) or deferred (with the reason). "Compiled" is `siyoc run`,
"interpreted" is `siyoc interpret`.

Regression tests assert the same output on both backends. They live in
`src/test/java/codeanalysis/TaskFlowRegressionTest.java`, `CliParityTest.java`
and, for the application as a whole, `TaskFlowEndToEndTest.java`.

## Fixed

| # | Kind | Finding | Test |
|---|------|---------|------|
| P1 | **silent wrong result** | interpreted, a function's locals lived in the globals, so a recursive call overwrote its caller's locals and loop counters: `fact(5)` was `0`, a loop stopped early. TaskFlow's cycle search returned wrong groups. | `aRecursiveCallDoesNotDisturbItsCallersLoop`, `...Locals`, `...BindingsOrCatchVariables`, `aGlobalIsStillSharedAndAClosureStillCaptures` |
| P2 | **silent wrong result** | interpreted, `break`/`continue` inside `try` raised a NullPointerException that the program's own `catch` then handled: compiled 2, interpreted 101 | `continueLeavesATryInsideALoop` |
| P3 | **silent wrong result** | interpreted, `return` inside a block arm went on to run the arm's tail | `returnFromAnArmSkipsTheArmsTail`, `anArmJumpsOutOfAValueExpression` |
| P4 | **silent wrong result** | interpreted, `mut h: long = 0` stayed an int and wrapped at 2^31 | `aDeclaredLongVariableDoesNotWrapAtAnInt`, `TaskFlowEndToEndTest.estimatesThatOverflowAnIntStillAddUp` |
| P5 | parity crash | interpreted, `break`/`continue` in a block-bodied match arm crashed | `continueAndBreakLeaveAMatchArm` |
| P6 | type bug | `mut ts: Array<T> = []` took `Array<T>` itself as the element type: `ts[0].id` was rejected | `anEmptyGenericArrayKnowsItsElementType`, `...KeepsItsElementStruct`, `anEmptyNestedArrayKeepsItsInnerType` |
| P7 | type bug | `pop` erased what it returned | `popKeepsTheElementType`, `popKeepsTheElementStruct`, `popFromAnArrayOfArraysKeepsTheInnerType`, `popOfAnUntypedArrayIsStillErased` |
| P8 | crash | `for x in set` crashed on both backends, differently; so did `for` over an erased set or map | `aForLoopWalksASetInInsertionOrder`, `aForLoopOverASetKnowsItsElementType`, `aForLoopOverAnUntypedSetIsErased`, `aForLoopWalksAnErasedValueByWhatItIs` |
| P9 | type bug | a generic type did not survive a call or a loop: `imut s = sets(); for n in s[1]` and `for batch in p.batches { for t in batch { t.id } }` | `aVariableKeepsTheGenericTypeOfTheCallItCameFrom`, `aLoopVariableKeepsTheGenericTypeItWasWalkedFrom`, `aForLoopOverATypedMapKnowsItsKeyType` |
| P10 | parity crash | a number stored in a declared `long`/`float` field, map slot or array stayed an int: compiled ClassCastException on read | `aStoredNumberWidensToTheDeclaredType` |
| P11 | VerifyError | an `else if` in an if expression bound to the literal 0: `imut x = if a { "p" } else if b { "q" } else { "r" }` | `anElseIfChainIsAValue` |
| P12 | VerifyError | a void `if/else` ending a match arm, or one with a void arm beside a value arm | `aVoidElseIfChainMayEndAMatchArm`, `anIfWhoseArmsAreVoidAndValueRunsForEffects` |
| P13 | parity crash | `m["no"] == null` on a `Map<string, int>` unboxed first: compiled NullPointerException, interpreted `true` | `aMissingKeyOfATypedMapComparesToNull` |
| P14 | parity crash | a stack overflow was caught compiled, and ended the process interpreted | `aStackOverflowIsCaughtTheSameWayOnBothBackends`, `CliParityTest.runawayRecursionIsCatchableOnBothBackends` |
| P15 | parser gap | a map literal could not be a tail value (`fn f() -> map { {"a": 1} }`), a 0.7.0 limitation TaskFlow's report wanted | `aMapLiteralMayBeATailValue`, `aMapLiteralMayEndAMatchArmAndSpanLines`, `aBareBlockIsStillABlock` |
| P16 | missing + diagnostics | `"$xs"` with an array, struct or map was an operator error, located at (1, 1) | `interpolationConvertsAnyValue`, `interpolatingASetOrSumValueWorks` |
| P17 | missing stdlib | `json.parse` only reads an object, and a task file may be an array: `json.parseValue` | `parseValueReadsAnyDocument`, `parseStillWantsAnObject` |
| P18 | stdlib bug | the "strict" parser accepted `01`, a raw tab in a string, `٣` as a digit and `\u+123` | `aLeadingZeroIsRejected`, `aRawControlCharacterInAStringIsRejected`, `aDigitMustBeAnAsciiDigit`, `aBadUnicodeEscapeIsRejected` |
| P19 | stdlib bug | a duplicate key kept the last value, so `{"deps": ["a"], "deps": []}` silently lost a dependency | `aDuplicateKeyIsRejected`, fixtures `duplicate_key.json`, `duplicate_field.json` |
| P20 | stdlib bug | deep nesting overflowed the stack, and the message differed by backend (`StackOverflowError` / `java.lang.StackOverflowError`) | `aDocumentNestedTooDeeplyIsMalformedNotAnOverflow`, `aReasonablyDeepDocumentStillParses`, `TaskFlowEndToEndTest.aDocumentNestedTooDeeplyIsReportedNotACrash` |
| P21 | stdlib gap | a valid integer wider than a long failed with `For input string`; a byte order mark was rejected; an error in a long file gave only an offset | `anIntegerTooLongForALongIsAFloat`, `aByteOrderMarkIsIgnored`, `aMalformedDocumentNamesItsLineAndColumn` |
| P22 | stdlib bug | `json.stringify` wrote a struct as the quoted text of `toString`, and a set likewise | `stringifyWritesAStructByItsFields` |
| P23 | performance | `io.readFile` and `strings.join` concatenated in a loop: 3.6 MB took 1m48s. `readFile` also dropped leading blank lines | `readingAndJoiningALargeTextIsNotQuadratic`, `readFileKeepsLeadingBlankLines` |
| P25 | regression caught in review | fixing P1 removed the leak that nested closures, spawns and nested `fn`s read outer locals through: interpreted they read `null`. Capture is now lexical and by symbol. A lambda or spawn holds what anything nested in it captures, a `fn` declared in a function holds what it refers to and what the nested functions it calls hold, and a reference to such a `fn` closes over those variables, shared with the parent when mutable. No local goes back in the globals. As a side effect these shapes that were internal errors compiled now work on both backends: a closure in a closure reading a local, reading a global, and writing a local, `scope` reading a captured variable inside a lambda, and a `for` inside a lambda. A nested `fn` using its parent's locals is reported as not compiled yet (D10) rather than an internal error or, with a same-named global, a silently wrong answer. | `aNestedClosureReadsAVariableItsParentNeverNamed`, `aNestedClosureThatOutlivesItsFunctionKeepsItsCapture`, `aNestedClosureWritesThroughToTheOuterVariable`, `aFunctionDeclaredInsideAFunctionReadsItsLocals`, `anEscapedNamedFunctionKeepsAnImmutableCapture`, `anEscapedNamedFunctionSharesAMutableCapture`, `aNamedFunctionAndItsParentSeeTheSameVariable`, `anEscapedNamedFunctionKeepsTheFrameItWasMadeIn`, `aParameterIsCapturedLikeALocal`, `aNamedFunctionCarriesWhatItsSiblingsAndChildrenNeed`, `aNamedFunctionMayRecurseAfterEscaping`, `aNamedFunctionsOwnLocalIsNotACapture`, `aSpawnMayCallANamedFunctionThatCaptures`, `aLocalDoesNotLeakIntoTheGlobals`, `aCaptureMayBeReadOnlyInsideAnyForm`, `aLambdaInsideAFunctionThatItCallsSeesTheFunctionsCaptures`, `aSameNamedLocalDoesNotHideACaptureOfACalledFunction`, `aSpawnInsideAFunctionItCallsSeesTheFunctionsCaptures`, `aGlobalIsNotCapturedByANestedFunction`, `compiledANestedFunctionUsingItsParentsLocalsIsReportedNotMiscompiled`, `aLambdaMayDeclareLocalsInsideTryAndScope`, `aLambdaInANestedFunctionMayReadAGlobal`, `aNestedFunctionInAMethodMayReadAGlobalWhateverComesFirst`, `aLambdaTellsAnInnerLocalFromAnOuterVariableOfTheSameName`, `aSpawnTellsAnInnerLocalFromAnOuterVariableOfTheSameName`, `aForLoopInALambdaKeepsItsOwnCounters`, `aClosureInAClosureMayReadAGlobalAndALocal`; the tests of a nested `fn` reading locals assert the interpreter only |
| P26 | VerifyError / parity | a value-returning function whose tail arm only loops or falls off the end failed verification; `if a { 1 } else { return 2 }` returned a default compiled | `aTailArmThatOnlyLoopsFallsThroughLikeAnyOtherPath`, `anIfArmMayReturnBesideAValue`, `anIfThatReturnsInOneArmMayFallThroughInTheOther` |
| P27 | silent wrong result | `fn a(x: int) -> map { if x == 1 { {"k": 1} } else { {} } }` returned null compiled and the map interpreted. A value beside an arm that falls off the end is now rejected, in an `if` or a `try` tail | `aValueBesideAnArmThatFallsOffTheEndIsRejected`, `aTryTailWithAValueBesideAVoidCatchIsRejected` |
| P28 | crash | an expression with no value used as one, such as `println(if a { imut b = 1 } else { imut c = 2 })`, failed verification or an internal error. It is now reported; an `if` whose arms both leave is not such an expression | `anExpressionWithNoValueIsRejectedWhereOneIsNeeded`, `anArgumentWithNoValueIsReportedOnce`, `anIfWhoseArmsBothLeaveMayInitialiseAVariable` |
| P29 | parity crash | `fn g() -> float[] { return [1, 2] }` kept ints: compiled ClassCastException on read, and `p([1, 2])` divided as ints interpreted. A literal widens to its declared element type as an initializer, a return, an argument and a field; a compiled read of a `long` or `float` tolerates a narrower number from an erased container, and compiled, a float read at `long` is still an error rather than a truncation (interpreted differs: D15) | `aLiteralWidensToItsDeclaredElementType`, `aLiteralArgumentOrFieldWidensToItsDeclaredElementType`, `aSetOfFloatsAcceptsAnIntAndReadsAFloat`, `aFloatReadAtADeclaredLongIsAnErrorNotATruncation` |
| P30 | diagnostics | `break` or `continue` in a lambda or spawn inside a loop was accepted: an internal error compiled, a jump out of the closure interpreted | `breakCannotLeaveALambdaOrASpawn` |
| P31 | tooling | `siyoc run missing.siyo` printed a Java stack trace | `CliParityTest.aMissingSourceFileIsReportedWithoutAStackTrace` |
| P33 | silent wrong result | function symbols compare by name, so two nested `fn`s of one name shared the last body, and a nested `helper` replaced a top-level `helper`, on both backends: printed `2 2 20 20` for `1 2 10 20` | `nestedFunctionsOfOneNameAreSeparateFunctions`, `CliParityTest.nestedFunctionsOfOneNameInDifferentModulesAreSeparate`, `aNestedFunctionMayBeDeclaredInAnyBlock`, `functionsInABlockMayCallEachOtherAheadOfDeclaration`, `aNestedFunctionNamedLambdaDoesNotCollideWithAClosure`, `anOverloadedNestedFunctionPicksByArgumentCount`, `aNestedFunctionDeclaredTwiceInOneBlockIsRejected` |
| P34 | regression caught in review | the capture walks listed the composite nodes `getChildren` leaves out by hand, and missed some: a variable used only as a union argument (`A(x)`), a cast operand (`x as String`) an actor constructor argument (`spawn Box.new(x)`) or a spawned call's argument (`spawn show(x)`) was not captured, so an escaped nested `fn` read null interpreted and, with a same-named global, read the global compiled. `BoundOperands` reads a node's fields, so a composite cannot be missed; the capture walks and the emitter's search for lambdas and spawns all use it. As a side effect, a lambda using a capture in those forms and a lambda inside a `spawn` now compile. | `CaptureTraversalTest` (every form, as an escaped `fn`, a same-named global compiled, and a lambda on both backends; a field audit of every node class; compile time of deep nesting), `aCaptureUsedOnlyAsAUnionArgumentIsHeld`, `aCaptureUsedOnlyAsACastOperandIsHeld`, `compiledAMissedCaptureNeverReadsASameNamedGlobal`, `anActorSpawnsConstructorArgumentIsACapture`, `aSpawnedCallHoldsItsArguments`, `aSpawnHoldsWhatACompositeExpressionUses`, `aSpawnMayNotHoldAMutableThroughACompositeExpression`, `CliParityTest.anImportedModulesNestedFunctionCapturesFromCompositeExpressions` |
| P32 | silent wrong result | interpreted, `5L == 5` was false, `NaN == NaN` true and `0.0 == -0.0` false; compiled had each right | `numbersCompareByValue` |
| P24 | missing stdlib | `parseInt` answers 0 for anything unreadable, so `--workers abc` and `--workers 0` looked alike: `strings.isInt` | `isIntTellsWhatParseIntCanRead` |

### Reproducers

```siyo
// P1 — interpreted 0, compiled 120
fn fact(n: int) -> int {
    imut before = n
    if n == 0 { return 1 }
    imut below = fact(n - 1)
    before * below
}
fn main() { println(toString(fact(5))) }

// P1 — interpreted stopped after "0 sees 1"
fn walk(n: int, kids: Array<Array<int>>) {
    for w in kids[n] {
        println(toString(n) + " sees " + toString(w))
        if w > 0 { walk(w, kids) }
    }
}
fn main() { walk(0, [[1, 2], [], []]) }

// P2 — compiled 2, interpreted 101
fn main() {
    mut c = 0
    for x in [1, 2, 3, 4, 5] {
        try {
            if x == 2 { continue }
            if x == 4 { break }
            c += 1
        } catch e { c = 100 }
    }
    println(toString(c))
}

// P3 — interpreted ran effect()
fn f(n: int) -> int { match n { 1 => { if n == 1 { return 1 }
                                       effect() }, _ => 2 } }

// P4 — interpreted -294967296
fn main() {
    mut h: long = 0
    for i in [1000000000, 1000000000, 1000000000, 1000000000] { h += i }
    println(toString(h))
}

// P5 — interpreted: NullPointerException
for r in rs { match r { Good(v) => { use(v) }, Skip => { continue }, Stop => { break } } }

// P6 — "Type SiyoArray does not have members"
struct T { id: string }
fn main() { mut ts: Array<T> = []
            push(ts, T { id: "a" })
            println(ts[0].id) }

// P7 — "Cannot convert Object to Integer"
fn main() { mut ns: Array<int> = [1]
            imut xs = ["p", "q"]
            println(xs[pop(ns)]) }

// P8 — IncompatibleClassChangeError / "Cannot index type: SiyoSet"
fn main() { mut s: Set<string> = #{"a"}
            for k in s { println(k) } }

// P9 — ClassCastException: SiyoSet cannot be cast to List
fn sets() -> Map<int, Set<string>> { mut m: Map<int, Set<string>> = {}
                                     m[1] = #{"a"}
                                     m }
fn main() { imut s = sets()
            for n in s[1] { println(n) } }

// P10 — ClassCastException: Integer cannot be cast to Long
struct P { r: long }
fn main() { imut p = P { r: 0 }
            println(toString(p.r + 1)) }

// P11 — VerifyError: Bad type on operand stack
fn pick(d: int) -> string {
    imut x = if d == 1 { "one" } else if d == 2 { "two" } else { "many" }
    x
}

// P12 — VerifyError: Inconsistent stackmap frames
match k { B => f(0), A(d) => { if d == 1 { f(1) } else if d == 2 { f(2) } else { f(3) } } }

// P13 — compiled NullPointerException, interpreted true
fn main() { mut m: Map<string, int> = {}
            println(toString(m["no"] == null)) }

// P14 — compiled "caught", interpreted: process ended
fn forever(n: int) -> int { 1 + forever(n + 1) }
fn main() { try { forever(0) } catch e { println("caught") } }

// P15 — "Unexpected token: <ColonToken>"
fn mk() -> map { {"a": 1} }

// P16 — "(1, 1): Binary operator '+' is not defined for String and SiyoArray"
fn main() { imut a = [1, 2]
            println("a is $a") }

// P17, P18, P19, P20, P21 — json.parse gave respectively:
//   [1, 2]                   invalid: expected an object
//   {"a": 01}                accepted
//   {"a": 1, "a": 2}         accepted, {"a": 2}
//   {"a": then "[" 200,000   invalid: StackOverflowError
//   {"a": 123456789012345678901}  invalid: For input string
// P22 — printed "T { id: a }" with its quotes
import "std/json"
struct T { id: string }
fn main() { println(json.stringify(T { id: "a" })) }

// P23 — 1m48s
import "std/io"
import "std/strings"
fn main() { mut xs: string[] = []
            for mut i = 0 i < 200000 i = i + 1 { push(xs, "line number " + toString(i)) }
            io.writeFile("big.txt", strings.join(xs, "\n"))
            println(toString(len(io.readFile("big.txt")))) }

// P24 — both print 0
fn main() { println(toString(parseInt("0")))
            println(toString(parseInt("12x"))) }
```

## TaskFlow's own bugs, found in testing

| Finding | Fix | Test |
|---------|-----|------|
| Hours were `int`: 3,000 tasks of 1,000,000 hours came to a negative total. | Hours are `long`. | `hoursDoNotOverflowAnInt`, `estimatesThatOverflowAnIntStillAddUp` |
| Control characters were escaped with a decimal code, so ESC showed as `'`, and C1 controls such as U+009B reached the terminal. | Hexadecimal, and C1 is escaped. | `controlCharactersBecomeEscapes`, `textOutputCarriesNoControlCharacterFromTheInput` |
| Messages depended on the order files were listed in, and on task order. | Files are read in sorted order; tasks and their dependencies are sorted before they are checked. | `theOrderOfFilesAndTasksDoesNotChangeTheResult`, `unknownDependenciesAreReportedInSortedOrder`, golden `validate_clash.txt` |
| `cycles` said `no cycles`, exit 0, for malformed input. | It reports the problems and exits 3. | golden `cycles_malformed.txt`, `cycles_malformed.json` |
| `--target` walked through `done` tasks, planning work nobody needed. | The walk stops at them. | `aTargetDoesNotLookThroughDoneWork`, `generatedTargetPlansObeyEveryRule` |
| `--target ""` silently planned everything; a failed target printed `nothing to do`. | It is an unknown target; plans list the failed tasks in scope. | `anEmptyTargetIsStillATarget`, `anUnknownTargetIsInvalidInput`, `aFailedTargetSaysSo` |
| A `dependencies` entry naming a missing task was accepted when its `needs` was empty, null or absent: only edges were checked, so `{"tasks":[{"id":"a"}],"dependencies":[{"task":"missing","needs":[]}]}` validated and planned. With several needs it was reported once per edge. | Every entry's task is checked once, after all files are read. | `aDependencyEntryMustNameATaskEvenWithNoEdges`, `anUnknownTaskIsReportedOncePerEntry`, `anEntryWithNoEdgesMayNameATaskInALaterFile`, `aDependencyEntryNamingAMissingTaskIsInvalidHoweverManyEdgesItHas`, `aDependencyEntryMayNameATaskInAnotherFileWhicheverIsListedFirst` |
| A cycle could start part-way round a group, which the README ruled out. | Search from the smallest id back to itself. | `aWalkMayRejoinPartWayRound`, `generatedCyclesMatchAnIndependentSearch` |
| Arguments and paths reached standard error unescaped. | Every error goes through `model.safe`. | `errorMessagesCarryNoControlCharacters` |
| `{"tasks": null}` was an error, though `null` reads as absent everywhere else. A bad `id` hid the task's other problems. Whitespace was a valid id. | All fixed in the loader. | `nullSectionsReadAsAbsent`, `everyFieldOfABadTaskIsChecked` |

## A change tried and withdrawn

Running programs on a 512 MB stack let the recursive cycle search handle long
chains, and was committed, then reverted after review (a12b65a). It masked
unbounded recursion instead of fixing anything: a runaway recursion went from
0.08 s and 50 MB RSS to 2.6 s and 2.3–3.3 GB, enough for the memory cgroup's OOM
killer to end test runs on the development machine. It also helped only `siyoc
run` and `interpret`, since `spawn` and `siyoc compile` output kept the old limit.
TaskFlow's walk now keeps its own stack, which is right whatever the runtime does:
depth comes from untrusted input.

## Deferred

| # | Kind | Finding | Why deferred | Workaround in TaskFlow |
|---|------|---------|--------------|------------------------|
| D1 | parity gap | Recursion depth differs by backend: on the default stack `deep(10000)` works compiled, and `deep(1000)` overflows interpreted. Both now raise a catchable `StackOverflowError` (P14). | Reducing the interpreter's Java frames per Siyo call is a redesign, and a larger stack was tried and withdrawn, above. | The graph walk keeps its own stack (`src/graph.siyo`). |
| D2 | VerifyError | A `try` expression where the operand stack is not empty fails verification compiled and works interpreted: `println("x" + try { "y" } catch e { "z" })`. Also in 0.8.0. | A JVM handler clears the stack, so this needs spilling operands or moving the try into a synthetic method. TaskFlow never needs it. | `imut v = try { ... } catch e { ... }`, then use `v`. |
| D3 | parity gap | `imut v = m["missing"]` on a `Map<string, int>`: compiled NullPointerException, interpreted `null`. | An `int` cannot hold null. Deciding what this should mean is a language decision; `m.has(k)` or a null check (P13) asks first. | `byId.has(id)` throughout. |
| D4 | diagnostics | A reserved word as a name (`scope`) gives a cascade of `Unexpected token` messages. | Cosmetic; belongs with parser error recovery. | Renamed the variable. |
| D5 | missing stdlib | No priority queue. | One use, 40 lines of Siyo; a `std/collections` needs a generic struct to be good, which is still missing. | `src/heap.siyo`. |
| D6 | tooling | An uncaught `throw` prints a Java stack trace, a different one per backend. Exit status and message agree. | A proper Siyo stack trace is its own feature. | Every failure is caught and reported by `main`. |
| D7 | type rule | `imut xs: object[] = [1, "a"]` is still rejected (LogScope D3). | Not needed: reports are built with `push`. | `push` into an `object[]`. |
| D8 | language rule | `parseInt("12x")` still answers 0. | Changing it would alter existing programs; `strings.isInt` (P24) asks first. | `strings.isInt`. |
| D9 | output | Bidirectional controls such as U+202E are not escaped in text output. Column padding counts UTF-16 units, and `len("😀")` is 2. JSON output leaves C1 controls raw, which RFC 8259 allows. A lone surrogate prints as `?`. Invalid UTF-8 decodes to U+FFFD, so two different bad ids can look like a duplicate. Under `LC_ALL=C` the JVM cannot decode a non-ASCII argument. | Display width and encoding policy are problems of their own. | Only fixed status names are padded. JSON is for programs. |
| D10 | not compiled | Closure conversion in the emitter has gaps. All of these work interpreted. Compiled: a `fn` declared in a function that uses that function's locals, called or escaped, and in an imported module too (reported at the main file's (1, 1)), is reported as `Function 'k' uses 'x' from the function around it, which is not compiled yet`. Any loop (`for` or `while`) around `scope { spawn { } }` is an internal compiler error. These fail at load or run time: a lambda reading a top-level `mut` or capturing `self` (ClassCastException; an actor method's can hang), a lambda capturing a `mut` parameter or walking an `imut` captured map or set with `for` (VerifyError; a `mut` one works), a lambda using a captured `mut` actor handle (ClassCastException). All also fail in 0.8.0 or in the same shape as an internal error. These do work compiled: a lambda using a captured variable in every form listed in `CaptureTraversalTest`, including a closure in a closure, `for`, `while`, `try`, `scope`, `scope { spawn { } }`, a lambda inside a `spawn`, a lambda in a union argument or an array, and a nested `fn` that uses no parent locals. | A proper closure conversion is a project of its own, and TaskFlow's closures are all single-level comparators. | TaskFlow does not nest closures. |
| D11 | type rule | A literal that contradicts its annotation, `imut m: Map<int, string> = {"1": "a"}`, is not rejected; compiled, walking its keys fails on the cast, interpreted it prints. | A type check of literals against generic annotations is a feature of its own. | Not applicable. |
| D13 | parity gap | A value-returning function that can reach its end without a value: `fn v(a: int) -> int { if a == 1 { 5 } }` gives 0 compiled, 5 interpreted, and `fn() { 5 }` gives null and 5. The interpreter takes the last value evaluated. Also in 0.8.0. | Making the interpreter return the type's default touches every call path; the mixed-arm rule (P27) already catches the common form. | Every TaskFlow function returns on every path. |
| D14 | language rule | `NaN > 1` is true compiled and false interpreted; `string < object` fails verification compiled; `#{1}.has(1L)` is false. `scope { for x in xs { spawn { } } }`, a spawn in a block inside `scope`, fails on both backends, and a function calling a nested `fn` declared later in the function's own top-level block is `does not exist`. `fn g() -> object { 41 }` fails verification compiled. An actor spawned inside a block spawn raises NullPointerException interpreted, and one spawned in an imported module fails compiled (IncompatibleClassChangeError or `Unknown method`). An actor writing a global works on its own copy interpreted: `c.m()` gave 4 where compiled gave 14. All in 0.8.0. | Not hit by TaskFlow, found by review; each is its own small fix to come. | Not applicable. |
| D15 | parity gap | A float read at a declared long through an erased value: `imut o: object = 1.5` then `imut a: long = o` raises compiled and holds 1.5 interpreted, because the interpreter does not check an annotation at run time. `imut e: Array<long> = [2.5]` splits the same way. Also in 0.8.0 for the first form. | Checking annotations at run time in the interpreter is a change of its own. `aFloatReadAtADeclaredLongIsAnErrorNotATruncation` asserts the compiled backend only. | TaskFlow checks `typeOf` before narrowing. |
| D16 | language rule | A `spawn` body may not hold a `mut` variable or a shared container, and now that includes one it uses in any form (`[x]`, `A(x)`), reaches through a nested `fn` it calls, or uses after an inner local of the same name; 0.8.0 interpreted let those through. The check is not made through a function still being bound, such as a spawn inside `k` calling `k`, for `mut` variables and containers alike: that spawn gets the value. This bypass is also in 0.8.0. A nested `fn` written `len` now shadows the builtin, as any nested name does. `ma.inner()` no longer reaches a function nested in `ma`'s function. | These follow the scoping rules rather than being gaps; recorded because 0.8.0 behaved differently. | Not applicable. |
| D12 | resources | Out of memory is a Java stack trace and exit status 1. | Only heap exhaustion reaches it; a Siyo `catch` should not pretend to recover. | Documented in the README. |
