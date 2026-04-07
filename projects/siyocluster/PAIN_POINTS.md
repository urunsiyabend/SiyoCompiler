# SiyoCluster Pain Points

Brutally honest documentation of every issue, workaround, and missing feature
encountered building a distributed key-value store with replication in Siyo 0.2.0.

## Compiler Bugs Fixed (4 bugs)

### 1. `spawn { }` blocks rejected outside `scope { }`
**Severity:** Blocker
**Symptom:** `spawn must be inside a scope block` — but server patterns need fire-and-forget spawns.
**Root cause:** Binder's `_insideScope` check rejected all block spawns outside `scope { }`.
**Fix:** Removed the restriction. Bare spawns now fire virtual threads without joining.
**Impact:** Broke siyodb, broker, and any concurrent server pattern.
**Files:** `Binder.java` (bindSpawnExpression), `Evaluator.java` (evaluateSpawnExpression)

### 2. Match arm blocks only evaluated last expression
**Severity:** Blocker
**Symptom:** `Name 'value' does not exist` inside match arm blocks with multiple statements.
**Root cause:** `bindBlockExpressionBody()` only bound the last expression, ignoring all preceding statements (variable declarations, if checks, loops).
**Fix:** Added `preStatements` to `BoundMatchArm` — binds all preceding statements in a new scope, lowers them before emitting.
**Impact:** Any match arm with more than one statement was silently broken.
**Files:** `Binder.java`, `BoundMatchExpression.java`, `Emitter.java`, `Evaluator.java`

### 3. Actor handles rejected by mutable capture check
**Severity:** Blocker
**Symptom:** `Mutable variable 'store' cannot be captured by spawn block` — actors are thread-safe by design.
**Root cause:** Capture check rejected all `mut` variables. Actors and Object-typed vars (sockets, etc.) should be exempt since actors are thread-safe and Objects are deep-copied at runtime.
**Fix:** Exempted actor handles and Object-typed variables from mutable capture restriction.
**Files:** `Binder.java` (collectCapturedVarsFromBody)

### 4. Object-typed values couldn't be indexed
**Severity:** Blocker
**Symptom:** `Type <class java.lang.Object> cannot be indexed` — actor methods return Object, can't use result as array.
**Root cause:** Index expression binding only accepted SiyoArray and String. Object (from actor method returns) was rejected.
**Fix:** Allow indexing Object at compile time with a runtime CHECKCAST to List.
**Files:** `Binder.java` (bindIndexExpression), `Emitter.java` (emitIndexExpression)

## Language Pain Points

### 5. Actor methods on `object`-typed parameters lose type info
**Severity:** High
**What happened:** Functions take `nodeState: object` because there's no way to type-annotate actor handles. All method return values become Object. Arithmetic on int returns requires `parseInt(toString(actor.method()))`.
**Workaround:** Every actor method return used for arithmetic needs explicit `parseInt(toString(...))`.
**What I wanted:** `fn foo(ns: NodeState)` parameter typing, or automatic unboxing of actor returns.
**Example:**
```siyo
// Have to write this:
mut myPort = parseInt(toString(nodeState.getPort()))
// Instead of this:
mut myPort = nodeState.getPort()
```

### 6. if/else is a statement, not an expression
**Severity:** High
**What happened:** Match arm blocks can't end with `if/else` — the last item must be an expression. Forces temp variable + mutation pattern.
**Workaround:** Declare `mut result = ""`, then `if/else` to assign, then `result` as final expression.
**What I wanted:** `if/else` as expression like Rust: `mut x = if cond { "a" } else { "b" }`

### 7. No sleep() builtin
**Severity:** Medium
**What happened:** Needed `Thread.sleep()` for heartbeat intervals. Required `import java "java.lang.Thread"` and `Thread.sleep(toLong(2000))`.
**Workaround:** Java interop import works fine.
**What I wanted:** `sleep(2000)` as a builtin or in `std/time`.

### 8. Returning arrays from actors requires serialization
**Severity:** High
**What happened:** Actor methods that return arrays (peers, keys, dead peers) can't be indexed outside due to Object typing. Even with the indexing fix, ASM frame computation fails on complex functions that use Object-typed arrays.
**Workaround:** Return comma-separated strings from actor methods, `split()` at call site.
**What I wanted:** Actor return types tracked and preserved through calls.
**Example:**
```siyo
// Have to write this:
fn getPeers(self) -> string {
    // serialize to "a,b,c"
}
// Then at call site:
mut peers = split(nodeState.getPeers(), ",")

// Instead of:
fn getPeers(self) -> string[] { ... }
mut peers = nodeState.getPeers()
```

### 9. ASM VerifyError on complex functions with mixed types
**Severity:** High
**What happened:** Functions with loops containing nested ifs where variables are Object (from actor calls) but used as int caused `VerifyError: Bad local variable type`. ASM frame computation fails when local variable types are inconsistent across branches.
**Workaround:** Split complex functions into smaller ones. Use explicit type conversions everywhere.
**Root cause:** The emitter uses COMPUTE_ALL_FRAMES which requires consistent types at merge points. Actor returns are Object, but code uses them as int — the implicit unboxing doesn't happen at all merge points.

