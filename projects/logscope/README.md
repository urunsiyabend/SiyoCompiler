# LogScope

LogScope reads a JSONL log — one JSON object per line — and tells you what is
in it: totals, counts by level and by service, and the error messages grouped
and ranked. It can also print the records that match a filter, or write the
summary as a JSON or HTML report.

It is a Siyo project, written to dogfood the language; what it ran into is in
[PAIN_POINTS.md](PAIN_POINTS.md).

## Running

From this directory, with `siyoc` 0.8.0 or later on your `PATH` (or
`java -jar ../../target/siyo-compiler-0.8.0.jar` in place of `siyoc`):

```bash
siyoc run summary fixtures/app.jsonl
siyoc run summary fixtures/mixed.jsonl --level error,warn
siyoc run filter  fixtures/app.jsonl --grep timeout
siyoc run report  fixtures/mixed.jsonl --json report.json --html report.html
siyoc run help
```

The same program runs on the interpreter:

```bash
siyoc interpret src/main.siyo summary fixtures/app.jsonl
```

### Commands

| command | does |
|---------|------|
| `summary <file>` | prints totals, counts by level and service, error groups and invalid lines |
| `filter <file>` | prints every matching record as JSONL, exactly as it was read |
| `report <file>` | writes the summary as JSON (`--json PATH`) and/or HTML (`--html PATH`); one is required |
| `help` | prints usage |

### Filters

All filters combine; a record must pass every one given.

| flag | keeps records |
|------|---------------|
| `--level L[,L]` | whose level is one of these (case-insensitive) |
| `--service S` | whose service is `S` |
| `--since TS` | at or after `TS` |
| `--until TS` | before `TS` |
| `--grep TEXT` | whose message contains `TEXT` (case-insensitive) |

A flag's value may not be another LogScope flag: `report x --json --html` is a
usage error rather than a JSON report written to a file named `--html`.

Timestamps are RFC 3339 / ISO-8601 in UTC: `2026-03-01T10:00:00Z`, with an
optional fraction of up to nine digits (`10:00:00.5Z`) and the zone written
`Z`, `z`, `+00:00` or `-00:00`; a filter may also be a date (`2026-03-01`,
midnight UTC). They are ordered by time, not as text, so `10:00:00Z` comes
before `10:00:00.5Z`. A record with no `ts` fails any time filter; a record
whose `ts` is not a UTC timestamp is listed as invalid; an invalid
`--since/--until` is a usage error.

### Records

Each line must be a JSON object with a string `level` and a string `msg`.
`ts` (a UTC timestamp, see above) and `service` are optional strings (`service` defaults to `-`); any other
fields are kept and printed back by `filter`. Levels are lowercased. Blank
lines are skipped. Any other line is counted as invalid and listed with its
line number and the reason — it never stops the run.

Error groups are the `error` and `fatal` records grouped by message, ordered
by count and then by message, each with its first and last timestamp and the
services that logged it. Levels are listed by severity (fatal, error, warn,
info, debug, trace, then any other), services alphabetically.

### Exit codes

| code | meaning |
|------|---------|
| 0 | success |
| 1 | usage error (message and usage on stderr) |
| 2 | the input file cannot be read, or a report cannot be written (message on stderr) |

## Layout

```
src/main.siyo      entry point: dispatch, exit codes
src/cli.siyo       argv -> Options, or a usage error
src/record.siyo    one JSONL line -> LogRecord, or why it is not one
src/timestamp.siyo UTC timestamps -> keys that order by time
src/query.siyo     Filter and matches()
src/summary.siyo   totals, counts, error groups
src/render.siyo    text, JSON and HTML output
tests/             unit tests per module (std/testing)
fixtures/          sample logs; fixtures/expected holds the golden outputs
```

## Testing

```bash
siyoc test                                  # unit tests, compiled
siyoc interpret tests/summary_test.siyo     # one file, interpreted
```

From the repository root, `mvn test` also runs
`LogScopeEndToEndTest`, which drives this CLI through both backends under a C
locale and compares every command's output, exit code and report files with
`fixtures/expected`, and runs the unit tests on both backends.

To regenerate a golden after an intended output change, run the command and
redirect its output into `fixtures/expected`, then review the diff.
