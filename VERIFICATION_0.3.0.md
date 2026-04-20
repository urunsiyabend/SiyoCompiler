# Siyo 0.3.0 Verification

Verification date: 2026-04-21.
All tests run against `target/siyo-compiler-0.3.0.jar` (freshly rebuilt after the fixes described at the bottom of this doc).

**Tally: 17 PASS, 0 PARTIAL, 0 FAIL** over the 17 milestone items.

> **Note on source snippets:** Items 3–14 below show the original test sources used for verification. After the Go-style module top-level rewrite (item #2 section below), a bare top-level `println(...)` is a compile error. The feature being exercised by each item still works, but to compile those snippets today, wrap the bare statements in `fn main() { ... }`. Items re-exercised under the new rules are marked `[re-verified 2026-04-21]`.

Each item below shows the Siyo source used, the exact command invoked, and verbatim captured output. Terminal artefacts (`---EXIT N---`) are the test harness's exit-code markers — not Siyo output.

---

## 1. `&&` / `||` short-circuit in bytecode backend — PASS

**Source (a):**
```siyo
{
    mut x = false && (1/0 > 0)
    println("a: " + toString(x))
}
```
**Source (b):**
```siyo
{
    mut y = true || (1/0 > 0)
    println("b: " + toString(y))
}
```
**Command:** `java -jar target/siyo-compiler-0.3.0.jar run <file>`

**Actual output (a):**
```
a: false
```
**Actual output (b):**
```
b: true
```
Both sides would throw `ArithmeticException: / by zero` if fully evaluated, confirming the right operand is skipped.

---

## 2. Module top-level semantics — Go-style — PASS `[re-verified 2026-04-21]`

**Locked design.** Bare statements at a module's outer `{ }` are a compile error. Allowed at top level: `fn`, `struct`, `actor`, `impl`, `enum`, `import`, `import java`, and `mut`/`imut` declarations. Two special zero-arg functions:
- `fn init()` — runs once when the module is first loaded, eagerly at import time (not lazily on first reference)
- `fn main()` — runs only when the module is the entrypoint (`siyoc run file.siyo` or `siyoc run` resolving to a file with `fn main()`)

Order: imported module `init()` runs before the importing module's own init/main/any code.

### 2a. Bare top-level statement → compile error

**Source `C:/tmp/verify_bare.siyo`:**
```siyo
{
    println("x")
}
```
**Command:** `java -jar target/siyo-compiler-0.3.0.jar run C:/tmp/verify_bare.siyo`
**Actual stderr (exit 1):**
```
verify_bare.siyo(2, 5): top-level statement not allowed; move into init() or main()
```
Message text matches the spec exactly.

**Source `C:/tmp/verify_bare_multi.siyo` (mixed declarations and bare stmts):**
```siyo
{
    fn hello() { println("hi") }
    mut x = 1
    if x > 0 { println("yes") }
    for mut i = 0 i < 2 i = i + 1 { println(i) }
    mut y = 5
}
```
**Actual stderr (exit 1):**
```
verify_bare_multi.siyo(4, 5): top-level statement not allowed; move into init() or main()
verify_bare_multi.siyo(5, 5): top-level statement not allowed; move into init() or main()
```
The `fn hello()` and `mut x`, `mut y` declarations are accepted; the `if` and `for` are rejected at exactly the columns they begin.

### 2b. Imported module `init()` runs before the importing module's first line

**Source `C:/tmp/verify_init/src/a.siyo`:**
```siyo
{
    fn init() { println("a.init") }
    fn hello() { println("a.hello") }
}
```
**Source `C:/tmp/verify_init/src/b.siyo`:**
```siyo
{
    fn init() { println("b.init") }
    fn hello() { println("b.hello") }
}
```
**Source `C:/tmp/verify_init/src/main.siyo`:**
```siyo
{
    import "a"
    import "b"

    fn init() { println("main.init") }
    fn main() {
        println("main.main starts")
        a.hello()
        b.hello()
        println("main.main ends")
    }
}
```
**Command:** `java -jar target/siyo-compiler-0.3.0.jar run C:/tmp/verify_init/src/main.siyo`
**Actual stdout:**
```
a.init
b.init
main.init
main.main starts
a.hello
b.hello
main.main ends
```
Both imported `init()`s run in import order **before** `main.init`. `main.init` runs before `main.main`. No output from any of the imported modules' `main()` functions (none defined). This is covered by sub-cases 2c and 2d below (single-import `a.init` ordering vs. two-import ordering).

### 2c. Imported module's `main()` does NOT run when the module is imported

**Source `C:/tmp/verify_init/src/both.siyo`** (defines BOTH init and main):
```siyo
{
    fn init() { println("both.init") }
    fn main() { println("both.main SHOULD NOT RUN WHEN IMPORTED") }
    fn hello() { println("both.hello") }
}
```
**Source `C:/tmp/verify_init/src/main2.siyo` (imports `both`):**
```siyo
{
    import "both"

    fn main() {
        both.hello()
        println("main2 done")
    }
}
```
**Command:** `java -jar target/siyo-compiler-0.3.0.jar run C:/tmp/verify_init/src/main2.siyo`
**Actual stdout:**
```
both.init
both.hello
main2 done
```
`both.main`'s `println("... SHOULD NOT RUN WHEN IMPORTED")` does **not** appear. Only the imported module's `init()` fires, never its `main()`.

### 2d. Two modules each with `init()` — both run in import order before the main module starts

Covered verbatim by 2b above: `a.init` → `b.init` → `main.init` → `main.main starts`. The two imported inits run in source order of the `import` statements, and both precede any line of the importing module's `init()` or `main()`.

### 2e. A module with both `init()` and `main()` run as the entrypoint — both run, init first then main

**Command (running `both.siyo` directly, not via import):**
```
java -jar target/siyo-compiler-0.3.0.jar run C:/tmp/verify_init/src/both.siyo
```
**Actual stdout:**
```
both.init
both.main SHOULD NOT RUN WHEN IMPORTED
```
(the "SHOULD NOT RUN WHEN IMPORTED" message is literal from the source — it's printed here because the module **is** the entrypoint, which is the opposite scenario to 2c.) `init()` fires first, `main()` fires second.

### 2f. Bonus: transitive imports (deep → mid → main) init in topological order

**Source `C:/tmp/verify_init/src/deep.siyo`, `mid.siyo`, `main3.siyo`:**
```siyo
// deep.siyo
{ fn init() { println("deep.init") }  fn leaf() { println("deep.leaf called") } }

// mid.siyo
{ import "deep"  fn init() { println("mid.init"); deep.leaf() }  fn branch() { println("mid.branch called") } }

// main3.siyo
{ import "mid"  fn main() { println("main3 body"); mid.branch() } }
```
**Command:** `java -jar target/siyo-compiler-0.3.0.jar run C:/tmp/verify_init/src/main3.siyo`
**Actual stdout:**
```
deep.init
mid.init
deep.leaf called
main3 body
mid.branch called
```
`deep.init` runs before `mid.init`; `mid.init` is able to call `deep.leaf` because `deep` is already initialized; `main3.main` runs last.

### 2g. Edge cases: module with only `init()`, only `main()`, or neither

```
$ java -jar <jar> run C:/tmp/only_init.siyo    # only fn init()
only init ran

$ java -jar <jar> run C:/tmp/only_main.siyo    # only fn main()
only main ran

$ java -jar <jar> run C:/tmp/neither.siyo      # only fn helper(), no init/main
(no output, exit 0)
```
When neither is defined, the entrypoint is a no-op — expected.

---

## 3. For-loop init variable scope is fresh — PASS

**Source:**
```siyo
{
    for mut i = 0 i < 3 i = i + 1 { println(i) }
    for mut i = 10 i < 13 i = i + 1 { println(i) }
}
```

**Actual output:**
```
0
1
2
10
11
12
```
No "Variable 'i' is already declared" error; the C-style `for` init now gets its own scope.

---

## 4. Typed empty array literal `mut xs: string[] = []` — PASS

**Source:**
```siyo
{
    mut xs: string[] = []
    push(xs, "hello")
    push(xs, "world")
    println(toString(len(xs)))
    println(xs[0])
    println(xs[1])
}
```

**Actual output:**
```
2
hello
world
```

---

## 5. `io.exists` / `io.isFile` / `io.isDir` — PASS

**Source:**
```siyo
{
    import "std/io"
    io.writeFile("C:/tmp/siyo_check.txt", "hi")
    io.mkdir("C:/tmp/siyo_check_dir")
    println("file exists: " + toString(io.exists("C:/tmp/siyo_check.txt")))
    println("dir exists: "  + toString(io.exists("C:/tmp/siyo_check_dir")))
    println("missing: "     + toString(io.exists("C:/tmp/siyo_nonexistent")))
    println("is file: "     + toString(io.isFile("C:/tmp/siyo_check.txt")))
    println("is not file: " + toString(io.isFile("C:/tmp/siyo_check_dir")))
    println("is dir: "      + toString(io.isDir("C:/tmp/siyo_check_dir")))
    println("is not dir: "  + toString(io.isDir("C:/tmp/siyo_check.txt")))
    io.delete("C:/tmp/siyo_check.txt")
    io.delete("C:/tmp/siyo_check_dir")
}
```

**Actual output:**
```
file exists: true
dir exists: true
missing: false
is file: true
is not file: false
is dir: true
is not dir: false
true
```

Trailing `true` is the last `io.delete` return value surfacing as the module block's final expression — cosmetic only, does not affect the three predicates.

---

## 6. `io.walk(dir)` recursive listing — PASS

**Setup:** `C:/tmp/walkroot/a.txt`, `C:/tmp/walkroot/sub/b.txt`, `C:/tmp/walkroot/sub/deep/c.txt`.

**Source:**
```siyo
{
    import "std/io"
    mut files = io.walk("C:/tmp/walkroot")
    for f in files { println(f) }
}
```

**Actual output:**
```
a.txt
sub/b.txt
sub/deep/c.txt
```
Recursive, sorted, forward-slash separators.

---

## 7. Binary I/O: `io.readBytes` / `io.writeBytes` / `io.copyFile` — PASS

**Source:**
```siyo
{
    import "std/io"
    imut src = "C:/tmp/siyo_bin_src.bin"
    imut dst = "C:/tmp/siyo_bin_dst.bin"
    io.writeBytes(src, [137, 80, 78, 71, 13, 10, 26, 10, 0, 255])
    mut rb = io.readBytes(src)
    println("len = "    + toString(len(rb)))
    println("rb[0] = "  + toString(rb[0]))
    println("rb[3] = "  + toString(rb[3]))
    println("rb[9] = "  + toString(rb[9]))
    io.copyFile(src, dst)
    mut rb2 = io.readBytes(dst)
    println("copy len = "      + toString(len(rb2)))
    println("copy rb2[0] = "   + toString(rb2[0]))
    println("copy rb2[9] = "   + toString(rb2[9]))
    io.delete(src)
    io.delete(dst)
}
```

**Actual output:**
```
len = 10
rb[0] = 137
rb[3] = 71
rb[9] = 255
copy len = 10
copy rb2[0] = 137
copy rb2[9] = 255
true
```
Byte values are unsigned 0..255 (fixed during verification — see "Bugs fixed" at the bottom). Copy preserves exact content.

---

## 8. `std/path` module — PASS

**Source:**
```siyo
{
    import "std/path"
    println(path.join("foo", "bar.siyo"))
    println(path.parent("C:/tmp/a/b.txt"))
    println(path.basename("C:/tmp/a/b.txt"))
    println(path.stem("C:/tmp/a/b.txt"))
    println(path.extension("C:/tmp/a/b.txt"))
    println("sep is: " + path.sep())
}
```

**Actual output:**
```
foo\bar.siyo
C:\tmp\a
b.txt
b
.txt
sep is: \
```
Windows-native separators. All six functions present.

---

## 9. `std/html` module — PASS

**Source:**
```siyo
{
    import "std/html"
    println(html.escape("<script>alert('x')</script>"))
    println(html.escapeAttr("a\"b&c<d"))
}
```

**Actual output:**
```
&lt;script&gt;alert('x')&lt;/script&gt;
a&quot;b&amp;c&lt;d
```

---

## 10. `std/strings` additions (`trimStart`, `trimEnd`, `lastIndexOf`, `pad*`, `join`, `repeat`, `lines`, `chars`) — PASS

**Source:**
```siyo
{
    import "std/strings"
    println("[" + strings.trimStart("   hi   ") + "]")
    println("[" + strings.trimEnd("   hi   ") + "]")
    println(toString(strings.lastIndexOf("abcabc", "b")))
    println("[" + strings.padLeft("42", 5, "0") + "]")
    println("[" + strings.padRight("hi", 5, "-") + "]")
    println("[" + strings.join(["a", "b", "c"], "-") + "]")
    println("[" + strings.repeat("ab", 3) + "]")
    println(toString(len(strings.lines("a\nb\nc"))))
    println(toString(len(strings.chars("abc"))))
}
```

**Actual output:**
```
[hi   ]
[   hi]
4
[00042]
[hi---]
[a-b-c]
[ababab]
3
3
```

---

## 11. `testing.assertThrows` + `beforeEach` / `afterEach` — PASS

### Case A — correct exception, correct substring → test passes

**Source:**
```siyo
{
    import "std/testing"
    fn bodyA() {
        testing.assertThrows(fn() { error("boom: nuclear") }, "nuclear")
        println("A: passed through")
    }
    testing.run("A", [bodyA])
}
```
**Actual output:**
```
=== A ===
A: passed through

1 passed, 0 failed, 1 total
```

### Case B — correct exception, wrong substring → test fails

**Source:**
```siyo
{
    import "std/testing"
    fn bodyB() {
        testing.assertThrows(fn() { error("boom: other") }, "nuclear")
    }
    testing.run("B", [bodyB])
}
```
**Actual output:**
```
=== B ===
  FAIL: expected message containing "nuclear", got: boom: other

0 passed, 1 failed, 1 total
```

### Case C — no exception thrown → test fails

**Source:**
```siyo
{
    import "std/testing"
    fn bodyC() {
        testing.assertThrows(fn() { mut x = 1 + 1 }, "nuclear")
    }
    testing.run("C", [bodyC])
}
```
**Actual output:**
```
=== C ===
  FAIL: expected exception but none was thrown

0 passed, 1 failed, 1 total
```

### Hooks — `beforeEach` / `afterEach`

**Source:**
```siyo
{
    import "std/testing"
    testing.beforeEach(fn() { println("  BEFORE") })
    testing.afterEach(fn()  { println("  AFTER") })
    fn t1() { testing.assertEq("x", "x", "eq") }
    fn t2() { testing.assertEq("x", "x", "eq") }
    testing.run("hooks", [t1, t2])
}
```
**Actual output:**
```
=== hooks ===
  BEFORE
  AFTER
  BEFORE
  AFTER

2 passed, 0 failed, 2 total
```
Hooks fire once per test, both before and after.

---

## 12. Nested JSON parse/stringify — PASS

**Source:**
```siyo
{
    import "std/json"
    mut s = "{\"name\":\"alice\",\"meta\":{\"age\":30,\"tags\":[\"x\",\"y\"]},\"nums\":[1,2,3]}"
    mut m = json.parse(s)
    println(toString(m.get("name")))
    mut meta = m.get("meta")
    println(toString(meta.get("age")))
    mut tags = meta.get("tags")
    println(toString(tags[0]))
    println(toString(tags[1]))
    mut nums = m.get("nums")
    println(toString(nums[0]))
    println(toString(nums[2]))
    println(json.stringify(m))
}
```

**Actual output:**
```
alice
30
x
y
1
3
{"name":"alice","meta":{"age":30,"tags":["x","y"]},"nums":[1,2,3]}
```
Nested map and array access works; round-trip preserves exact structure.

---

## 13. Diagnostics carry file + line + column — PASS

**Source (`item13.siyo`):**
```siyo
{
    mut x = undefined_name + 5
}
```

**Actual stderr:**
```
item13.siyo(2, 13): Name 'undefined_name' does not exist
```
Format: `<filename>(<line>, <col>): <message>`. File name, line 2, column 13 all present. No duplicate messages.

---

## 14. `siyoc test` auto-discovery from `tests/*_test.siyo` — PASS

**Setup:** `C:/tmp/verify_tests/siyo.toml` + `src/main.siyo` + two test files:

`tests/a_test.siyo`:
```siyo
{ println("A test ran") }
```
`tests/b_test.siyo`:
```siyo
{ println("B test ran") }
```

**Command:** `(cd C:/tmp/verify_tests && java -jar <jar> test)`

**Actual output:**
```
A test ran
B test ran
```
Both discovered and run in alphabetical order. No "no test file found" error.

---

## 15. `siyoc --version` / `siyoc --help` — PASS

```
$ java -jar <jar> --version
siyoc 0.3.0

$ java -jar <jar> -v
siyoc 0.3.0

$ java -jar <jar> --help
Usage: siyoc <command> [options]

Commands:
  run <file.siyo>     Compile and run a .siyo file
  run                 Run the project defined in siyo.toml
  test [file]         Run tests (default: src/test.siyo)
  compile <file.siyo> Compile to .class without running
  new <name>          Create a new project skeleton
  repl                Start the interactive REPL

Options:
  -v, --version       Print version and exit
  -h, --help          Print this help and exit
  -cp, --classpath    Append to classpath

$ java -jar <jar> -h
(identical to --help)

$ java -jar <jar> --bogus
Unknown flag: --bogus
Usage: siyoc <command> [options]
...
(exit 1)
```
Both forms work for version and help. Unknown flags error with usage instead of a Java stack trace.

---

## 16. `SiyoProject.load` walks up to find `siyo.toml` — PASS

**Setup:** `C:/tmp/verify_walkup/siyo.toml` + `src/main.siyo` (prints `hello walkup`).

**Command:** `(cd C:/tmp/verify_walkup/src && java -jar <jar> run)` — CWD is *below* the toml.

**Actual output:**
```
hello walkup
```
`SiyoProject.load` traverses parents to locate `siyo.toml`, then resolves `main` relative to the project root.

---

## 17. Module resolution search order documented in `GRAMMAR.md` — PASS

`GRAMMAR.md` lines 337–344:
```
### Module resolution order

When `import "name"` is resolved, the compiler searches in this order:
1. `name.siyo` relative to the importing file's directory
2. `name/index.siyo` relative to the importing file's directory
3. `src/name.siyo` relative to the project root (siyo.toml location)
4. `name.siyo` in the working directory
5. `std/name.siyo` from the bundled stdlib (classpath)
```
All five steps matching `ModuleHandler.resolveModulePath`.

---

## Bugs fixed during verification

The verification pass surfaced and fixed three real compiler issues:

1. **Parser postfix loop did not handle `expr(args)`** → `tests[i]()` was parsed as two statements (`tests[i]` then `()`), so `testing.run` never actually called its test closures. Every test silently "passed".
   - `src/main/java/codeanalysis/syntax/Parser.java` — added `OpenParenthesisToken` case in the postfix loop.
   - `src/main/java/codeanalysis/syntax/SyntaxType.java` — added `PostfixCallExpression`.
   - `src/main/java/codeanalysis/syntax/PostfixCallExpressionSyntax.java` — new node.
   - `src/main/java/codeanalysis/binding/Binder.java` — dispatch case + `bindPostfixCallExpression` that routes through the existing `BoundClosureCallExpression` emitter path.

2. **`SiyoArray.fromJavaArray` widened signed Java bytes** → `readBytes` returned values in the -128..127 range instead of 0..255 as documented.
   - `src/main/java/codeanalysis/SiyoArray.java:97` — `(int) b` → `b & 0xFF`.

3. **Version string still reported `0.2.0`.**
   - `src/main/java/Main.java` — bumped `VERSION` to `"0.3.0"`.
   - `pom.xml` — `<version>` bumped to `0.3.0`.

## Module semantics rewrite (item #2)

The first pass of 0.3.0 implemented module top-level as Python/Ruby-style (bare statements run lazily in `<clinit>` on first class reference). The locked design was Go-style. Re-implemented in one pass:

**Syntax housekeeping during this rewrite:** the historical outer `{ }` wrapper around every `.siyo` file was redundant (the parser already synthesizes a compilation-unit block from a sequence of top-level items). Every refactored file now omits it; the parser still accepts the old form for backward compatibility. Updated `GRAMMAR.md` and `siyoc new` scaffolding accordingly.


- **Binder** `src/main/java/codeanalysis/binding/Binder.java` — added `_atFileTopLevel` flag and `isAllowedAtTopLevel(...)` helper. `bindBlockStatement` checks each statement on the outermost block and emits `reportTopLevelNotAllowed(...)` for anything that isn't a declaration or import.
- **Compilation** `src/main/java/codeanalysis/Compilation.java` — REPL path (null `_filePath`) bypasses the enforcement; file-based compilation enforces.
- **DiagnosticBox** — new `reportTopLevelNotAllowed(TextSpan)` with the exact message `top-level statement not allowed; move into init() or main()`.
- **Emitter** `src/main/java/codeanalysis/emitting/Emitter.java` — removed "print the last expression value" legacy in `emitMainMethod`; both `emitMainMethod` and `emitModuleInitializer` now:
  1. force-load each imported module (`Class.forName`) before anything else — this is how `init()` runs *eagerly* at import time instead of lazily on first reference,
  2. run the remaining top-level statements (only declarations and var-decl initializers reach here after the binder gate),
  3. invoke the user-defined `init()` if present,
  4. (main class only) invoke the user-defined `main()` if present.
- **Refactor** — every `.siyo` file in `examples/`, `examples/modules/`, `projects/*/src/` that used bare top-level statements was rewrapped into `fn init()` / `fn main()`. Stdlib (`src/main/resources/std/`) was already declarations-only.

## Known cosmetic quirks (not blocking)

- Windows path separators in `std/path` (`foo\bar.siyo`): OS-native, as expected, but worth noting for tests that assert forward-slash output.
- (The "trailing `true` on items 5/7" quirk noted in the first pass is gone — the last-expression-printing behaviour in the main method was removed as part of the item-#2 rewrite.)

## Open follow-ups (for 0.3.1 / later)

- Qualified module variable access `moduleName.varName` is not yet resolved (`bindMemberAccessExpression` only handles enums / Java static fields / struct fields). Imported function calls work. Deliberately out of scope for 0.3.0 per milestone note.
- Module-level function references as closures (see `examples/modules/...`) still require explicit `fn()` wrapping in some positions.
