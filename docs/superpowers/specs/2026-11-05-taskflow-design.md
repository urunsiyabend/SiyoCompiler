# TaskFlow design (Siyo 0.9.0 dogfood)

## Intent

Write a useful multi-module CLI in Siyo and let it show what the language still
gets wrong. Close the gaps it demonstrates and ship them as 0.9.0. Nothing goes
into the compiler or standard library unless TaskFlow, or a probe written while
building it, hit it. The app does not wrap Java.

Requested by the user: JSON task/dependency files, input validation, cycle
detection, deterministic plans, status reports; each pain point recorded with a
reproducer, an outcome and coverage on both backends. The user delegated
planning and review, and print mode has nobody to ask, so the assumptions below
are decisions, not open questions.

## Input

One or more JSON files, merged. A file is an array of tasks or an object:

```json
{"project": "name",
 "tasks": [{"id": "a", "title": "...", "status": "todo", "priority": 0,
            "estimate": 1, "owner": "ada", "deps": ["b"]}],
 "dependencies": [{"task": "a", "needs": ["c"]}]}
```

Only `id` is required. `status` is `todo` (default), `doing`, `done` or `failed`.
`priority` is an integer in ±1,000,000, higher first (default 0). `estimate` is
whole hours in 0..1,000,000 (default 1). An unknown field is an error, because a
typo such as `dep` would otherwise produce a wrong plan silently.

## Commands and exit codes

| Command | Does | Exit |
|---|---|---|
| `validate F...` | every problem and every cycle, or `ok` | 0, or 3 if invalid |
| `cycles F...` | the dependency cycles, and any problems that stop the answer being complete | 0, or 3 if any |
| `plan F... [--workers N] [--target ID]` | batches, blocked tasks, critical path | 0, or 3 if invalid |
| `status F...` | counts, progress, running, ready, failed, blocked, warnings | 0, or 3 if invalid |

All take `--json`. Exit 1 is a usage error, 2 an unreadable file. Malformed JSON
is invalid input (3), and is reported with the other files' problems.

## Determinism rules

- Tasks are sorted by id, so file order and argument order do not change output.
- Files are read in sorted order, and each from top to bottom, so a report of invalid
  input does not depend on the order files were listed in. Tasks and dependencies
  are sorted before they are resolved.
- A batch is ordered: `doing` first, then higher priority, then smaller id.
- A cycle starts at its smallest id and is the shortest way back to it, within its
  strongly connected group, smaller ids first. Cycles are sorted by first id.
- A critical path tie goes to the smaller rank.
- No wall-clock time or environment appears in output.

## Semantics

`done` satisfies a dependency. `failed` does not run and blocks everything
downstream, naming the failed tasks each one waits on. A `done` task with an unfinished dependency
is a warning in `status`, not an error: the history happened. `--target` limits a
plan to what the target needs, not looking through `done` tasks. Text output escapes control characters from input;
JSON output is escaped by `json.stringify`.

## Modules

`model` (types), `loader` (JSON to validated project), `graph` (Tarjan cycles,
closure), `heap` (min-heap of ranks), `plan` (batches, blocked, critical path),
`status`, `report` (text and JSON), `cli`, `main`. The graph walk keeps its own
stack, because input decides how deep it goes.

## Compiler and library policy

Each change needs a reproducer in `projects/taskflow/PAIN_POINTS.md` and a test
asserting the same result on `siyoc run` and `siyoc interpret`. A gap TaskFlow can
live without, or whose fix is a design of its own, is recorded as deferred with
its reason. The version becomes 0.9.0 because additive features land
(`json.parseValue`, `strings.isInt`, a map literal as a tail value, interpolating
any value, `for` over a set).

## Testing

- Siyo unit tests in `projects/taskflow/tests`, run by `siyoc test` and each
  interpreted.
- `TaskFlowEndToEndTest`: the real CLI on both backends under a C locale against
  goldens, including adversarial fixtures, generated deep and wide graphs, and
  independent checks of the output (topological validity with a strict JSON
  parser) rather than only goldens.
- `TaskFlowRegressionTest`, `CliParityTest`: one test per language gap.
- Release gate: `mvn clean verify`.
