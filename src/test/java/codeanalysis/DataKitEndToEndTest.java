package codeanalysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs DataKit (projects/datakit) through the real CLI on both backends under a
 * C locale. Fixed fixtures are checked against goldens; generated data sets are
 * checked against a CSV reader and computations written here from the README
 * alone, so a bug in the application cannot hide behind a golden recorded from it.
 */
class DataKitEndToEndTest {
    private static final Path PROJECT = Path.of("projects", "datakit").toAbsolutePath();
    private static final Path EXPECTED = PROJECT.resolve("fixtures").resolve("expected");
    private static final Map<String, String> C_LOCALE = Map.of("LC_ALL", "C", "LANG", "C");
    private static final List<String> MODES = List.of("run", "interpret");

    private static final String CSV = "fixtures/staff.csv";
    private static final String JSON = "fixtures/staff.json";
    private static final String SCHEMA = "fixtures/staff.schema.json";
    private static final String BAD = "fixtures/adversarial/";

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("datakit-e2e");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    // --- goldens ---------------------------------------------------------------

    /** A golden for standard output, or (stderr) for standard error with nothing on standard output. */
    private record Case(String golden, int exit, boolean stderr, String... args) {
    }

    private static Case out(String golden, int exit, String... args) {
        return new Case(golden, exit, false, args);
    }

    private static final List<Case> GOLDEN = new ArrayList<>(List.of(
            out("validate_staff_csv.txt", 0, "validate", CSV, "--schema", SCHEMA),
            out("validate_staff_csv.json", 0, "validate", CSV, "--schema", SCHEMA, "--json"),
            out("validate_staff_json.txt", 0, "validate", JSON, "--schema", SCHEMA),
            out("validate_staff_json.json", 0, "validate", JSON, "--schema", SCHEMA, "--json"),
            out("transform_staff_csv.csv", 0, "transform", CSV, "--schema", SCHEMA,
                    "--where", "dept!=sales", "--sort", "salary:desc", "--select", "id,name,salary"),
            out("transform_staff_json.csv", 0, "transform", JSON, "--schema", SCHEMA,
                    "--where", "dept!=sales", "--sort", "salary:desc", "--select", "id,name,salary"),
            out("transform_staff_high.json", 0, "transform", JSON, "--schema", SCHEMA,
                    "--where", "salary>=5000", "--format", "json"),
            out("transform_staff_limit.csv", 0, "transform", CSV, "--schema", SCHEMA,
                    "--sort", "start", "--limit", "4", "--select", "name,start"),
            out("transform_staff_sort.json", 0, "transform", CSV, "--schema", SCHEMA,
                    "--sort", "salary", "--format", "json", "--select", "id,salary"),
            out("transform_staff_desc.json", 0, "transform", JSON, "--schema", SCHEMA,
                    "--sort", "start:desc", "--format", "json", "--select", "id,start"),
            out("transform_staff_no_start.csv", 0, "transform", CSV, "--schema", SCHEMA, "--where", "start="),
            out("transform_staff_two_wheres.csv", 0, "transform", CSV, "--schema", SCHEMA,
                    "--where", "remote=true", "--where", "salary<8000"),
            out("group_staff_csv.txt", 0, "group", CSV, "--schema", SCHEMA, "--by", "dept", "--sum", "salary"),
            out("group_staff_csv.json", 0, "group", CSV, "--schema", SCHEMA, "--by", "dept", "--sum", "salary", "--json"),
            out("group_staff_json.txt", 0, "group", JSON, "--schema", SCHEMA, "--by", "dept", "--sum", "salary"),
            out("group_staff_json.json", 0, "group", JSON, "--schema", SCHEMA, "--by", "dept", "--sum", "salary", "--json"),
            out("group_staff_remote.txt", 0, "group", JSON, "--schema", SCHEMA, "--by", "remote", "--sum", "id"),
            out("group_staff_remote.json", 0, "group", JSON, "--schema", SCHEMA, "--by", "remote", "--sum", "id", "--json"),
            out("group_staff_start.txt", 0, "group", CSV, "--schema", SCHEMA, "--by", "start"),
            out("report_staff_csv.txt", 0, "report", CSV, "--schema", SCHEMA),
            out("report_staff_csv.json", 0, "report", CSV, "--schema", SCHEMA, "--json"),
            out("report_staff_json.txt", 0, "report", JSON, "--schema", SCHEMA),
            out("report_staff_json.json", 0, "report", JSON, "--schema", SCHEMA, "--json"),
            out("report_hostile.txt", 0, "report", BAD + "hostile.csv", "--schema", SCHEMA),
            out("group_hostile.txt", 0, "group", BAD + "hostile.csv", "--schema", SCHEMA, "--by", "name"),
            out("group_hostile.json", 0, "group", BAD + "hostile.csv", "--schema", SCHEMA, "--by", "name", "--json"),
            out("transform_hostile.json", 0, "transform", BAD + "hostile.csv", "--schema", SCHEMA, "--format", "json"),
            new Case("schema_bad_schema.txt", 3, true, "validate", CSV, "--schema", BAD + "bad_schema.json"),
            new Case("schema_not_object.txt", 3, true, "validate", CSV, "--schema", BAD + "schema_not_object.json"),
            new Case("schema_malformed.txt", 3, true, "validate", CSV, "--schema", BAD + "schema_malformed.json")));

