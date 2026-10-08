# LogScope pain points

Everything below was hit while writing LogScope against Siyo 0.7.0. Each entry
has a minimal reproducer, what each backend did, and whether it was fixed in the
compiler/stdlib (with the commit and the regression test) or deferred (with the
reason). "Compiled" is `siyoc run`, "interpreted" is `siyoc interpret`.

Regression tests live in `src/test/java/codeanalysis/DogfoodRegressionTest.java`
(in-process, both backends), `CliParityTest.java` (the real CLI, both backends)
and `LogScopeEndToEndTest.java` (this application, both backends).

## Fixed

| # | Kind | Finding | Commit | Test |
|---|------|---------|--------|------|
| P1 | parity bug | `siyoc interpret f.siyo a b` dropped program args | 7b271c6 | `CliParityTest.interpretPassesProgramArgs` |
| P2 | silent miscompile | a lambda's tail `if/else` value returned `0`/`null` compiled | b19fbe0 | `aLambdaReturnsItsIfElseTailValue`, `anInlineComparatorWithAnIfElseTailSorts`, `aLambdaTailValueIsWidenedToItsDeclaredType` |
| P3 | type bug | `Map<string, Array<Rec>>` lost `Rec` after indexing | 36419ee | `aMapOfArraysKeepsTheElementStruct`, `aValueTakenFromAMapOfArraysKeepsItsType`, `aNestedMapKeepsItsInnerValueType` |
| P4 | tooling bug | `siyoc test` exited 0 when tests failed | cefde06 | `aFailingTestSuiteExitsNonZero`, `aFailingTestCaseExitsNonZero` |
| P5 | lexer bug | `"""text...` on the opening line was "Unterminated string literal", though GRAMMAR.md documents it | fdbe3e4 | `aTripleQuotedStringMayStartOnTheOpeningLine` |
| P6 | type bug + VerifyError | an arm that only `throw`s was typed `int`; an `if` expression whose first arm threw failed verification | e486cdf | `aMatchArmThatThrowsSitsBesideValueArms`, `anIfExpressionTakesItsTypeFromTheArmThatDoesNotThrow` |
| P7 | parity bug | interpreted, imported module variables were `null` and no module `init()` ran; compiled, a transitive module initialised lazily, after its importer | 262cf25 | `anImportedModulesStateIsInitialisedWhenInterpreted` |
| P8 | missing builtin | no way to ask what kind an erased value (a JSON field) is | a2ff34c | `typeOfNamesEveryKindOfValue`, `typeOfTellsJsonFieldsApart` |
| P9 | parity bug | a loop inside a block-bodied `match`/`if` arm crashed the interpreter | a2ff34c | `aLoopInsideAMatchOrIfArmRunsInterpreted` |
| P10 | parity bug | interpreted, a module's call into its own import failed: "Function body not found" | aa72b3e | `aModuleCallsIntoItsOwnImportWhenInterpreted` |
| P11 | silent wrong result | interpreted, catching an exception from a call left the callee's frame on the stack; the catcher read dead locals, a loop stopped after one catch | aa72b3e | `aLoopKeepsGoingAfterCatchingAnExceptionFromACall`, `aCallerReadsItsOwnParametersAfterCatching` |
| P12 | type rule | a `match` used as a statement still had to agree on one arm type (`Blank => {}` beside `{ push(...) }`) | 5810fd7 | `aMatchStatementsArmsNeedNotAgreeOnAType`, `aMatchStatementMayDiscardValuesOfDifferentTypes`, `aMatchWhoseArmsDisagreeIsStillRejectedAsAReturnValue` |
| P13 | parser gap | a struct field could not have a generic type (`items: Array<Item>`) | cc4acea | `aStructFieldMayHaveAGenericType` |
| P14 | diagnostics bug | syntax errors in an imported module were dropped and the program ran; interpreted diagnostics were placed in the wrong file | 68551d6 | `aSyntaxErrorInAnImportedModuleIsReported` |
| P15 | missing stdlib | writing to stderr needed `import java "java.lang.System"` in app code | 2d68e2c | `ioEprintlnWritesToStandardError` |
| P16 | output bug | under a C/POSIX locale every non-ASCII character printed as `?` | 2d68e2c | `outputIsUtf8WhateverTheLocale`, `LogScopeEndToEndTest` (runs under `LC_ALL=C`) |

### Reproducers

