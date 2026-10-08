# LogScope — dogfooding design

Date: 2026-10-08. Branch: `dogfood/logscope`. Baseline release: 0.7.0.

## Intent

LogScope is a real, multi-module Siyo CLI that reads JSONL logs, filters records,
and produces error summaries plus JSON and HTML reports. Its purpose is
dogfooding: every place where Siyo makes the program harder to write than it
should be is recorded as a pain point with a minimal reproducer, and the
justified compiler/stdlib gaps are fixed in the compiler (not worked around in
the app or hidden behind Java), with regression tests that check
compiled/interpreted parity.

Constraints (from the request): work only in this worktree; preserve unrelated
changes; no push/merge/tag/publish; commit the reviewed result; produce a
release-ready version bump and release notes following SemVer (patch if only
bug fixes, minor if any additive language/stdlib feature).

Assumptions (mine): the app targets a typical structured log shape
(`ts`, `level`, `service`, `msg`, optional extra fields); timestamps are
ISO-8601 UTC strings so lexicographic comparison orders them; reports must be
deterministic so they can be golden-tested.

## Application

Location: `projects/logscope` (a `siyo.toml` project, like `projects/sitegen`).

```
siyoc run summary <file.jsonl> [filters]
siyoc run filter  <file.jsonl> [filters]          # matching records as JSONL
siyoc run report  <file.jsonl> [--json out] [--html out] [filters]
filters: --level L[,L]  --service S  --since TS  --until TS  --grep TEXT
exit codes: 0 ok, 1 usage error, 2 input error (missing/unreadable file)
```

Modules (each one purpose):

| module | responsibility |
|---|---|
| `src/main.siyo` | entry point, command dispatch, exit codes |
| `src/cli.siyo` | argv → `Options` struct or usage error |
| `src/record.siyo` | `LogRecord` struct; parse one JSONL line into `Line = Rec(LogRecord) \| Bad(int, string)` |
| `src/query.siyo` | `Filter` struct and `matches(filter, record)` |
| `src/summary.siyo` | aggregate: totals, invalid lines, counts by level/service, error groups (message, count, first/last ts, services) |
| `src/render.siyo` | text summary, JSON report, HTML report (escaped) |

Malformed lines never abort a run: they are counted and listed (line number +
reason) in the summary. Error groups are records with level `error` or `fatal`,
grouped by message, ordered by count desc then message asc.

## Testing

- `projects/logscope/tests/*_test.siyo` — unit tests per module via `std/testing`
  (`siyoc test` from the project dir).
- `projects/logscope/fixtures/` — sample logs (clean, mixed with malformed lines,
  empty) and golden outputs (`expected/`).
- `src/test/java/codeanalysis/LogScopeEndToEndTest.java` — runs the real CLI
  through both backends against the fixtures and compares with goldens, so the
  app is part of `mvn test` and checks parity end to end.
- Every compiler/stdlib fix gets a focused JUnit regression asserting the same
  output compiled and interpreted.

## Pain-point process

Each finding goes into `projects/logscope/PAIN_POINTS.md` with: id, symptom,
minimal reproducer, compiled vs interpreted output, classification
(bug / missing feature / ergonomics), and status **fixed** (with the commit/test)
or **deferred** (with the reason). Fix criteria: a correctness or parity bug is
always fixed; a missing feature is fixed if the app genuinely needs it and the
change is small and coherent with the language; otherwise deferred.

Initial findings from probing (to be confirmed and extended while building):

1. `siyoc interpret file args…` drops program args (`os.args()` empty) — parity bug.
2. A lambda whose tail value is an `if/else` expression returns `0`/`null`
   when compiled — silent miscompile.
3. `Map<string, Array<Rec>>`: indexing the map then the array loses `Rec`
   (`Type object does not have members`).

## Release

Version is decided after the findings are final: 0.7.1 if only bug fixes land,
0.8.0 if any additive feature lands. Update `pom.xml`, `Main.VERSION`,
`SiyoProject` default, README badge/known-limitations, GRAMMAR header if
syntax/stdlib changed, `FUTURE.md`, `CONTRIBUTING.md` jar name, and add
`RELEASE_NOTES_<version>.md` in the existing style.
