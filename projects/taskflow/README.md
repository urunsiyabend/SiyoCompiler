# TaskFlow

Validates JSON task files, finds dependency cycles, computes a deterministic
execution plan and reports status. It is a Siyo program from end to end: no app
code imports a Java class. Written to find out what Siyo still gets wrong; what
it found is in [PAIN_POINTS.md](PAIN_POINTS.md).

## Run

From this directory, with `siyoc` 0.9.0 or later on your `PATH` (or
`java -jar ../../target/siyo-compiler-0.9.0.jar` in place of `siyoc`):

```bash
siyoc run validate fixtures/release.json
siyoc run plan     fixtures/release.json --workers 2
siyoc run plan     fixtures/release.json --target tests
siyoc run status   fixtures/tasks_app.json fixtures/deps_app.json
siyoc run cycles   fixtures/adversarial/cycles.json --json
siyoc test                                  # the unit tests
```

`siyoc interpret src/main.siyo plan fixtures/release.json` gives the same output.

```
$ siyoc run plan fixtures/tasks_app.json fixtures/deps_app.json --workers 2
plan for storefront
batch 1:
  beta (2h)  Beta programme
  api (8h)  HTTP API  @lin
batch 2:
  ui (13h)  Web UI  @noor
batch 3:
  e2e (4h)  End-to-end suite
blocked:
  admin by auth
  launch by auth
remaining: 27h
critical path (17h): ui -> e2e
```

## Input

One or more files, which are merged. A file is an array of tasks, or an object:

```json
{
  "project": "storefront",
  "tasks": [{"id": "api", "title": "HTTP API", "status": "doing", "priority": 2,
             "estimate": 8, "owner": "lin", "deps": ["schema"]}],
  "dependencies": [{"task": "e2e", "needs": ["api", "ui"]}]
}
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `id` | non-empty string | required | unique across all files |
| `title` | string | the id | |
| `status` | `todo`, `doing`, `done`, `failed` | `todo` | |
| `priority` | integer, ±1,000,000 | 0 | higher runs first |
| `estimate` | integer, 0 to 1,000,000 | 1 | hours |
| `owner` | string | none | |
| `deps` | array of ids | none | what this task needs |

A dependency may be in another file. `null` reads as absent. An unknown field is
an error, since a typo like `dep` would otherwise produce a wrong plan silently.

## Commands

| Command | Output | Exit |
|---|---|---|
| `validate F...` | every problem and cycle, or `ok: N tasks, M dependencies` | 0 or 3 |
| `cycles F...` | the dependency cycles | 0, or 3 if any |
| `plan F... [--workers N] [--target ID]` | batches, blocked tasks, hours, critical path | 0 or 3 |
| `status F...` | counts, progress, running, ready, failed, blocked, warnings | 0 or 3 |

Every command takes `--json`. Exit codes: 0 ok, 1 usage error, 2 a file cannot be
read, 3 invalid input. Malformed JSON counts as invalid input and is reported with
the other files' problems. `plan` and `status` print nothing on invalid input but
the problems, on standard error.

## What the plan means

- `done` satisfies a dependency. `failed` does not run, and blocks everything
  downstream, naming the failed tasks responsible. A `done` task stops a failure
  spreading through it: the work happened.
- A batch holds tasks that can run together, at most `--workers` of them.
- The output is deterministic: tasks are ordered `doing` first, then higher
  priority, then smaller id, and neither file order nor task order changes it.
- A cycle starts at its smallest id and takes the smallest id that stays in its
  strongly connected group. One cycle is named per group.
- The critical path is the longest remaining chain by hours; a tie goes to the
  task that would run first.
- `status` warns about a `done` or `doing` task whose dependencies are unfinished.

Text output escapes control characters from the input, so a hostile task title
cannot drive your terminal. JSON output escapes what JSON requires (U+0000 to
U+001F); it is meant for programs, so read it with one.

## Layout

```
src/model.siyo    Task, Problem, Project
src/loader.siyo   JSON to a validated, merged Project
src/graph.siyo    the dependency graph: cycles (Tarjan, own stack), closure
src/heap.siyo     a min-heap of ints, for the ready list
src/plan.siyo     batches, blocked tasks, critical path
src/status.siyo   progress
src/report.siyo   text and JSON reports
src/cli.siyo      argv to Options
src/main.siyo     wires them together, owns the exit codes
tests/            unit tests (`siyoc test`)
fixtures/         realistic and adversarial input, goldens in expected/
```

`src/test/java/codeanalysis/TaskFlowEndToEndTest.java` runs the whole CLI on both
backends under a C locale against those goldens, and checks generated graphs
against rules worked out independently of the application.

## Limits

Graph depth is bounded by memory, not the call stack. A chain of 100,000 tasks
takes about 20 s compiled and about 60 s interpreted; 10,000 takes a few seconds.
