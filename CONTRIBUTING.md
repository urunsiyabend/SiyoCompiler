# Contributing to Siyo

This project moves quickly, but contributions still need to be reviewable, reproducible, and small enough to reason about. Prefer targeted pull requests over broad refactors.

## Setup

Requirements:

- Java 21+
- Maven 3.9+

Basic local setup:

```bash
git clone https://github.com/urunsiyabend/SiyoCompiler.git
cd SiyoCompiler
mvn test
```

If you are working on the CLI or packaging flow, also verify:

```bash
mvn package
```

## What to Work On

Good contributions usually fall into one of these buckets:

- Compiler correctness fixes
- New syntax or language features with tests
- Standard library improvements
- Better diagnostics, docs, and examples
- Small project samples that demonstrate real language capabilities

Before implementing a large feature, open an issue or start a discussion so the direction is clear.

## Contribution Rules

- Keep changes focused. One pull request should solve one problem.
- Add or update tests for behavior changes.
- Update docs when syntax, CLI behavior, or standard library APIs change.
- Preserve existing project style unless there is a strong reason to change it.
- Avoid mixing unrelated cleanup with feature work.

## Tests

At minimum, run the tests affected by your change.

Typical checks:

```bash
mvn test
```

Useful manual checks:

```bash
java -jar target/siyo-compiler-0.2.0-SNAPSHOT-shaded.jar run examples/compile_test.siyo
java -jar target/siyo-compiler-0.2.0-SNAPSHOT-shaded.jar test
```

If you change parser, binder, evaluator, emitter, modules, or stdlib behavior, include automated coverage in `src/test/java`.

## Commit Guidelines

- Make small commits with clear intent.
- Use imperative commit messages.
- Separate compiler/runtime changes from docs, examples, and sample projects when possible.

Examples:

- `Add map literal parsing`
- `Fix module top-level initialization in bytecode`
- `Refresh examples for stdlib imports`

## Pull Requests

A good pull request should include:

- What changed
- Why it changed
- How it was tested
- Any known limitations or follow-up work

If your change affects language syntax or semantics, mention user-visible examples in the PR description.

## Documentation

Update the relevant files when behavior changes:

- `README.md` for user-facing workflow changes
- `GRAMMAR.md` for syntax or type-system changes
- `FUTURE.md` for roadmap adjustments

## Scope Discipline

Do not bundle these together unless they are tightly coupled:

- New syntax
- Runtime semantics
- Example rewrites
- Large project-sample restructures
- Documentation-only edits

Small, isolated contributions are much easier to review and much less likely to regress the compiler.
