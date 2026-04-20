# Siyo 0.3.0 Audit — State of the 0.2.0 Codebase vs. Stardust Post-Mortem

Audit date: 2026-04-20.
All paths are relative to repo root `C:\Users\uruns\IdeaProjects\SiyoCompiler`.
Dynamic tests were run against `target/siyo-compiler-0.2.0-SNAPSHOT-shaded.jar`.

---

## 1. Compiler bugs

### [BUG-001] `&&` / `||` short-circuit in bytecode backend

**Status:** MISSING

**Evidence:**
- File: `src/main/java/codeanalysis/emitting/Emitter.java` lines 681-711
  ```java
  emitExpression(node.getLeft());
  ...
  emitExpression(node.getRight());
  ...
  switch (node.getOperator().getType()) {
      ...
      case LogicalAnd -> _mv.visitInsn(IAND);
      case LogicalOr  -> _mv.visitInsn(IOR);
  ```
  Both sides are unconditionally emitted before the `IAND`/`IOR` instruction. There is no conditional branch, no label for skipping the right operand.
- Dynamic repro (`mut x = false && (1/0 > 0)`):
  ```
  java.lang.ArithmeticException: / by zero
      at Bug001_and.main(bug001_and.siyo:1)
  ```
- Same crash for `mut x = true || (1/0 > 0)`.

**Confirmed absent.** `&&` / `||` are emitted as bitwise `IAND` / `IOR` over fully-evaluated booleans; no short-circuit branch is generated.

---

### [BUG-002] Module top-level execution on import

**Status:** PARTIAL (silently drops everything except `BoundVariableDeclaration`)

**Evidence:**
- File: `src/main/java/codeanalysis/emitting/Emitter.java` lines 200-231
  ```java
  private void emitModuleInitializer(ClassWriter cw, String className) {
      if (_statement.getStatements().isEmpty() && !_needsScanner) return;
      boolean hasVarDecls = false;
      for (BoundStatement stmt : _statement.getStatements()) {
          if (stmt instanceof BoundVariableDeclaration) { hasVarDecls = true; break; }
      }
      if (!hasVarDecls) return;
      _mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      ...
      for (BoundStatement stmt : _statement.getStatements()) {
          if (stmt instanceof BoundVariableDeclaration) { emitStatement(stmt); }
      }
      ...
  }
  ```
  Only `BoundVariableDeclaration` statements are emitted into the module's `<clinit>`. `if`, `println`, bare function calls, loops, etc., are silently discarded when present at the top of a module file.
- Dynamic repro (project `mymod.siyo` with `println(...)` at top-level, imported from `main.siyo`): only the function-defs survive; the `println` never runs.

