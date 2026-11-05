# DataKit

Validates CSV and JSON data files against a schema, then transforms, groups and
profiles the rows that pass. It is a Siyo program from end to end: no app code
imports a Java class. Its data handling is a set of typed, generic pipeline
functions (`src/pipeline.siyo`). It was written to find out what Siyo 0.9.0
still got wrong, and what it found is in [PAIN_POINTS.md](PAIN_POINTS.md).

## Run

Run these from this directory, with `siyoc` 0.10.0 on your `PATH`. Without it,
use `java -jar ../../target/siyo-compiler-0.10.0.jar` in place of `siyoc`.

```bash
S="--schema fixtures/staff.schema.json"
siyoc run validate  fixtures/staff.csv $S
siyoc run transform fixtures/staff.csv $S --where dept!=sales --sort salary:desc --select id,name,salary
siyoc run transform fixtures/staff.json $S --where 'salary>=5000' --out /tmp/high.json
siyoc run group     fixtures/staff.csv $S --by dept --sum salary
siyoc run report    fixtures/staff.csv $S --json
siyoc test                                  # the unit tests
```

`siyoc interpret src/main.siyo group fixtures/staff.csv $S --by dept` gives the same output.

```
$ siyoc run group fixtures/staff.csv --schema fixtures/staff.schema.json --by dept --sum salary
dept   count  present  sum       min      max      mean
eng    3      3        24600.50  7300.00  9100.00  8200.17
ops    2      2        9450.25   4300.00  5150.25  4725.13
sales  2      1        3900.75   3900.75  3900.75  3900.75
```

## Schema

```json
{"name": "staff",
 "columns": [{"name": "id", "type": "int", "required": true, "min": 1},
             {"name": "dept", "type": "string", "values": ["eng", "ops", "sales"]},
             {"name": "salary", "type": "float", "min": 0}],
 "unknown": "reject"}
```

| Field | Meaning |
|---|---|
| `name` | Shown in reports. Optional. |
| `columns[].name` | Must be unique and non-empty. |
| `columns[].type` | Each type accepts different cells. `int`: a 64-bit integer, with an optional minus sign and ASCII digits. `float`: a finite decimal number, such as `-2.5`, `1e3` or `.5`; `NaN`, `Infinity` and surrounding spaces are not numbers. `string`: any text. `bool`: `true` or `false` exactly. `date`: `YYYY-MM-DD`, a real calendar date from year 0001. |
| `columns[].required` | An empty cell, a JSON `null` or an absent field is a problem. |
| `columns[].min`, `max` | Inclusive bounds. Number columns only. |
| `columns[].values` | The only values allowed. String columns only. |
| `unknown` | What happens to a column the schema does not name. `reject` (the default) makes it a problem of the file, reported once where it is first seen: a CSV header column, or the first JSON record with that key. No row is rejected for it, but the data is invalid. `ignore` drops it. |

An empty cell and a JSON `null` both mean *missing*, in every column type.

## Data

- **CSV** follows RFC 4180, using `std/csv`. It is read with `io.readText`, so
  line breaks inside quoted fields are kept as written. The first row is the header. Quoted
  fields may hold commas, doubled quotes and line breaks. CRLF, LF and CR all end
  a line, and a byte order mark is skipped. Blank lines are skipped; a record holding one quoted empty field (`""`) is not
  blank and is checked like any other. A row with
  a different number of fields from the header is a problem, and the other rows
  are still checked. Messages name the line a row starts on.
- **JSON** must be an array of objects. Messages name the record by its
  position, from 1. JSON types must match the column type exactly: `"5"` is not
  an int. An int is accepted where a float is expected.

## Commands

| Command | Output | Exit |
|---|---|---|
| `validate DATA --schema S [--json]` | The first 100 problems in file order, `... and N more` past that, then a summary counting all of them; or `ok: N rows, M columns` | 0 or 3 |
| `transform DATA --schema S [options]` | Rows that pass every `--where`, as CSV (header first) or as a JSON array of objects | 0 |
| `group DATA --schema S --by COL [--sum COL] [--json]` | One line per distinct value of COL, with count and (with `--sum`) present, sum, min, max and mean | 0 |
| `report DATA --schema S [--json]` | Each column's present, missing and distinct counts; min, max, sum and mean for numbers; earliest and latest for dates; true and false counts for bools; the three most frequent values for strings | 0 |