    static {
        Map<String, Integer> adversarial = new LinkedHashMap<>();
        adversarial.put("bad_cells.csv", 3);
        adversarial.put("hostile.csv", 0);
        adversarial.put("empty.csv", 3);
        adversarial.put("header_only.csv", 0);
        adversarial.put("blank_lines.csv", 0);
        adversarial.put("duplicate_header.csv", 3);
        adversarial.put("unknown_column.csv", 3);
        adversarial.put("missing_column.csv", 3);
        adversarial.put("unclosed_quote.csv", 3);
        adversarial.put("stray_quote.csv", 3);
        adversarial.put("trailing_text.csv", 3);
        adversarial.put("bad_records.json", 3);
        adversarial.put("scalar.json", 3);
        adversarial.put("malformed.json", 3);
        adversarial.put("duplicate_key.json", 3);
        adversarial.put("nested.json", 3);
        Set<String> withJson = Set.of("bad_cells.csv", "bad_records.json", "empty.csv", "unclosed_quote.csv",
                "duplicate_key.json");
        adversarial.forEach((file, exit) -> {
            String stem = file.substring(0, file.lastIndexOf('.'));
            GOLDEN.add(out("validate_" + stem + ".txt", exit, "validate", BAD + file, "--schema", SCHEMA));
            if (withJson.contains(file)) {
                GOLDEN.add(out("validate_" + stem + ".json", exit, "validate", BAD + file, "--schema", SCHEMA, "--json"));
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> everyFixtureMatchesItsGoldenOnBothBackends() {
        return GOLDEN.stream().map(c -> DynamicTest.dynamicTest(c.golden(), () -> {
            String expected = Files.readString(EXPECTED.resolve(c.golden()), StandardCharsets.UTF_8).trim();
            for (String mode : MODES) {
                CliParityTest.Result result = datakit(mode, c.args());
                String command = mode + " " + String.join(" ", c.args());
                assertEquals(c.exit(), result.exitCode(), command + ": " + result.stderr());
                if (c.stderr()) {
                    assertEquals("", result.stdout(), command);
                    assertEquals(expected, result.stderr(), command);
                } else {
                    assertEquals(expected, result.stdout(), command);
                }
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyJsonGoldenIsStrictlyValidJson() {
        return GOLDEN.stream().filter(c -> c.golden().endsWith(".json")).map(c -> DynamicTest.dynamicTest(c.golden(), () ->
                StrictJson.parse(Files.readString(EXPECTED.resolve(c.golden()), StandardCharsets.UTF_8))));
    }

    @Test
    void theProjectsOwnTestsPassOnBothBackends() throws Exception {
        CliParityTest.Result suite = CliParityTest.siyoc(PROJECT, C_LOCALE, "test");
        assertEquals(0, suite.exitCode(), suite.stdout() + suite.stderr());
        assertTrue(suite.stdout().contains(" 0 failed"), suite.stdout());
        assertFalse(suite.stdout().contains("FAIL"), suite.stdout());
        try (Stream<Path> tests = Files.list(PROJECT.resolve("tests"))) {
            List<Path> files = tests.filter(p -> p.toString().endsWith("_test.siyo")).sorted().toList();
            assertFalse(files.isEmpty());
            for (Path test : files) {
                CliParityTest.Result result = CliParityTest.siyoc(PROJECT, C_LOCALE, "interpret", test.toString());
                assertEquals(0, result.exitCode(), test + ": " + result.stdout() + result.stderr());
                assertTrue(result.stdout().contains(" passed, 0 failed"), test + ": " + result.stdout());
            }
        }
    }

    // --- hostile text ----------------------------------------------------------

    private static boolean unsafe(int c) {
        return (c < 0x20 && c != '\n') || (c >= 0x7f && c < 0xa0) || c == 0x200e || c == 0x200f
                || (c >= 0x202a && c <= 0x202e) || (c >= 0x2066 && c <= 0x2069) || c == 0x061c;
    }

    private static void assertNoControls(String text, String where) {
        text.codePoints().forEach(c -> assertFalse(unsafe(c), where + " wrote U+" + Integer.toHexString(c)));
    }

    @Test
    void textOutputCarriesNoControlOrBidiCharacterFromTheInput() throws Exception {
        // Invalid cells are quoted back in problem messages; they must be escaped too.
        Path hostile = tempDir.resolve("hostile_invalid.csv");
        Files.writeString(hostile, "id,name,dept,salary,start,remote\n"
                + "1,\"a\u001b[31m\",\"‮eng\u0007\",\"1\u0085\",\"2020-01-01⁦\",\"tr\u009bue\"\n");
        for (String mode : MODES) {
            for (String[] args : List.of(
                    new String[]{"validate", BAD + "hostile.csv", "--schema", SCHEMA},
                    new String[]{"report", BAD + "hostile.csv", "--schema", SCHEMA},
                    new String[]{"group", BAD + "hostile.csv", "--schema", SCHEMA, "--by", "name"},
                    new String[]{"validate", hostile.toString(), "--schema", SCHEMA},
                    new String[]{"transform", hostile.toString(), "--schema", SCHEMA})) {
                CliParityTest.Result result = datakit(mode, args);
                String command = mode + " " + String.join(" ", args);
                String text = result.stdout() + result.stderr();
                assertTrue(text.length() > 5, command);
                assertNoControls(text, command);
            }
            String report = datakit(mode, "report", BAD + "hostile.csv", "--schema", SCHEMA).stdout();
            assertTrue(report.contains("\\u001b[31m") && report.contains("\\u202e") && report.contains("\\u009b"),
                    mode + ": " + report);
            String problems = datakit(mode, "validate", hostile.toString(), "--schema", SCHEMA).stdout();
            assertTrue(problems.contains("\\u202eeng\\u0007"), mode + ": " + problems);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonAndCsvOutputCarryHostileTextUnchanged() throws Exception {
        String bell = "bell\u0007 esc\u001b[31m red";
        String rtl = "rtl ‮gnp.exe";
        for (String mode : MODES) {
            List<Object> rows = (List<Object>) StrictJson.parse(
                    datakit(mode, "transform", BAD + "hostile.csv", "--schema", SCHEMA, "--format", "json").stdout());
            assertEquals(bell, ((Map<String, Object>) rows.get(0)).get("name"), mode);
            assertEquals(rtl, ((Map<String, Object>) rows.get(1)).get("name"), mode);
            // A byte order mark is skipped: the first column is "id", not "﻿id".
            assertEquals(new BigDecimal(1), ((Map<String, Object>) rows.get(0)).get("id"), mode);
            String csv = datakit(mode, "transform", BAD + "hostile.csv", "--schema", SCHEMA).stdout();
            assertTrue(csv.contains(bell) && csv.contains(rtl), mode + ": CSV is data, written as it is");
        }
    }

    // --- refusing invalid input ------------------------------------------------

    @Test
    void transformGroupAndReportComputeNothingFromInvalidData() throws Exception {
        for (String mode : MODES) {
            for (String[] args : List.of(
                    new String[]{"transform", BAD + "bad_cells.csv", "--schema", SCHEMA},
                    new String[]{"group", BAD + "bad_cells.csv", "--schema", SCHEMA, "--by", "dept"},
                    new String[]{"report", BAD + "bad_records.json", "--schema", SCHEMA, "--json"})) {
                CliParityTest.Result result = datakit(mode, args);
                String command = mode + " " + String.join(" ", args);
                assertEquals(3, result.exitCode(), command);
                assertEquals("", result.stdout(), command);
                assertTrue(result.stderr().contains(args[1].endsWith(".csv")
                        ? "line 3, id: must be an integer" : "record 2, id:"), command + ": " + result.stderr());
            }
        }
    }

    @Test
    void anIntSumThatOverflowsIsAnError() throws Exception {
        Path file = tempDir.resolve("big.csv");
        Files.writeString(file, "id,name,dept\n9223372036854775807,a,eng\n2,b,eng\n");
        for (String mode : MODES) {
            CliParityTest.Result result = datakit(mode, "group", file.toString(), "--schema", SCHEMA,
                    "--by", "dept", "--sum", "id");
            assertEquals(3, result.exitCode(), mode + ": " + result.stdout() + result.stderr());
            assertEquals("", result.stdout(), mode);
        }
    }

    // --- command line misuse and file safety -----------------------------------

    @Test
    void aFlagIsNeverTakenAsAValue() throws Exception {
        for (String mode : MODES) {
            for (String[] args : List.of(
                    new String[]{"transform", CSV, "--schema", SCHEMA, "--out", "--json"},
                    new String[]{"transform", CSV, "--schema", SCHEMA, "--out"},
                    new String[]{"transform", CSV, "--schema", SCHEMA, "--where", "--json"},
                    new String[]{"group", CSV, "--schema", SCHEMA, "--by", "--json"},
                    new String[]{"validate", CSV, "--schema", "--json"})) {
                CliParityTest.Result result = datakit(mode, args);
                String command = mode + " " + String.join(" ", args);
                assertEquals(1, result.exitCode(), command + ": " + result.stderr());
                assertEquals("", result.stdout(), command);
            }
            assertFalse(Files.exists(PROJECT.resolve("--json")), mode + ": wrote a file named --json");
        }
        try (Stream<Path> left = Files.list(tempDir)) {
            assertEquals(0, left.count());
        }
    }

    @Test
    void anExistingOutFileIsNeverOverwritten() throws Exception {
        Path existing = tempDir.resolve("exists.csv");
        byte[] bytes = "keep me\n".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, bytes);
        for (String mode : MODES) {
            CliParityTest.Result result = datakit(mode, "transform", CSV, "--schema", SCHEMA, "--out", existing.toString());
            assertEquals(2, result.exitCode(), mode + ": " + result.stderr());
            assertArrayEquals(bytes, Files.readAllBytes(existing), mode);
        }
    }

    @Test
    void theOutFileMayNotBeAnInputFile() throws Exception {
        Path data = tempDir.resolve("data.csv");
        Path schema = tempDir.resolve("schema.json");
        Files.copy(PROJECT.resolve(CSV), data);
        Files.copy(PROJECT.resolve(SCHEMA), schema);
        byte[] dataBytes = Files.readAllBytes(data);
        byte[] schemaBytes = Files.readAllBytes(schema);
        for (String mode : MODES) {
            for (String target : List.of(data.toString(), schema.toString(), tempDir + "/./data.csv")) {
                CliParityTest.Result result = datakit(mode, "transform", data.toString(), "--schema", schema.toString(),
                        "--out", target);
                assertEquals(2, result.exitCode(), mode + " " + target + ": " + result.stderr());
                assertEquals("", result.stdout(), mode);
            }
            assertArrayEquals(dataBytes, Files.readAllBytes(data), mode);
            assertArrayEquals(schemaBytes, Files.readAllBytes(schema), mode);
        }
    }

    @Test
    void nothingIsWrittenWhenTheDataOrAFlagIsInvalid() throws Exception {
        Path target = tempDir.resolve("result.csv");
        for (String mode : MODES) {
            CliParityTest.Result invalid = datakit(mode, "transform", BAD + "bad_cells.csv", "--schema", SCHEMA,
                    "--out", target.toString());
            assertEquals(3, invalid.exitCode(), mode);
            assertFalse(Files.exists(target), mode + ": invalid data");
            CliParityTest.Result badFlag = datakit(mode, "transform", CSV, "--schema", SCHEMA,
                    "--out", target.toString(), "--limit", "x");
            assertEquals(1, badFlag.exitCode(), mode);
            assertFalse(Files.exists(target), mode + ": bad flag");
            CliParityTest.Result badColumn = datakit(mode, "transform", CSV, "--schema", SCHEMA,
                    "--out", target.toString(), "--sort", "nope");
            assertEquals(1, badColumn.exitCode(), mode);
            assertFalse(Files.exists(target), mode + ": bad column");
        }
    }

    @Test
    void anOutFileInAMissingDirectoryExitsTwo() throws Exception {
        Path target = tempDir.resolve("no").resolve("such").resolve("out.csv");
        for (String mode : MODES) {
            CliParityTest.Result result = datakit(mode, "transform", CSV, "--schema", SCHEMA, "--out", target.toString());
            assertEquals(2, result.exitCode(), mode + ": " + result.stderr());
            assertFalse(Files.exists(target), mode);
        }
    }

    @Test
    void aWrittenOutFileHoldsWhatStandardOutputWouldShow() throws Exception {
        for (String mode : MODES) {
            Path json = tempDir.resolve(mode + "_high.json");
            CliParityTest.Result wrote = datakit(mode, "transform", JSON, "--schema", SCHEMA,
                    "--where", "salary>=5000", "--out", json.toString());
            assertEquals(0, wrote.exitCode(), mode + ": " + wrote.stderr());
            assertFalse(wrote.stdout().contains("Ada"), mode + ": rows went to standard output");
            String printed = datakit(mode, "transform", JSON, "--schema", SCHEMA,
                    "--where", "salary>=5000", "--format", "json").stdout();
            String written = Files.readString(json, StandardCharsets.UTF_8);
            assertEquals(printed, written.trim(), mode + ": .json takes its format from the extension");
            StrictJson.parse(written);

            Path csv = tempDir.resolve(mode + "_all.csv");
            assertEquals(0, datakit(mode, "transform", CSV, "--schema", SCHEMA, "--sort", "name",
                    "--out", csv.toString()).exitCode(), mode);
            assertEquals(datakit(mode, "transform", CSV, "--schema", SCHEMA, "--sort", "name").stdout(),
                    Files.readString(csv, StandardCharsets.UTF_8).trim(), mode);
        }
    }

    @Test
    void usageErrorsExitOne() throws Exception {
        List<String[]> cases = List.of(
                new String[]{"transform", CSV, "--schema", SCHEMA, "--bogus"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--limit", "x"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--limit", "-1"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--format", "xml"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--where", "salary=abc"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--where", "id>1.5"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--where", "nope=1"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--where", "remote>true"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--sort", "nope"},
                new String[]{"transform", CSV, "--schema", SCHEMA, "--select", "id,nope"},
                new String[]{"group", CSV, "--schema", SCHEMA, "--by", "nope"},
                new String[]{"group", CSV, "--schema", SCHEMA, "--by", "dept", "--sum", "name"},
                new String[]{"group", CSV, "--schema", SCHEMA},
                new String[]{"validate", CSV},
                new String[]{"fly", CSV, "--schema", SCHEMA});
        for (String mode : MODES) {
            // Every case on the compiled backend; every third on the interpreter, to keep the run short.
            for (int i = 0; i < cases.size(); i += mode.equals("run") ? 1 : 3) {
                String[] args = cases.get(i);
                CliParityTest.Result result = datakit(mode, args);
                String command = mode + " " + String.join(" ", args);
                assertEquals(1, result.exitCode(), command + ": " + result.stdout() + result.stderr());
                assertEquals("", result.stdout(), command);
                assertTrue(result.stderr().startsWith("datakit: "), command + ": " + result.stderr());
            }
        }
    }

    @Test
    void anUnreadableFileExitsTwo() throws Exception {
        for (String mode : MODES) {
            for (String[] args : List.of(
                    new String[]{"validate", "fixtures/missing.csv", "--schema", SCHEMA},
                    new String[]{"report", CSV, "--schema", "fixtures/missing.json"},
                    new String[]{"validate", "fixtures", "--schema", SCHEMA})) {
                CliParityTest.Result result = datakit(mode, args);
                String command = mode + " " + String.join(" ", args);
                assertEquals(2, result.exitCode(), command + ": " + result.stderr());
                assertEquals("", result.stdout(), command);
            }
        }
    }

    // --- rules checked independently of the application ------------------------

    private static final List<String> COLUMNS = List.of("id", "cat", "n", "x", "d", "b", "note");
    private static final List<String> CATEGORIES = List.of("alpha", "beta", "gamma, inc", "say \"hi\"", "delta");
    private static final String GEN_SCHEMA = "{\"name\": \"gen\", \"columns\": ["
            + "{\"name\": \"id\", \"type\": \"int\", \"required\": true, \"min\": 1},"
            + "{\"name\": \"cat\", \"type\": \"string\", \"values\": [\"alpha\", \"beta\", \"gamma, inc\", \"say \\\"hi\\\"\", \"delta\"]},"
            + "{\"name\": \"n\", \"type\": \"int\"},"
            + "{\"name\": \"x\", \"type\": \"float\", \"min\": -1000, \"max\": 1000},"
            + "{\"name\": \"d\", \"type\": \"date\"},"
            + "{\"name\": \"b\", \"type\": \"bool\"},"
            + "{\"name\": \"note\", \"type\": \"string\"}], \"unknown\": \"reject\"}";
    private static final Map<String, List<String>> INVALID = Map.of(
            "id", List.of("0", "-3", "1.0", "x1"),
            "cat", List.of("omega", "Alpha", "beta "),
            "n", List.of("12x", "1.5", "9223372036854775808", " 5", "--1"),
            "x", List.of("NaN", "Infinity", "1e999", "1000.01", "abc", "1,5", " 1.00"),
            "d", List.of("2021-02-30", "2021-13-01", "0000-01-01", "2021-1-01", "1900-02-29", "20210101"),
            "b", List.of("TRUE", "yes", "1", "False"));

    /** One generated data set: the rows as written, and the cells made invalid on purpose. */
    private record Dataset(Path csv, Path schema, List<Map<String, String>> rows, Set<String> injected) {
    }

    private Dataset generate(long seed, int count, boolean invalid) throws Exception {
        Random random = new Random(seed);
        List<String> header = new ArrayList<>(COLUMNS);
        if (seed % 2 == 1) Collections.shuffle(header, random);
        String eol = switch ((int) (seed % 3)) {
            case 0 -> "\n";
            case 1 -> "\r\n";
            default -> "\r";
        };
        String[] notes = {"plain", "a,b", "say \"x\"", "line1\nline2", "cr\r\nlf", "  spaced  ", "ünïcödé",
                "\"", ",", "tab\there"};
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("id", Integer.toString(i + 1));
            row.put("cat", random.nextInt(10) == 0 ? "" : CATEGORIES.get(random.nextInt(CATEGORIES.size())));
            String n = "";
            if (random.nextInt(7) != 0) {
                n = random.nextInt(3) == 0
                        ? Long.toString((random.nextLong() >> 10)) // up to about 2^53 in size
                        : Integer.toString(random.nextInt(41) - 20);
            }
            row.put("n", n);
            String x = "";
            if (random.nextInt(7) != 0) {
                int cents = random.nextInt(4001) - 2000;
                x = BigDecimal.valueOf(cents, 2).toPlainString();
                if (cents % 100 == 0 && random.nextBoolean()) x = Integer.toString(cents / 100);
            }
            row.put("x", x);
            row.put("d", random.nextInt(7) == 0 ? ""
                    : LocalDate.of(1990, 1, 1).plusDays(random.nextInt(15000)).toString());
            row.put("b", random.nextInt(7) == 0 ? "" : Boolean.toString(random.nextBoolean()));
            row.put("note", random.nextInt(5) == 0 ? "" : notes[random.nextInt(notes.length)] + random.nextInt(3));
            rows.add(row);
        }
        Set<String> injected = new HashSet<>();
        if (invalid) {
            int k = 1 + random.nextInt(8);
            List<String> columns = new ArrayList<>(INVALID.keySet());
            Collections.sort(columns);
            while (injected.size() < k) {
                int r = random.nextInt(count);
                String column = columns.get(random.nextInt(columns.size()));
                if (injected.add(r + "/" + column)) {
                    List<String> bad = INVALID.get(column);
                    rows.get(r).put(column, bad.get(random.nextInt(bad.size())));
                }
            }
        }
        StringBuilder text = new StringBuilder();
        if (seed % 4 == 2) text.append('﻿');
        appendRecord(text, header, random, eol);
        for (Map<String, String> row : rows) {
            if (seed % 5 == 0 && random.nextInt(10) == 0) text.append(eol); // a blank line
            List<String> fields = new ArrayList<>();
            for (String column : header) fields.add(row.get(column));
            appendRecord(text, fields, random, eol);
        }
        if (seed % 2 == 0) text.setLength(text.length() - eol.length()); // no line break at the end
        Path csv = tempDir.resolve("gen" + seed + ".csv");
        Path schema = tempDir.resolve("gen" + seed + ".schema.json");
        Files.writeString(csv, text.toString(), StandardCharsets.UTF_8);
        Files.writeString(schema, GEN_SCHEMA, StandardCharsets.UTF_8);
        return new Dataset(csv, schema, rows, injected);
    }

    private static void appendRecord(StringBuilder text, List<String> fields, Random random, String eol) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) text.append(',');
            String f = fields.get(i);
            boolean quote = f.contains(",") || f.contains("\"") || f.contains("\r") || f.contains("\n")
                    || (!f.isEmpty() && random.nextInt(10) == 0);
            text.append(quote ? '"' + f.replace("\"", "\"\"") + '"' : f);
        }
        // A one-field record that is empty would read as a blank line; the generated ones never are.
        text.append(eol);
    }

    /** A record from the reader below: its fields and the line it starts on. */
    private record CsvRecord(int line, List<String> fields) {
    }

    /** An RFC 4180 reader written from the README: CRLF, LF and CR end a line, BOM and blank lines are skipped. */
    private static List<CsvRecord> readCsv(String text) {
        int pos = text.startsWith("﻿") ? 1 : 0;
        int line = 1;
        List<CsvRecord> records = new ArrayList<>();
        while (pos < text.length()) {
            int start = line;
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quotedAny = false;
            boolean inQuotes = false;
            boolean atFieldStart = true;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (inQuotes) {
                    if (c == '"') {
                        if (pos + 1 < text.length() && text.charAt(pos + 1) == '"') {
                            field.append('"');
                            pos += 2;
                        } else {
                            inQuotes = false;
                            pos++;
                        }
                    } else {
                        if (c == '\n' || (c == '\r' && !(pos + 1 < text.length() && text.charAt(pos + 1) == '\n'))) line++;
                        field.append(c);
                        pos++;
                    }
                    continue;
                }
                if (c == '"' && atFieldStart) {
                    inQuotes = true;
                    quotedAny = true;
                    atFieldStart = false;
                    pos++;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                    atFieldStart = true;
                    pos++;
                } else if (c == '\r' || c == '\n') {
                    pos += (c == '\r' && pos + 1 < text.length() && text.charAt(pos + 1) == '\n') ? 2 : 1;
                    line++;
                    break;
                } else {
                    if (c == '"') throw new IllegalStateException("stray quote at line " + line);
                    field.append(c);
                    atFieldStart = false;
                    pos++;
                }
            }
            if (inQuotes) throw new IllegalStateException("unclosed quote");
            fields.add(field.toString());
            if (fields.size() == 1 && fields.get(0).isEmpty() && !quotedAny) continue;
            records.add(new CsvRecord(start, fields));
        }
        return records;
    }

    /** A typed view of the generated rows, built from what the reader read back. */
    private record Row(int line, long id, String cat, Long n, Double x, String d, Boolean b, String note) {
    }

    private static List<Row> readBack(Dataset data) throws Exception {
        List<CsvRecord> records = readCsv(Files.readString(data.csv(), StandardCharsets.UTF_8));
        List<String> header = records.get(0).fields();
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            Map<String, String> cells = new HashMap<>();
            for (int j = 0; j < header.size(); j++) cells.put(header.get(j), records.get(i).fields().get(j));
            assertEquals(data.rows().get(i - 1), cells, "the test's own CSV writer and reader disagree");
            if (!data.injected().isEmpty()) {
                rows.add(new Row(records.get(i).line(), 0, null, null, null, null, null, null));
                continue;
            }
            rows.add(new Row(records.get(i).line(), Long.parseLong(cells.get("id")), blankToNull(cells.get("cat")),
                    cells.get("n").isEmpty() ? null : Long.parseLong(cells.get("n")),
                    cells.get("x").isEmpty() ? null : Double.parseDouble(cells.get("x")),
                    blankToNull(cells.get("d")),
                    cells.get("b").isEmpty() ? null : Boolean.parseBoolean(cells.get("b")),
                    blankToNull(cells.get("note"))));
        }
        assertEquals(data.rows().size(), rows.size());
        return rows;
    }

    private static String blankToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    @Test
    void generatedDataMatchesAnIndependentOracle() throws Exception {
        long[][] seeds = {{1, 400, 0}, {2, 300, 0}, {3, 250, 1}, {4, 350, 0}, {5, 200, 0}, {6, 400, 1}};
        for (long[] s : seeds) {
            long seed = s[0];
            Dataset data = generate(seed, (int) s[1], s[2] == 1);
            List<Row> rows = readBack(data);
            List<String> modes = seed == 1 || seed == 3 ? MODES : List.of("run");
            for (String mode : modes) {
                String where = mode + " seed " + seed;
                checkValidate(data, rows, mode, where);
                if (!data.injected().isEmpty()) {
                    for (String[] command : List.of(new String[]{"group", "--by", "cat"}, new String[]{"report"},
                            new String[]{"transform"})) {
                        List<String> args = new ArrayList<>(List.of(command[0], data.csv().toString(),
                                "--schema", data.schema().toString()));
                        args.addAll(List.of(command).subList(1, command.length));
                        CliParityTest.Result refused = datakit(mode, args.toArray(new String[0]));
                        assertEquals(3, refused.exitCode(), where + " " + command[0]);
                        assertEquals("", refused.stdout(), where + " " + command[0]);
                    }
                    continue;
                }
                checkGroup(data, rows, mode, "n", where);
                checkGroup(data, rows, mode, "x", where);
                checkReport(data, rows, mode, where);
                checkTransforms(data, rows, mode, where);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkValidate(Dataset data, List<Row> rows, String mode, String where) throws Exception {
        CliParityTest.Result result = datakit(mode, "validate", data.csv().toString(), "--schema", data.schema().toString(),
                "--json");
        boolean valid = data.injected().isEmpty();
        assertEquals(valid ? 0 : 3, result.exitCode(), where + ": " + result.stderr());
        Map<String, Object> json = (Map<String, Object>) StrictJson.parse(result.stdout());
        Set<Integer> badRows = new HashSet<>();
        Set<String> expectedProblems = new HashSet<>();
        for (String cell : data.injected()) {
            int r = Integer.parseInt(cell.substring(0, cell.indexOf('/')));
            badRows.add(r);
            expectedProblems.add(rows.get(r).line() + "/" + cell.substring(cell.indexOf('/') + 1));
        }
        assertEquals(valid, json.get("valid"), where);
        assertEquals(new BigDecimal(rows.size()), json.get("rows"), where);
        assertEquals(new BigDecimal(rows.size() - badRows.size()), json.get("validRows"), where);
        assertEquals(new BigDecimal(badRows.size()), json.get("invalidRows"), where);
        assertEquals(new BigDecimal(data.injected().size()), json.get("problemCount"), where);
        // Each problem names the line its row starts on, and the column.
        Set<String> actual = new HashSet<>();
        for (Map<String, Object> p : (List<Map<String, Object>>) json.get("problems")) {
            int line = ((BigDecimal) p.get("row")).intValueExact();
            assertEquals("line " + line, p.get("place"), where);
            actual.add(line + "/" + p.get("column"));
        }
        assertEquals(expectedProblems, actual, where + ": problems");
        if (valid) {
            CliParityTest.Result text = datakit(mode, "validate", data.csv().toString(), "--schema", data.schema().toString());
            assertEquals("ok: " + rows.size() + " rows, " + COLUMNS.size() + " columns", text.stdout(), where);
        }
    }

    private static int compareKeys(String a, String b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : 1) : -1;
        return a.compareTo(b);
    }

    private static void assertClose(double expected, Object actual, double scale, String where) {
        double a = ((BigDecimal) actual).doubleValue();
        assertTrue(Math.abs(a - expected) <= 1e-9 * Math.max(1.0, scale), where + ": expected " + expected + ", got " + a);
    }

    @SuppressWarnings("unchecked")
    private void checkGroup(Dataset data, List<Row> rows, String mode, String sum, String where) throws Exception {
        CliParityTest.Result result = datakit(mode, "group", data.csv().toString(), "--schema", data.schema().toString(),
                "--by", "cat", "--sum", sum, "--json");
        assertEquals(0, result.exitCode(), where + ": " + result.stderr());
        Map<String, Object> json = (Map<String, Object>) StrictJson.parse(result.stdout());
        assertEquals("cat", json.get("by"), where);
        assertEquals(sum, json.get("sum"), where);
        Map<String, List<Row>> groups = new TreeMap<>(DataKitEndToEndTest::compareKeys);
        for (Row r : rows) groups.computeIfAbsent(r.cat(), k -> new ArrayList<>()).add(r);
        List<Map<String, Object>> actual = (List<Map<String, Object>>) json.get("groups");
        assertEquals(new ArrayList<>(groups.keySet()), actual.stream().map(g -> (String) g.get("key")).toList(),
                where + ": group keys and order");
        int i = 0;
        for (Map.Entry<String, List<Row>> e : groups.entrySet()) {
            Map<String, Object> g = actual.get(i++);
            String at = where + " group " + e.getKey() + " sum " + sum;
            assertEquals(new BigDecimal(e.getValue().size()), g.get("count"), at);
            if (sum.equals("n")) {
                List<Long> values = e.getValue().stream().map(Row::n).filter(v -> v != null).toList();
                assertEquals(new BigDecimal(values.size()), g.get("present"), at);
                if (values.isEmpty()) continue;
                BigInteger total = BigInteger.ZERO;
                for (long v : values) total = total.add(BigInteger.valueOf(v));
                assertEquals(new BigDecimal(total), g.get("sum"), at + ": an exact int sum");
                assertEquals(new BigDecimal(Collections.min(values)), g.get("min"), at);
                assertEquals(new BigDecimal(Collections.max(values)), g.get("max"), at);
                double mean = new BigDecimal(total).divide(new BigDecimal(values.size()), java.math.MathContext.DECIMAL128)
                        .doubleValue();
                assertClose(mean, g.get("mean"), Math.abs(mean), at + " mean");
            } else {
                List<Double> values = e.getValue().stream().map(Row::x).filter(v -> v != null).toList();
                assertEquals(new BigDecimal(values.size()), g.get("present"), at);
                if (values.isEmpty()) continue;
                BigDecimal total = BigDecimal.ZERO;
                double magnitude = 0;
                for (double v : values) {
                    total = total.add(new BigDecimal(Double.toString(v)));
                    magnitude += Math.abs(v);
                }
                assertClose(total.doubleValue(), g.get("sum"), magnitude, at);
                assertEquals(Collections.min(values), ((BigDecimal) g.get("min")).doubleValue(), at);
                assertEquals(Collections.max(values), ((BigDecimal) g.get("max")).doubleValue(), at);
                assertClose(total.doubleValue() / values.size(), g.get("mean"), magnitude / values.size(), at + " mean");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkReport(Dataset data, List<Row> rows, String mode, String where) throws Exception {
        CliParityTest.Result result = datakit(mode, "report", data.csv().toString(), "--schema", data.schema().toString(),
                "--json");
        assertEquals(0, result.exitCode(), where + ": " + result.stderr());
        Map<String, Object> json = (Map<String, Object>) StrictJson.parse(result.stdout());
        assertEquals("gen", json.get("name"), where);
        assertEquals(new BigDecimal(rows.size()), json.get("rows"), where);
        Map<String, Map<String, Object>> columns = new HashMap<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) json.get("columns")) columns.put((String) c.get("name"), c);
        assertEquals(new HashSet<>(COLUMNS), columns.keySet(), where);

        Map<String, List<Object>> values = new LinkedHashMap<>();
        values.put("id", new ArrayList<>(rows.stream().map(r -> (Object) r.id()).toList()));
        values.put("cat", new ArrayList<>(rows.stream().map(r -> (Object) r.cat()).toList()));
        values.put("n", new ArrayList<>(rows.stream().map(r -> (Object) r.n()).toList()));
        values.put("x", new ArrayList<>(rows.stream().map(r -> (Object) r.x()).toList()));
        values.put("d", new ArrayList<>(rows.stream().map(r -> (Object) r.d()).toList()));
        values.put("b", new ArrayList<>(rows.stream().map(r -> (Object) r.b()).toList()));
        values.put("note", new ArrayList<>(rows.stream().map(r -> (Object) r.note()).toList()));
        for (Map.Entry<String, List<Object>> e : values.entrySet()) {
            String at = where + " report " + e.getKey();
            Map<String, Object> c = columns.get(e.getKey());
            List<Object> present = e.getValue().stream().filter(v -> v != null).toList();
            assertEquals(new BigDecimal(present.size()), c.get("present"), at);
            assertEquals(new BigDecimal(rows.size() - present.size()), c.get("missing"), at);
            assertEquals(new BigDecimal(new HashSet<>(present).size()), c.get("distinct"), at);
        }
        for (String name : List.of("id", "n")) {
            List<Long> v = values.get(name).stream().filter(o -> o != null).map(o -> (Long) o).toList();
            BigInteger total = BigInteger.ZERO;
            for (long x : v) total = total.add(BigInteger.valueOf(x));
            Map<String, Object> c = columns.get(name);
            assertEquals(new BigDecimal(Collections.min(v)), c.get("min"), where + " " + name);
            assertEquals(new BigDecimal(Collections.max(v)), c.get("max"), where + " " + name);
            assertEquals(new BigDecimal(total), c.get("sum"), where + " " + name + ": an exact int sum");
        }
        List<Double> xs = values.get("x").stream().filter(o -> o != null).map(o -> (Double) o).toList();
        BigDecimal xTotal = BigDecimal.ZERO;
        double magnitude = 0;
        for (double v : xs) {
            xTotal = xTotal.add(new BigDecimal(Double.toString(v)));
            magnitude += Math.abs(v);
        }
        Map<String, Object> x = columns.get("x");
        assertEquals(Collections.min(xs), ((BigDecimal) x.get("min")).doubleValue(), where + " x min");
        assertEquals(Collections.max(xs), ((BigDecimal) x.get("max")).doubleValue(), where + " x max");
        assertClose(xTotal.doubleValue(), x.get("sum"), magnitude, where + " x sum");

        List<String> dates = values.get("d").stream().filter(o -> o != null).map(o -> (String) o).sorted().toList();
        assertEquals(dates.get(0), columns.get("d").get("earliest"), where);
        assertEquals(dates.get(dates.size() - 1), columns.get("d").get("latest"), where);
        long trues = rows.stream().filter(r -> Boolean.TRUE.equals(r.b())).count();
        long falses = rows.stream().filter(r -> Boolean.FALSE.equals(r.b())).count();
        assertEquals(new BigDecimal(trues), columns.get("b").get("true"), where);
        assertEquals(new BigDecimal(falses), columns.get("b").get("false"), where);

        // String profiles rank by count, then by value.
        Map<String, Integer> counts = new HashMap<>();
        for (Row r : rows) if (r.cat() != null) counts.merge(r.cat(), 1, Integer::sum);
        List<String> ranked = new ArrayList<>(counts.keySet());
        ranked.sort(Comparator.<String>comparingInt(k -> -counts.get(k)).thenComparing(Comparator.naturalOrder()));
        List<Map<String, Object>> top = (List<Map<String, Object>>) columns.get("cat").get("top");
        assertEquals(Math.min(3, ranked.size()), top.size(), where);
        for (int i = 0; i < top.size(); i++) {
            assertEquals(ranked.get(i), top.get(i).get("value"), where + " top " + i);
            assertEquals(new BigDecimal(counts.get(ranked.get(i))), top.get(i).get("count"), where + " top " + i);
        }
    }

    private void checkTransforms(Dataset data, List<Row> rows, String mode, String where) throws Exception {
        // One equality and one numeric comparison; sort by a float, descending.
        List<Row> expected = new ArrayList<>(rows.stream()
                .filter(r -> "beta".equals(r.cat()) && r.n() != null && r.n() >= 0).toList());
        expected.sort(missingLast(Row::x, Comparator.<Double>reverseOrder()));
        checkTransform(data, mode, where + " beta n>=0 x:desc", expected, null,
                "--where", "cat=beta", "--where", "n>=0", "--sort", "x:desc");

        // Many ties: sorting by category must keep file order within each.
        expected = new ArrayList<>(rows.stream().filter(r -> r.n() != null && r.n() < 5).toList());
        expected.sort(missingLast(Row::cat, Comparator.<String>reverseOrder()));
        checkTransform(data, mode, where + " n<5 cat:desc", expected, null,
                "--where", "n<5", "--sort", "cat:desc");

        // Inequality keeps missing values; a limit cuts the sorted list.
        expected = new ArrayList<>(rows.stream().filter(r -> !"delta".equals(r.cat())).toList());
        expected.sort(missingLast(Row::n, Comparator.<Long>naturalOrder()));
        expected = expected.subList(0, Math.min(50, expected.size()));
        checkTransform(data, mode, where + " cat!=delta n limit", expected, List.of("note", "n", "id"),
                "--where", "cat!=delta", "--sort", "n", "--limit", "50", "--select", "note,n,id");

        // An empty value means missing.
        expected = rows.stream().filter(r -> r.d() == null).toList();
        checkTransform(data, mode, where + " d=", expected, null, "--where", "d=");
    }

    private static String lineBreaksAsLf(String s) {
        return s == null ? null : s.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    @SuppressWarnings("unchecked")
    void aLineBreakInsideAQuotedFieldIsKeptAsWritten() throws Exception {
        // RFC 4180: a quoted field holds what is between the quotes, line breaks included.
        Path file = tempDir.resolve("breaks.csv");
        Files.writeString(file, "id,name,dept\r\n1,\"cr\r\nlf\",eng\r\n2,\"lf\nonly\",eng\r\n3,\"cr\ronly\",eng\r\n");
        for (String mode : MODES) {
            List<Map<String, Object>> rows = (List<Map<String, Object>>) StrictJson.parse(
                    datakit(mode, "transform", file.toString(), "--schema", SCHEMA, "--format", "json").stdout());
            assertEquals(List.of("cr\r\nlf", "lf\nonly", "cr\ronly"), rows.stream().map(r -> r.get("name")).toList(), mode);
        }
    }

    private static <T> Comparator<Row> missingLast(java.util.function.Function<Row, T> key, Comparator<T> order) {
        return (a, b) -> {
            T x = key.apply(a);
            T y = key.apply(b);
            if (x == null || y == null) return x == null ? (y == null ? 0 : 1) : -1;
            return order.compare(x, y);
        };
    }

    @SuppressWarnings("unchecked")
    private void checkTransform(Dataset data, String mode, String where, List<Row> expected, List<String> select,
                                String... options) throws Exception {
        List<String> args = new ArrayList<>(List.of("transform", data.csv().toString(), "--schema", data.schema().toString(),
                "--format", "json"));
        args.addAll(List.of(options));
        CliParityTest.Result result = datakit(mode, args.toArray(new String[0]));
        assertEquals(0, result.exitCode(), where + ": " + result.stderr());
        List<Map<String, Object>> actual = (List<Map<String, Object>>) StrictJson.parse(result.stdout());
        assertEquals(expected.stream().map(Row::id).toList(),
                actual.stream().map(o -> ((BigDecimal) o.get("id")).longValueExact()).toList(), where + ": row order");
        for (int i = 0; i < expected.size(); i++) {
            Row r = expected.get(i);
            Map<String, Object> o = actual.get(i);
            String at = where + " id " + r.id();
            if (select != null) {
                assertEquals(select, new ArrayList<>(o.keySet()), at + ": --select order");
            } else {
                assertEquals(new HashSet<>(COLUMNS), o.keySet(), at);
                assertEquals(r.cat(), o.get("cat"), at);
                assertEquals(r.x(), o.get("x") == null ? null : ((BigDecimal) o.get("x")).doubleValue(), at);
                assertEquals(r.d(), o.get("d"), at);
                assertEquals(r.b(), o.get("b"), at);
            }
            assertEquals(r.n() == null ? null : new BigDecimal(r.n()), o.get("n"), at);
            // Line breaks are compared loosely here so that the rest of the oracle
            // still runs; aLineBreakInsideAQuotedFieldIsKeptAsWritten checks them exactly.
            assertEquals(lineBreaksAsLf(r.note()), lineBreaksAsLf((String) o.get("note")), at + ": a quoted field");
        }
    }

    @Test
    void aGeneratedFileGivesTheSameResultOnBothBackends() throws Exception {
        Dataset data = generate(11, 300, false);
        Dataset bad = generate(12, 200, true);
        for (String[] args : List.of(
                new String[]{"report", data.csv().toString(), "--schema", data.schema().toString()},
                new String[]{"group", data.csv().toString(), "--schema", data.schema().toString(), "--by", "d", "--sum", "x"},
                new String[]{"transform", data.csv().toString(), "--schema", data.schema().toString(), "--sort", "note"},
                new String[]{"validate", bad.csv().toString(), "--schema", bad.schema().toString()})) {
            CliParityTest.Result compiled = datakit("run", args);
            CliParityTest.Result interpreted = datakit("interpret", args);
            assertEquals(compiled, interpreted, String.join(" ", args));
        }
    }

    private CliParityTest.Result datakit(String mode, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(mode);
        if (mode.equals("interpret")) command.add("src/main.siyo");
        command.addAll(List.of(args));
        return CliParityTest.siyoc(PROJECT, C_LOCALE, command.toArray(new String[0]));
    }
}