**What works:** `mut x = f()` at module top-level does execute on import (value is stored in the module class's static field).
**What is missing:** bare statements — `println(...)`, `if ...`, `fn` calls, loops — are all silently dropped.
**Reference:** Post-mortem BUG-002 is exact: "only `BoundVariableDeclaration` makes it into `<clinit>`". Confirmed verbatim.

---

### [BUG-003] `$name` interpolation in regular `"..."` strings

**Status:** DONE

**Evidence:**
- File: `src/main/java/codeanalysis/syntax/Lexer.java` lines 480-496 (`readStringContent` — regular strings)
  ```java
  case '$' -> {
      char afterDollar = peek(1);
      if (afterDollar == '{') { ... }          // ${expr}
      else if (Character.isLetter(afterDollar) || afterDollar == '_') {
          next();
          _pendingSimpleInterpRead = true;
          _pendingSimpleInterpIsTripleQuote = false;
          _type = isStart ? InterpolatedStringStartToken : InterpolatedStringMidToken;
          ...
      }
  }
  ```
  Regular `"..."` strings DO handle `$name`. The same branch exists in `readTripleQuoteStringContent` (lines 404-421).
- Dynamic test:
  ```siyo
  mut name = "world"
  println("hello $name")
  ```
  Output: `hello world`

**Post-mortem disagrees — verified independently, post-mortem is stale.** `$name` works in regular strings on the current shaded jar. Likely fixed between whatever Siyo revision Stardust tested against and HEAD.

---

### [BUG-004] `${expr}` interpolation in regular strings

**Status:** DONE

**Evidence:**
- File: `src/main/java/codeanalysis/syntax/Lexer.java` lines 480-496 — same branch covers `${expr}`:
  ```java
  if (afterDollar == '{') {
      next(); next();                           // consume $ and {
      _interpStack.push(1);
      _type = isStart ? InterpolatedStringStartToken : InterpolatedStringMidToken;
      ...
  }
  ```
- Dynamic test:
  ```siyo
  mut a = 3
  mut b = 4
  println("sum is ${a + b}")
  ```
  Output: `sum is 7`

**Post-mortem disagrees — verified independently.** `${expr}` works in regular strings. Either fixed in 0.2.0 after Stardust tested, or the post-mortem tested a cached/installed older jar.

---

### [BUG-005] For-loop init variable scope

**Status:** PARTIAL (confirmed broken for the C-style `for mut i = ...` form; `for x in ...` is fine)

**Evidence:**
- File: `src/main/java/codeanalysis/binding/Binder.java` lines 318-328
  ```java
  private BoundStatement bindForStatement(ForStatementSyntax syntax) {
      BoundStatement initializer = bindStatement(syntax.getInitializer());
      ...
  }
  ```
  No `_scope = new BoundScope(_scope)` before binding the initializer. The `mut i = 0` declaration lands in the enclosing function's scope.
- Compare with `bindChannelForIn` at line 493 and `for-in` at line 503 which both explicitly push a scope.
- Dynamic repro — two sequential C-style loops in the same block:
  ```
  Variable 'i' is already declared
  Variable 'i' is already declared
  ```

**Confirmed broken for the three-expression form only.** `for x in range(...)` works because `bindChannelForIn`/array-for-in variants push a new scope around the item variable.

---

## 2. Missing stdlib

### [STD-001] `io.isDir` / `io.isFile` / `io.exists`

**Status:** PARTIAL (only `fileExists` present, and it's misleadingly named)

**Evidence:**
- File: `src/main/resources/std/io.siyo` lines 48-51
  ```siyo
  fn fileExists(path: string) -> bool {
      mut f = File.new(path)
      return f.exists()
  }
  ```
- `grep -n 'isDir\|isFile\|isDirectory' src/main/resources/std/io.siyo` → no matches.
- `java.io.File.isDirectory()` is reachable through `import java "java.io.File"` (post-mortem workaround used that). No Siyo-level wrapper.

**What works:** `fileExists(path)` — but returns `true` for directories too, per `java.io.File.exists()`. Naming is misleading.
**What is missing:** `io.isDir`, `io.isFile`, `io.exists` (distinct from `fileExists`).

---

### [STD-002] `io.walk(dir) -> string[]` recursive walk

**Status:** MISSING

**Evidence:**
- `grep -n 'walk' src/main/resources/std/io.siyo` → no matches.
- Only `listDir(path) -> string[]` exists (line 53), which is non-recursive.

Confirmed absent.

---

### [STD-003] `io.copyFile`, `io.readBytes`, `io.writeBytes`

**Status:** MISSING

**Evidence:**
- `grep -n 'copyFile\|readBytes\|writeBytes' src/main/resources/std/io.siyo` → no matches.
- `io.siyo` only exposes line-oriented `readFile` / `writeFile` / `appendFile` / `readLines`, all text-mode via `FileReader` / `FileWriter`.

Confirmed absent. Post-mortem's concern about silent binary corruption on `writeFile(to, readFile(from))` is valid — `BufferedReader.readLine()` eats line terminators.

---

### [STD-004] `std/path` module

**Status:** MISSING

**Evidence:**
- `ls src/main/resources/std/` → `io.siyo, json.siyo, math.siyo, net.siyo, os.siyo, strings.siyo, testing.siyo`. No `path.siyo`.
- `grep -rn 'path.join\|pathJoin\|basename\|dirname\|stem' src/main/resources/std/` → no matches.

Confirmed absent.

---

### [STD-005] `std/html.escape`

**Status:** MISSING

**Evidence:**
- No `std/html.siyo` in `src/main/resources/std/`.
- `grep -rn 'escapeHtml\|htmlEscape\|html\.escape' src/main/resources/std/ src/main/java/` → no matches (except `json.siyo`'s `_escapeStr` which is JSON-string escape, not HTML).

Confirmed absent.

---

## 3. Syntax / language ergonomics

### [LANG-002] Typed empty array literal `mut xs: string[] = []`

**Status:** MISSING (both the type annotation syntax AND element-type inference for `[]`)

**Evidence:**
- File: `src/main/java/codeanalysis/syntax/VariableDeclarationSyntax.java` — class fields are `_keyword, _identifier, _equalsToken, _initializer`. No `_typeClause`. Parser `parseVariableDeclaration` at `Parser.java:220-231` matches only `Mut|Imut Ident = expr`.
- File: `src/main/java/codeanalysis/binding/Binder.java` lines 1151-1169 (`bindArrayLiteralExpression`)
  ```java
  if (elementType == null) { elementType = Object.class; }
  ```
  An empty `[]` is bound as `Object[]`, with no ability for the call site to pass an expected element type.
- Dynamic repro:
  ```siyo
  mut xs: string[] = []
  ```
  ```
  ERROR: Unexpected token: <ColonToken>, expected <EqualsToken>
  ...
  Name 'string' does not exist
  ```
- GRAMMAR.md line 75 claims "`[]` (empty literal infers element type from context)" — this is not true: no context-flow exists, and there is no syntax to supply one.

Confirmed absent on both axes: no `: Type` clause and no context-flow inference.

---

### [LANG-003] Closure mutable captures

**Status:** MISSING (captures are pass-by-value; writes don't propagate)

**Evidence:**
- File: `src/main/java/codeanalysis/emitting/Emitter.java` lines 1365-1395 (`emitLambdaMethod`)
  ```java
  // Method signature: lambda$N(captured0, captured1, ..., param0, param1, ...) -> Object
  for (VariableSymbol captured : lambda.getCapturedVariables()) {
      desc.append("Ljava/lang/Object;");
  }
  ...
  for (VariableSymbol captured : capturedSet) { _locals.put(captured, _nextLocal++); }
  ```
  Captured variables are passed in as `Object` locals. No boxing cell (e.g. `Object[1]`) is used; assignments inside the lambda body overwrite the local copy only.
- Dynamic repro:
  ```siyo
  mut x = 0
  mut inc = fn () { x = x + 1 }
  inc(); inc()
  println("x after = " + toString(x))
  ```
  Output: `x after = 0`.
- For `spawn`, the compiler actively errors via `reportMutableCaptureInSpawn` (see `Binder.java:1357`), but for lambdas the assignment silently succeeds on the local copy.

Confirmed missing.

---

## 4. Tooling

### [TOOL-001] `std/*.siyo` resources in release jar

**Status:** DONE

**Evidence:**
```
$ jar tf target/siyo-compiler-0.2.0-SNAPSHOT-shaded.jar | grep '\.siyo'
std/io.siyo
std/json.siyo
std/math.siyo
std/net.siyo
std/os.siyo
std/strings.siyo
std/testing.siyo

$ jar tf target/siyo-compiler-0.2.0-SNAPSHOT.jar | grep '\.siyo'
std/io.siyo
std/json.siyo
std/math.siyo
std/net.siyo
std/os.siyo
std/strings.siyo
std/testing.siyo
```
All seven std modules are packaged in both the shaded and regular 0.2.0 jars.

**Caveat for 0.3.0:** The post-mortem was filed against the *installed* `~/.siyo/lib/siyoc.jar`, which the author reported as missing these resources. This audit is of the freshly-built repo jar, not the installer output. If the issue was ever real, it was in the installer pipeline (see `install.ps1` / `install.sh`) — not in the Maven build. Worth re-verifying the install flow before closing out, but the Maven build is correct.

---

### [TOOL-002] `SiyoProject.load` walks up to find `siyo.toml`

**Status:** MISSING

**Evidence:**
- File: `src/main/java/codeanalysis/project/SiyoProject.java` lines 35-46
  ```java
  public static SiyoProject load(Path directory) {
      Path tomlPath = directory.resolve("siyo.toml");
      if (!Files.exists(tomlPath)) return null;
      ...
  }
  ```
  Only checks the given directory. No loop / no `getParent()` traversal.
- Callers: `Main.java:98` and `Main.java:128` both pass `Paths.get(System.getProperty("user.dir"))` — raw CWD, not a walk.
- Dynamic repro from `proj/src/subdir/`: `Error: no siyo.toml found in current directory.`

Confirmed absent.

---

### [TOOL-003] `siyoc test` auto-discovery

**Status:** MISSING (only `src/test.siyo` is the default)

**Evidence:**
- File: `src/main/java/Main.java` lines 96-124
  ```java
  if (testFile != null) { path = testFile; }
  else {
      Path defaultTest = cwd.resolve("src").resolve("test.siyo");
      if (!Files.exists(defaultTest)) {
          System.err.println("Error: no test file found. Use: siyoc test <file> or create src/test.siyo");
          System.exit(1);
      }
      path = defaultTest.toString();
  }
  ```
  No glob over `tests/*_test.siyo`, no aggregation across files.
- Dynamic repro: project with `tests/foo_test.siyo` but no `src/test.siyo` — `siyoc test` prints the "no test file found" error.

Confirmed absent.

---

### [TOOL-004] `siyoc --version`, `siyoc --help`

**Status:** MISSING

**Evidence:**
- File: `src/main/java/Main.java` lines 24-94. The dispatch chain is: `new`, `run <file>`, `run`, `test`, `interpret`, `compile`, `-c`/`exec`, else `runFile(cargs[0])` (line 89-92). No `--version` or `--help` branch.
- Dynamic repro:
  ```
  $ java -jar ... --version
  java.nio.file.NoSuchFileException: .../--version
  ...
  ```
  Unknown args fall through to `runFile`, which tries to read them as source files. No REPL drop (good-ish), but also no `--version`/`--help` output.

Confirmed absent. Post-mortem note about "drops to REPL" is inaccurate on current HEAD — unknown args now crash trying to read the arg as a file path, which is arguably worse (non-zero exit with a Java stack trace instead of a usage message).

---

### [TOOL-005] Diagnostics carry `TextSpan` (file + line + column)

**Status:** PARTIAL (TextSpan on every `Diagnostic`, but output format drops it on the bytecode path, and file name is nowhere on the `Diagnostic`)

**Evidence:**
- File: `src/main/java/codeanalysis/Diagnostic.java` — `Diagnostic(TextSpan span, String message)`. No file field.
- File: `src/main/java/codeanalysis/DiagnosticBox.java` — every `report*` method takes `TextSpan span`. Good.
- File: `src/main/java/Main.java`
  - `runFile` (interpreter path) lines 279-288 — extracts line/column and prints `(line, col): message`.
  - `compileAndRun` (bytecode path) lines 166-172:
    ```java
    while (diagnostics.hasNext()) {
        System.err.println(diagnostics.next());
    }
    ```
    Just `println(diagnostic)` — and `Diagnostic.toString()` (line 53-55) returns `getMessage()` only. Line/column NOT included.
- Dynamic repro (compile path, `mut x = undefined_var + 5`):
  ```
  Name 'undefined_var' does not exist
  Name 'undefined_var' does not exist
  ```
  No file, no line, no column. Also duplicated (probably tree + global scope both reported).

**What works:** Every call site has a `TextSpan`. Interpreter path prints `(line, col)`.
**What is missing:** File name is never in the diagnostic. Bytecode path (which is what `siyoc run` uses by default) strips the line/column entirely. Diagnostics are sometimes duplicated between `tree.diagnostics()` and `compilation.getGlobalScope().getDiagnostics()`.

---

## 5. New additions (B-revised scope)

### [NEW-JSON] `std/json` module with parse / stringify

**Status:** DONE (basic — nested objects/arrays are stored as raw strings, not recursively decoded)

**Evidence:**
- File: `src/main/resources/std/json.siyo` — `fn parse(s: string) -> map` (line 111) and `fn stringify(m: map) -> string` (line 169) both exported.
- Dynamic test:
  ```siyo
  import "std/json"
  mut m = json.parse("{\"name\":\"alice\",\"age\":30}")
  println(toString(m.get("name")))      // alice
  println(toString(m.get("age")))       // 30
  println(json.stringify(m))            // {"name":"alice","age":30}
  ```
- Known limitation (json.siyo:82-105): nested `{...}` and `[...]` are stored as their raw substring with tags `O:` / `A:` and never re-parsed into a map / array. Only flat scalar-valued objects round-trip cleanly. Worth flagging for 0.3.0 if deeper JSON is a goal.

---

### [NEW-STRINGS] Individual string function coverage

Checked against `src/main/java/codeanalysis/BuiltinFunctions.java` (all as built-in registrations) and `src/main/resources/std/strings.siyo`.

| Function | Status | Evidence |
|---|---|---|
| `split` | DONE | Builtin, `BuiltinFunctions.java:222-226` |
| `trim` | DONE | Builtin, `BuiltinFunctions.java:141-145` |
| `trimStart` | MISSING | grep returns nothing |
| `trimEnd` | MISSING | grep returns nothing |
| `replace` | DONE | Builtin, `BuiltinFunctions.java:135-139` (note: a single replace — behavior is Java `String.replace` which replaces all, so `replaceAll` is effectively subsumed; see next row) |
| `replaceAll` | MISSING (but subsumed) | Java's `String.replace(CharSequence,CharSequence)` replaces all occurrences; Siyo's `replace` wraps that. A separate `replaceAll` named builtin is absent; document the fact that `replace` is already global. |
| `indexOf` | DONE | Builtin, `BuiltinFunctions.java:117-121` |
| `lastIndexOf` | MISSING | grep returns nothing in Siyo surface; `String.lastIndexOf` is only used inside Java internals |
| `toLower` | DONE | Builtin, `BuiltinFunctions.java:153-157` |
| `toUpper` | DONE | Builtin, `BuiltinFunctions.java:147-151` |
| `padStart` | MISSING (but `padLeft` in std/strings.siyo:31 fills the same niche) | Naming differs from the spec. If the 0.3.0 target names are `padStart`/`padEnd`, these need to be aliased or renamed. |
| `padEnd` | MISSING (but `padRight` in std/strings.siyo:39) | Same as above. |
| `startsWith` | DONE | Builtin, `BuiltinFunctions.java:123-127` |
| `endsWith` | DONE | Builtin, `BuiltinFunctions.java:129-133` |
| `contains` | DONE | Builtin, `BuiltinFunctions.java:87-91` |
| `repeat` | DONE | `src/main/resources/std/strings.siyo:23-29` |

**Summary for NEW-STRINGS:** 10 done, 4 missing (`trimStart`, `trimEnd`, `lastIndexOf`, `replaceAll` — though `replace` already replaces all), 2 misnamed (`padStart`/`padEnd` vs `padLeft`/`padRight`).

---

### [NEW-TESTING] `std/testing` module with `assertEq` / `assertThrows` / `before` / `after`

**Status:** PARTIAL

**Evidence:**
- File: `src/main/resources/std/testing.siyo`
  - Line 2-7: `fn assert(condition: bool, msg: string)`
  - Line 9-15: `fn assertEqual(actual, expected, msg)` — **note: name is `assertEqual`, not `assertEq`** as the spec asked for
  - Line 17-28: `fn test(name: string, body: fn() -> bool)`
  - Line 30-45: `fn run(name: string, tests: fn()[])`
- Missing:
  - `assertEq` (only `assertEqual` exists — naming mismatch)
  - `assertThrows`
  - `before` / `after` lifecycle hooks

The skeleton exists; the richer API surface does not.

---

## 6. Documentation gaps (GRAMMAR.md)

### [DOC-001] GRAMMAR.md claims `&&`, `||` are short-circuit

**Status:** DONE (claim is in the doc — but the claim is false per BUG-001)

**Evidence:**
- File: `GRAMMAR.md` line 46
  ```
  | `&&` `\|\|` | Logical AND / OR (short-circuit) |
  ```

The doc makes the claim. Matches the post-mortem. The fix is in the compiler, not the doc.

---

### [DOC-002] GRAMMAR.md coverage of module top-level semantics

**Status:** MISSING

**Evidence:**
- `grep -in 'module\|import\|clinit\|top.level\|run.*import' GRAMMAR.md` → only one hit at line 22 (`import` in the keyword table) and the grammar rule for `import_statement` at line 142-144. No prose section explaining what runs on import vs. direct run.

Confirmed absent.

---

### [DOC-INTERP] GRAMMAR.md claim about string interpolation

**Status:** DONE (claim is in the doc AND matches behavior)

**Evidence:**
- File: `GRAMMAR.md` line 72
  ```
  **String interpolation**: `"hello $name"` (bare identifier) and `"sum is ${a + b}"` (arbitrary expression). `\$` escapes a literal `$`. Works inside both regular and triple-quoted strings.
  ```
- Behavior matches (see BUG-003 / BUG-004 dynamic tests — both work).

---

## 7. Extra items present in the post-mortem but not in the audit task list

The 2026-03 Stardust post-mortem included five items not explicitly requested above. Auditing each here since the user asked me to.

### [LANG-001] Tuple / multiple return values

**Status:** MISSING

**Evidence:**
- `grep -rn 'tuple\|Tuple' GRAMMAR.md README.md src/main/java/` → no matches.
- No `TupleLiteral` or `TupleTypeClause` syntax classes.
- No parser rule accepting `(T, U)` as a type or `(a, b)` as a value/pattern.

Confirmed absent.

---

### [LANG-004] `error()` throws bare string; no typed exceptions

**Status:** MISSING (typed errors); the built-in `error` throws a plain `java.lang.RuntimeException(message)`

**Evidence:**
- File: `src/main/java/codeanalysis/emitting/Emitter.java` lines 1737-1743
  ```java
  if (function == BuiltinFunctions.ERROR) {
      _mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      _mv.visitInsn(DUP);
      emitExpression(node.getArguments().get(0));
      _mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException",
                          "<init>", "(Ljava/lang/String;)V", false);
      _mv.visitInsn(ATHROW);
      return;
  }
  ```
- File: `BuiltinFunctions.java:99-103` — signature `error(message: string)`, no kind/code parameter.
- `catch e { ... }` binds `e` to the exception's message (string). No way to discriminate by kind/type.

Confirmed absent.

---

### [DOC-003] `{` / `}` module-wrapper requirement not explained

**Status:** MISSING (not explained in GRAMMAR.md, and the convention is inconsistent across examples)

**Evidence:**
- `grep -n 'wrapper\|module.*{' GRAMMAR.md` → no matches.
- Examples use the wrapper inconsistently:
  - `projects/sitegen/src/main.siyo` — wrapped in `{ }`
  - `examples/modules/utils.siyo` — wrapped
  - `examples/test_framework.siyo` — not wrapped (per post-mortem report; worth re-checking)
- The rule that modules need a top-level `{ }` block is not stated in any docs file I could find.

Confirmed absent from documentation.

---

### [DOC-004] `siyoc test` default-file convention (`src/test.siyo`) not documented

**Status:** MISSING from `README.md` / `GRAMMAR.md`; the only places the convention exists are `Main.java:110-115` (the source) and the one-line error message on missing file.

**Evidence:**
- `grep -n 'test\.siyo\|src/test\|siyoc test' README.md GRAMMAR.md` → no substantive matches. (`README.md:275` mentions `src/test/java/` for the compiler's own Java tests, not user tests.)

Confirmed absent.

---

### [DOC-005] Module-resolution search order not documented

**Status:** MISSING from the docs (the logic exists in `ModuleHandler.resolveModulePath`)

**Evidence:**
- File: `src/main/java/codeanalysis/binding/ModuleHandler.java` lines 189-254 — the resolver checks ~5 locations (file's own directory, file's subdir `index.siyo`, project `src/`, CWD, classpath `std/`), with paginated numbered comments in the source.
- `grep -n 'search order\|resolveModule\|module path' GRAMMAR.md README.md` → no matches.

The logic exists; only user-facing docs are missing.

---

## Summary table

| ID | Status | Notes |
|----|--------|-------|
| BUG-001 | MISSING | `LogicalAnd`→`IAND`, `LogicalOr`→`IOR` in `Emitter.java:710-711`; dynamic crash confirmed on both forms. |
| BUG-002 | PARTIAL | `emitModuleInitializer` keeps only `BoundVariableDeclaration`; all other top-level stmts silently dropped. |
| BUG-003 | DONE | `readStringContent` handles `$name` in regular strings. Dynamic test: `hello world`. Post-mortem stale. |
| BUG-004 | DONE | `${expr}` handled the same way. Dynamic test: `sum is 7`. Post-mortem stale. |
| BUG-005 | PARTIAL | `bindForStatement` omits `new BoundScope`; C-style for leaks `i` into enclosing scope. `for in` is fine. |
| STD-001 | PARTIAL | `fileExists` exists but misleadingly named; `isDir`/`isFile`/`exists` all absent. |
| STD-002 | MISSING | No `io.walk` or equivalent. |
| STD-003 | MISSING | No `io.copyFile`/`readBytes`/`writeBytes`; only text-mode IO. |
| STD-004 | MISSING | No `std/path` module. |
| STD-005 | MISSING | No `std/html` module. |
| LANG-002 | MISSING | Parser has no `: Type` clause on var decls; empty `[]` binds to `Object[]`. |
| LANG-003 | MISSING | Lambda captures are pass-by-value; writes stay local to the lambda. |
| TOOL-001 | DONE | Both 0.2.0 jars contain all 7 `std/*.siyo`. Post-mortem issue was in the installer, not Maven build. |
| TOOL-002 | MISSING | `SiyoProject.load(cwd)` doesn't walk up to parents. |
| TOOL-003 | MISSING | `siyoc test` only defaults to `src/test.siyo`; no glob over `tests/*_test.siyo`. |
| TOOL-004 | MISSING | No `--version`/`--help` dispatch; unknown args fall to `runFile` and throw. |
| TOOL-005 | PARTIAL | TextSpan on every Diagnostic; no file field; bytecode-path output strips line/col; errors sometimes duplicated. |
| NEW-JSON | DONE | `std/json.siyo` has `parse`/`stringify`. Flat scalars only; nested `{}`/`[]` stored as raw strings. |
| NEW-STRINGS-split | DONE | Builtin. |
| NEW-STRINGS-trim | DONE | Builtin. |
| NEW-STRINGS-trimStart | MISSING | — |
| NEW-STRINGS-trimEnd | MISSING | — |
| NEW-STRINGS-replace | DONE | Builtin; wraps Java `String.replace`, so behavior is already global. |
| NEW-STRINGS-replaceAll | MISSING (subsumed) | No separate builtin; `replace` already replaces all. Decide whether to add an alias or document. |
| NEW-STRINGS-indexOf | DONE | Builtin. |
| NEW-STRINGS-lastIndexOf | MISSING | — |
| NEW-STRINGS-toLower | DONE | Builtin. |
| NEW-STRINGS-toUpper | DONE | Builtin. |
| NEW-STRINGS-padStart | MISSING (named `padLeft` in `std/strings.siyo`) | Decide: alias, rename, or leave. |
| NEW-STRINGS-padEnd | MISSING (named `padRight` in `std/strings.siyo`) | Same as above. |
| NEW-STRINGS-startsWith | DONE | Builtin. |
| NEW-STRINGS-endsWith | DONE | Builtin. |
| NEW-STRINGS-contains | DONE | Builtin. |
| NEW-STRINGS-repeat | DONE | `std/strings.siyo:23`. |
| NEW-TESTING | PARTIAL | Has `assert`/`assertEqual`/`test`/`run`; missing `assertEq` alias, `assertThrows`, `before`/`after` hooks. |
| DOC-001 | DONE (doc claim exists; impl doesn't honor it) | `GRAMMAR.md:46` says "short-circuit"; compiler doesn't. |
| DOC-002 | MISSING | GRAMMAR.md has no prose on module top-level / `<clinit>` semantics. |
| DOC-INTERP | DONE | `GRAMMAR.md:72` matches implementation. |
| LANG-001 | MISSING | No tuple syntax anywhere in parser/runtime. |
| LANG-004 | MISSING | `error(msg)` throws plain `RuntimeException(msg)`; no typed errors. |
| DOC-003 | MISSING | `{}` module wrapper rule not stated in docs; examples inconsistent. |
| DOC-004 | MISSING | `src/test.siyo` default convention only exists in `Main.java` source. |
| DOC-005 | MISSING | `ModuleHandler` 5-step search order not documented. |

**Net picture for 0.3.0 planning:** 5 items already DONE (BUG-003, BUG-004, TOOL-001, NEW-JSON, DOC-INTERP + many NEW-STRINGS sub-items); the post-mortem's two blockers (BUG-001 short-circuit; TOOL-001 missing std in jar) need only BUG-001 fixed in the compiler — TOOL-001 is actually fine in the Maven build and needs a recheck of the installer. Most remaining work is additive (missing stdlib, docs) rather than compiler-internal fixes.
