# Siyo 0.4.0 — Module Boundary Maturity

Siyo 0.4.0 is driven by the Aja static-site-generator maturity test. Its focus
is preserving semantic and JVM type information when code crosses module,
closure, and Java interop boundaries.

## Fixed

- Imported enums are exported with their module and remain available as
  `Enum.Member` in consumers.
- Imported `impl` methods retain the declaring module as their JVM owner and
  the emitted method name, avoiding `NoSuchMethodError`.
- Modules no longer re-export transitive imports under a new JVM owner, fixing
  linkage in diamond-shaped application module graphs.
- `T[]` function returns retain their element type (and struct identity) across
  module boundaries, including diamond-shaped imports.
- Struct identity is retained for typed array parameters and their `for-in`
  elements, so imported impl methods dispatch statically inside collection code.
- Typed lambda parameters retain imported struct and Java identities.
- Lambda return opcodes are derived from the lambda itself rather than leaked
  enclosing-module emitter state, preventing module-only `VerifyError`s.
- Imported Java classes can be used in function, lambda, and impl signatures.
- Java constructor/method overload selection uses exact JVM descriptors when
  available while retaining reference-supertype compatibility.
- Erased `object` arguments that could select multiple Java overloads now fail
  with an actionable compile-time diagnostic instead of invalid bytecode or an
  arbitrary runtime cast.
- Void-valued `match` statements no longer emit an invalid trailing `POP`.
- Missing qualified module members now report the missing function rather than
  claiming the module qualifier is undefined.
- The legacy documented `actor struct Name` form is accepted alongside
  `actor Name`, eliminating a parser non-progress loop for that spelling.
- Every parser list loop — parameters, arguments, struct/actor fields, enum
  members, match arms, `impl` bodies, and map/array/struct literals — now
  guarantees progress. Malformed input such as the pre-0.4.0 documented
  `fn Type.method()` spelling reported the same unexpected token forever and
  exhausted the heap with an `OutOfMemoryError`; it now yields diagnostics.
- Development launchers discover the current compiler artifact instead of
  hard-coding a stale JAR version.

## Validation

The release adds focused bytecode regression coverage for imported enums,
imported impl methods, typed array returns, typed inline comparators, Java type
annotations/overloads, qualified diagnostics, void matches, and actor syntax,
plus parser-termination coverage for twelve malformed sources. The suite is
1,499 tests. The Aja maturity report records the post-upgrade application-level
results.