All problems are found and counted. Text output and `--json` list the first
100: in JSON, `problemCount` is the total and `problems` holds at most 100, so
the two differ for a file with more. `rows` counts every data row read,
including a ragged CSV line or a JSON record that is not an object, and
`invalidRows` those rejected.

`transform`, `group` and `report` compute nothing from invalid data. Each
prints the problems to standard error and exits 3.

Transform options:

- `--where COL=VAL`, also with `!=`, `>=`, `<=`, `>` or `<`. Repeatable, and every
  condition must hold. The value is read as the column's type. An empty value
  means missing, so `--where start=` keeps rows with no start. Ordering
  operators never match a missing value, and do not apply to a bool.
- `--select A,B` writes these columns, in this order.
- `--sort COL[:desc]` sorts stably. Missing values come last in either direction.
- `--limit N` writes at most N rows.
- `--format csv|json` sets the output format. By default it comes from `--out`'s
  extension, else CSV. A `--format` that contradicts a `.csv` or `.json` `--out`
  is a usage error.
- `--out FILE` writes to FILE rather than standard output. It never overwrites:
  if FILE exists or is an input file, DataKit exits 2. The file is created
  exclusively with `io.writeNew`, so a file or symbolic link that appears between
  the check and the write is refused too, never followed or truncated. If writing
  fails part-way (a full disk), exit 2 and the partial file DataKit created stays;
  it is never removed by name, since the path may by then be someone else's. Nothing is written when
  the data or a flag is invalid.

Ordering and number formats:

- **Groups** are ordered by value: numbers numerically, text and dates as text,
  false before true. Missing comes last, as `(missing)` in text and `null` in JSON.
- **Sums** of an int column are exact 64-bit integers. A sum that does not fit
  is an error (exit 3), not a wrapped number. Sums of a float column are floats;
  one past the largest float is an error too (exit 3), never `Infinity` or `null`.
- **Floats** are written losslessly, in the shortest decimal that reads back as
  the same float and never in exponent form (`0.000000000000000001`), in CSV,
  problem messages and group keys. `1e-18` and `2e-18` are different groups.
- **Bounds** keep their written value: an integer `min` or `max` is compared as
  an exact 64-bit integer.
- **Group and report tables** show floats to two places, rounded half up.
  Problem messages and CSV output show a value's shortest exact form. JSON carries full
  precision.
- **The mean** is the sum divided by the count of present values.
- **String profiles** rank by count, then by value.

Exit codes: 0 ok, 1 usage error (an unknown flag, a missing or flag-like value,
a column the schema lacks, a bad number), 2 a file cannot be read or written,
or `--out` exists, 3 invalid data or schema.

Text output shows control characters and bidirectional controls as `\uXXXX`
escapes. JSON output escapes what JSON requires. CSV output is data and is
written as it is.

## Layout

| Module | Role |
|---|---|
| `cli` | Turns arguments into Options, or a usage error |
| `schema` | Reads the schema file and reports every problem in it |
| `source` | Turns a CSV or JSON file into raw rows |
| `validate` | Types each cell by its column, and collects problems |
| `pipeline` | Generic steps: `keep`, `take`, `sortBy`, `groupBy`, `sumBy`, `countBy`, `distinct`, `Group<T>` |
| `query` | `--where`, `--sort` |
| `stats` | Groups and column profiles |
| `render` | Text, JSON and CSV |
| `model` | `Value`, `Record`, `Schema`, `Problem`, comparison and escaping |

Fixtures are in `fixtures/`. Hostile inputs are in `fixtures/adversarial/`, and
golden outputs in `fixtures/expected/`. `DataKitEndToEndTest` runs the CLI on
both backends against the goldens, and against an oracle written independently
in Java.
