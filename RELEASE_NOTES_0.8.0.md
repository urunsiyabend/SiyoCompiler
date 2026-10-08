# Siyo 0.8.0

0.7.0 gave the language most of what a real program reaches for. This release
is what happened when one was written: LogScope (`projects/logscope`), a
multi-module CLI that reads JSONL logs, filters them, and writes error
summaries as text, JSON and HTML. Every place it got stuck is recorded with a
reproducer in `projects/logscope/PAIN_POINTS.md`, and the ones that were the
language's fault are fixed here rather than worked around in the app.

Most of them were the same defect this project cares most about: a program
that means one thing compiled and another interpreted. Several were silent.

2,229 tests pass, up from 2,184. LogScope itself is now part of `mvn test`:
its whole CLI runs through both backends, under a C locale, against golden
outputs.

---

## The interpreter and the compiler agree again

**A lambda returns its tail `if/else`.**

```siyo
imut cmp = fn(a: string, b: string) -> int { if a < b { -1 } else if a > b { 1 } else { 0 } }
sort(names, cmp)
```

Compiled, such a lambda returned `0` or `null`, so this `sort` was a silent
no-op. A named function already had its tail rewritten into a return; a lambda
now goes through the same rewrite, which also widens the tail to the declared
type (`fn() -> float { 3 }` used to fail verification).

**An exception unwinds the frames it passes through.** Interpreted, catching
an exception thrown from a called function left that call's frame on the
stack, and the function that caught it read its own parameters and locals from
the dead frame. A loop that caught a failure stopped after the first one —
`std/testing`'s `run()` reported 1 of 9 tests.

**Imported modules are initialised.** Interpreted, no imported module's
variables were initialised and no module `init()` ran, so every module
variable read as `null`. Compiled, a module that only read another module's
variable initialised it lazily, after its own `init()`. Both backends now
follow the order GRAMMAR.md documents: imports first, depth-first, then the
module's own variables, then its `init()`.

**A module may call into its own imports interpreted.** A call from one module
into a module it imported failed with "Function body not found".

**A loop inside a block-bodied `match` or `if` arm runs interpreted.** It
failed with "Unexpected node: BlockStatement".

**`siyoc interpret file.siyo a b` passes `a b`.** `os.args()` was empty.

---

## Small additions

**`typeOf(v)` names the kind of any value.**

```siyo
fn levelOf(m: map) -> string {
    if typeOf(m["level"]) != "string" { throw "\"level\" is not a string" }
    toString(m["level"])
}
```

It answers `int`, `long`, `float`, `bool`, `string`, `array`, `map`, `set`,
`fn`, `channel`, `null`, a struct's or sum type's name, or a Java object's
class name. A parsed JSON field could otherwise only be told apart by how it
printed. `typeName` is unchanged: it still answers only for structs.

**`io.eprintln(msg)` writes to standard error.** A CLI's diagnostics had to
import `java.lang.System` to get there.

**A struct field may have a generic type.**

```siyo
struct Summary { groups: Array<ErrorGroup>, byLevel: Map<string, int> }
```

Fields were parsed by their own subset of the type grammar, so this was a
syntax error although a parameter accepted it.

**A triple-quoted string may start on its opening line.**

```siyo
fn usage() -> string {
    """usage: logscope <command> <file.jsonl>

  --level L   only these levels"""
}
```

It used to open only when a newline followed `"""`; anything else lexed as
`""` and an unterminated string, although GRAMMAR.md documented this form. An
empty string can never be directly followed by another string, so `"""` now
always opens one. The newline right after the opening quotes, and the one
right before the closing quotes, are still dropped.

**An arm that throws has no type.**

```siyo
fn unwrap(p: Parsed) -> Options {
    match p {
        Opts(o) => o,
        Usage(why) => { throw why }
    }
}
```

The throwing arm was typed as `int` and rejected beside the struct arm; in an
`if` expression whose first branch threw, the placeholder type made the class
fail verification. A block arm ending in `throw` or `return` now takes no part
in deciding the type.

**A `match` written as a statement may have arms of different types.**

```siyo
for line in lines {
    match line {
        Blank => {},
        Bad(n, why) => { push(bad, why) },
        Rec(r) => { total = total + 1 }
    }
}
```

Its value is not used, so its arms need not agree. A match whose arms disagree
is still an error where its value is used — returned from a function, say.

---

## Tooling

**`siyoc test` fails when a test fails.** `std/testing` printed `FAIL` and the
counts, and the process exited 0, so CI could not see a failure. `siyoc test`
now runs every discovered file and then exits 1 if any case failed; running a
single test file with `run` or `interpret` exits 1 too.

**A syntax error in an imported module is reported.** It was dropped: the
importer compiled against whatever the parser recovered, and the program ran.
`siyoc interpret` also reports diagnostics the way `run` does, against the file
they were raised in.

**Output is UTF-8.** `siyoc` wrote standard output and error in the locale's
encoding, so under a C/POSIX locale every non-ASCII character became `?`.
Source files and file I/O were already UTF-8. On a terminal that is not set to
UTF-8 (a legacy Windows code page, for one), non-ASCII text now shows as its
UTF-8 bytes rather than as `?`.

---

## Still missing

Found by LogScope and deliberately left for later (see PAIN_POINTS.md):

- A type annotation cannot be module-qualified: `-> cli.Options` is a syntax
  error; imported types are written unqualified.
- A mixed array literal is rejected even when annotated `object[]`.
- `error(msg)` is not treated as diverging the way `throw` is.

And from 0.7.0, still open: generic structs, interface default methods and
bounds, `Empty { }`, a map literal as a tail value, and numeric `as`.