```siyo
// P1 — interpreted printed 0
import "std/os"
fn main() { println(toString(len(os.args()))) }      // siyoc interpret a.siyo x y

// P2 — compiled printed "0 0", interpreted "1 2"; sort(xs, <such a lambda>) was a no-op
fn main() {
    imut f = fn(a: int) -> int { if a > 0 { 1 } else { 2 } }
    println(toString(f(5)) + " " + toString(f(-5)))
}

// P3 — "Type object does not have members"
struct Rec { n: int }
fn main() {
    mut g: Map<string, Array<Rec>> = {}
    g["e"] = [Rec { n: 1 }]
    println(toString(g["e"][0].n))
}

// P4 — prints "0 passed, 1 failed" and exits 0
import "std/testing"
fn bad() { testing.assertEq("1", "2", "x") }
fn main() { testing.run("t", [bad]) }

// P5 — "Unterminated string literal"
fn usage() -> string { """usage: tool
  --flag""" }

// P6 — "cannot mix void and value arms"; the if form failed JVM verification
fn unwrap(p: Parsed) -> Opts { match p { Ok(o) => o, Bad(why) => { throw why } } }
fn half(n: int) -> int { imut h = if n % 2 == 0 { n / 2 } else { throw "odd" }
    h }

// P7 — interpreted printed "null"; std/testing's run() crashed on its null hook
// counter.siyo:  mut count = 7   pub fn get() -> int { count }
import "counter"
fn main() { println(toString(counter.get())) }

// P9 — interpreted: "Unexpected node: BlockStatement"
fn main() { match 2 { 2 => { for k in [1, 2] { println(toString(k)) } }, _ => println("") } }

// P10 — mid.siyo: import "base"  pub fn quad(n: int) -> int { base.twice(base.twice(n)) }
import "mid"
fn main() { println(toString(mid.quad(3))) }        // interpreted: Function body not found

// P11 — compiled "failed 3", interpreted "failed 1"
fn deep(k: int) -> int { throw "x" }
fn runAll(xs: string[]) -> int {
    mut failed = 0
    for mut i = 0 i < len(xs) i = i + 1 { try { deep(i) } catch e { failed += 1 } }
    failed
}

// P12 — rejected
for line in lines { match line { Blank => {}, Bad(n, why) => { push(bad, why) }, Rec(r) => { total = total + 1 } } }

// P13 — "Unexpected token <LessToken>"
struct Box { items: Array<Item> }

// P14 — broken.siyo: pub fn f() -> int { 1 +      (main imports it: ran, exit 0)

// P16 — LC_ALL=C siyoc run x.siyo printed "caf?"
fn main() { println("café") }
```

## Deferred

| # | Kind | Finding | Why deferred | Workaround in LogScope |
|---|------|---------|--------------|------------------------|
| D1 | ergonomics | A type annotation cannot be module-qualified: `fn f() -> cli.Options` is a parse error ("Unexpected token DotToken"). | Imported types are in scope unqualified by design (GRAMMAR.md, Modules); qualified type names are a language feature with aliasing implications, not a fix. The poor diagnostic is worth a follow-up. | Write `Options`. |
| D2 | diagnostics | When an imported module fails to bind, the importer also reports `Name 'mod' does not exist` for each use. | Follow-on noise only; the real error is reported first and against the right file. Suppressing it belongs with a broader cascade-suppression pass. | None needed. |
| D3 | type rule | A mixed array literal is rejected even when annotated: `imut xs: object[] = [1, "a"]`. | Not needed by LogScope (it builds `object[]` with `push`); deciding literal element unification against an annotation is a design change. | `push` into an `object[]`. |
| D4 | type rule | `error(msg)` is not known to diverge, so `B(s) => error(s)` beside a value arm is rejected. | `error` is an ordinary call to the verifier, so treating it as diverging needs emitter support (a dead value after the call). `throw` (fixed in P6) covers the need. | `throw msg`. |
| D5 | performance | The interpreter re-lowers a `try` body every time it runs. | Measured, not a problem: a 20,000-line summary takes 0.68 s interpreted (0.25 s compiled). | None needed. |
| D6 | lexer rule | The newline right before a closing `"""` is dropped, so a template ending in `<body>\n"""` joins the next line. | Deliberate in the lexer, and was undocumented (GRAMMAR.md now states it); changing it would alter existing programs' output. | Start the next piece with `"\n"`. |
| D7 | parity gap (REPL only) | In `siyoc repl`, `import "ctr"` then `ctr.bump()` reads the module's variable as `null`: each REPL line is a separate compilation with no shared module registry, so the module initialisation added for P7 never sees it. | Not a regression (module state was `null` in the REPL before 0.8.0 too) and outside what LogScope needs; fixing it needs "initialise each module once per REPL session" state tied to the REPL's variables. | Not applicable; LogScope does not use the REPL. |