### 10. No toLong() for integer-to-long conversion
**Severity:** Low
**What happened:** `System.currentTimeMillis()` returns long, but heartbeat timeout comparison needed int-to-long conversion. Had to use `parseLong(toString(timeoutMs))`.
**What I wanted:** `toLong(timeoutMs)` or implicit int-to-long promotion.

### 11. Bool returns from actors can't be used in conditions
**Severity:** Medium
**What happened:** `nodeState.startElection()` returns bool, but since it comes through actor messaging as Object, `if !canStart` fails or produces wrong bytecode.
**Workaround:** `mut canStart = toString(nodeState.startElection())` then `if canStart != "true"`.
**What I wanted:** Automatic unboxing of Object to bool in conditionals.

### 12. Compiler runs evaluator after compiling (double execution attempt)
**Severity:** Low (cosmetic)
**What happened:** `siyoc run` both compiles and evaluates. For server programs, the evaluator also tries to bind the port, causing `BindException`. The compiled version works fine.
**Workaround:** Ignore the evaluator's error — the compiled code runs correctly.

### 13. No `std/time` module
**Severity:** Low
**What happened:** No way to get current time, format dates, or sleep without Java interop.
**Workaround:** `import java "java.lang.System"` for `System.currentTimeMillis()`, `import java "java.lang.Thread"` for `Thread.sleep()`.

### 14. Array construction requires `[""]` + push + removeAt(0) pattern
**Severity:** Medium
**What happened:** No way to create an empty typed array. Must use `[""]` placeholder, push items, then `removeAt(arr, 0)` to remove the dummy first element.
**What I wanted:** `mut arr = string[]()` or `mut arr: string[] = []`
**Example:**
```siyo
// Have to write:
mut result = [""]
for k in allKeys { push(result, toString(k)) }
removeAt(result, 0)

// Wanted:
mut result = []
for k in allKeys { push(result, toString(k)) }
```

### 15. No way to type function parameters as specific actor/struct types
**Severity:** High (fundamental)
**What happened:** All helper functions must take `object` for actor params. This erases type info, causing cascading issues (#5, #8, #9, #11).
**What I wanted:** `fn heartbeatLoop(nodeState: NodeState, myAddr: string)` — the compiler knows the actor type and can track return types of method calls.

### 16. f-string interpolation doesn't auto-convert int to string
**Severity:** Medium
**What happened:** `"port=$myPort"` works, but `"keys=$keyCount"` where `keyCount` is int may not find the variable or may produce wrong type.
**Workaround:** Convert to string first: `mut kc = toString(keyCount)` then use `$kc`.
**What I wanted:** Automatic toString() in f-string interpolation for all types.

## Architecture Observations

### What worked well
- **Actor model**: Perfect for this use case. Each node's state is an actor — no shared mutable state, no locks.
- **spawn for connection handling**: Fire-and-forget virtual threads (after fix #1) are ideal for server patterns.
- **TCP via Java interop**: ServerSocket/Socket work perfectly. Simple and reliable.
- **Match expressions**: Great for command dispatch once the block body issue was fixed.
- **String interpolation**: Very readable for log messages and protocol strings.
- **try/catch**: Essential for network failure handling. Works as expected.

### What was painful
- **Type erasure through actors**: The single biggest pain point. Every actor method call returns Object, losing all type information. This cascades into needing explicit conversions everywhere.
- **No async/await or Futures**: Replication is synchronous because there's no way to fire-and-forget from a non-actor function. The `send` keyword only works with actors.
- **Single-file modules**: All actors and functions must be in one file per module. For a project this size (~800 lines), this gets unwieldy.
- **No generics**: Can't write type-safe helper functions that work with different types.

### What I wanted but didn't have
1. **Timer/scheduler actor**: Built-in periodic execution without `while true { sleep(n) }` loops
2. **async/await**: For non-blocking replication and sync operations
3. **Map iteration with key+value**: `for (k, v) in map { }` — had to iterate keys then lookup values
4. **String-to-int casting without toString**: Direct `toInt(actor.method())` 
5. **Typed actor references**: `ActorRef<NodeState>` so method calls preserve return types
6. **Multi-file actors**: Split actors across files in a project
7. **Pattern matching on strings**: `match` uses equality — would love prefix matching
8. **Empty array literal**: `[]` instead of `[""]` + removeAt hack

## Summary

**Compiler bugs fixed:** 4 (all blockers for this project)
**Language pain points:** 12 (5 high, 4 medium, 3 low)
**Lines of code:** ~800 (main.siyo) + ~250 (test.siyo)
**Actors used:** 2 (Store, NodeState)
**Functions:** 20+

The biggest takeaway: **actor method return type erasure is the #1 issue**. It affects everything — arithmetic, comparisons, conditionals, array operations. If Siyo adds typed actor references (even basic ones), it would eliminate 60% of the workarounds in this project.

The compiler fixes made during this project (match arm blocks, bare spawns, actor capture, Object indexing) are real bugs that affect all existing projects — siyodb and broker both failed to compile before these fixes.
