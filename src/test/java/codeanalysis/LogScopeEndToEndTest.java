package codeanalysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the LogScope sample application (projects/logscope) through the real
 * CLI on both backends — {@code siyoc run} in project mode and
 * {@code siyoc interpret src/main.siyo} — and checks every result against the
 * goldens in fixtures/expected. Runs under a C locale so output encoding is
 * part of what is checked.
 */
class LogScopeEndToEndTest {
    private static final Path PROJECT = Path.of("projects", "logscope").toAbsolutePath();
    private static final Path EXPECTED = PROJECT.resolve("fixtures").resolve("expected");
    private static final Map<String, String> C_LOCALE = Map.of("LC_ALL", "C", "LANG", "C");

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("logscope-e2e");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void summaryOfACleanLog() throws Exception {
        assertGolden("summary_app.txt", "summary", "fixtures/app.jsonl");
    }

    @Test
    void summaryOfAMixedLogFilteredToErrorsAndWarnings() throws Exception {
        assertGolden("summary_mixed_errors.txt", "summary", "fixtures/mixed.jsonl", "--level", "error,warn");
    }

    @Test
    void summaryOfATimeWindow() throws Exception {
        assertGolden("summary_window.txt", "summary", "fixtures/app.jsonl",
                "--since", "2026-03-01T10:03:00Z", "--until", "2026-03-01T10:08:00Z");
    }

    @Test
    void summaryOfAnEmptyLog() throws Exception {
        assertGolden("summary_empty.txt", "summary", "fixtures/empty.jsonl");
    }

    @Test
    void filterByMessageText() throws Exception {
        assertGolden("filter_timeout.jsonl", "filter", "fixtures/app.jsonl", "--grep", "timeout");
    }

    @Test
    void filterKeepsEveryValidRecordVerbatim() throws Exception {
        assertGolden("filter_mixed.jsonl", "filter", "fixtures/mixed.jsonl");
    }

    @Test
    void summaryOfAnAdversarialLog() throws Exception {
        assertGolden("summary_adversarial.txt", "summary", "fixtures/adversarial.jsonl");
    }

    @Test
    void aTimeWindowOrdersFractionalSeconds() throws Exception {
        assertGolden("summary_adversarial_window.txt", "summary", "fixtures/adversarial.jsonl",
                "--since", "2026-03-01T10:00:00.3Z", "--until", "2026-03-01T10:00:01Z");
    }

    @Test
    void filterEchoesAdversarialRecordsAsTheSameValidJson() throws Exception {
        assertGolden("filter_adversarial.jsonl", "filter", "fixtures/adversarial.jsonl");
        List<String> input = Files.readAllLines(PROJECT.resolve("fixtures/adversarial.jsonl"), StandardCharsets.UTF_8);
        input.remove(5); // line 6 has a non-UTC timestamp and is reported, not echoed
        for (String mode : List.of("run", "interpret")) {
            List<String> output = logscope(mode, "filter", "fixtures/adversarial.jsonl").stdout().lines().toList();
            assertEquals(input.size(), output.size(), mode);
            for (int i = 0; i < input.size(); i++) {
                assertEquals(StrictJson.parse(input.get(i)), StrictJson.parse(output.get(i)), mode + " line " + (i + 1));
            }
        }
    }

    @Test
    void aJsonReportIsStrictlyValidAndKeepsStringsAsStrings() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            Path json = tempDir.resolve(mode + ".json");
            Path html = tempDir.resolve(mode + ".html");
            CliParityTest.Result result = logscope(mode, "report", "fixtures/adversarial.jsonl",
                    "--json", json.toString(), "--html", html.toString());
            assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
            assertEquals(read(EXPECTED.resolve("report_adversarial.json")), read(json), mode + " json");
            assertEquals(read(EXPECTED.resolve("report_adversarial.html")), read(html), mode + " html");
            @SuppressWarnings("unchecked")
            Map<String, Object> report = (Map<String, Object>) StrictJson.parse(read(json).trim());
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) ((List<Object>) report.get("errorGroups")).get(0);
            assertEquals("123", first.get("message"), mode);
            assertEquals(List.of("01", "api"), first.get("services"), mode);
            assertEquals("2026-03-01T10:00:00Z", first.get("first"), mode);
            assertEquals("2026-03-01T10:00:00.500Z", first.get("last"), mode);
        }
    }

    @Test
    void aFlagIsNeverTakenAsAnOutputPath() throws Exception {
        String main = PROJECT.resolve("src/main.siyo").toString();
        String log = PROJECT.resolve("fixtures/app.jsonl").toString();
        List<List<String>> attempts = List.of(
                List.of("report", log, "--json", "--html"),
                List.of("report", log, "--json", "--html", "out.html"),
                List.of("report", log, "--html", "--json", "out.json"));
        for (String mode : List.of("run", "interpret")) {
            for (List<String> attempt : attempts) {
                List<String> command = new ArrayList<>(List.of(mode, main));
                command.addAll(attempt);
                CliParityTest.Result result = CliParityTest.siyoc(tempDir, C_LOCALE, command.toArray(new String[0]));
                assertEquals(1, result.exitCode(), mode + " " + attempt);
                assertTrue(result.stderr().startsWith("logscope: flag '" + attempt.get(2) + "' needs a value"),
                        mode + " " + attempt + ": " + result.stderr());
                try (Stream<Path> created = Files.list(tempDir)) {
                    assertEquals(List.of(), created.toList(), mode + " " + attempt + " created a file");
                }
            }
        }
    }

    @Test
    void anInvalidTimeFilterIsAUsageError() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, "summary", "fixtures/app.jsonl", "--since", "2026-03-01T10:00:00+02:00");
            assertEquals(1, result.exitCode(), mode);
            assertTrue(result.stderr().startsWith(
                    "logscope: --since needs an ISO-8601 UTC timestamp, got '2026-03-01T10:00:00+02:00'"), mode);
        }
    }

    @Test
    void reportWritesJsonAndHtml() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            Path json = tempDir.resolve(mode + ".json");
            Path html = tempDir.resolve(mode + ".html");
            CliParityTest.Result result = logscope(mode, "report", "fixtures/mixed.jsonl",
                    "--json", json.toString(), "--html", html.toString());
            assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
            assertEquals("wrote " + json + "\nwrote " + html, result.stdout(), mode);
            assertEquals(read(EXPECTED.resolve("report_mixed.json")), read(json), mode + " json");
            assertEquals(read(EXPECTED.resolve("report_mixed.html")), read(html), mode + " html");
        }
    }

    @Test
    void missingFileExitsTwo() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, "summary", "fixtures/missing.jsonl");
            assertEquals(2, result.exitCode(), mode);
            assertEquals("", result.stdout(), mode);
            assertEquals("logscope: cannot read fixtures/missing.jsonl", result.stderr(), mode);
        }
    }

    @Test
    void unwritableReportExitsTwo() throws Exception {
        String target = tempDir.resolve("no-such-dir").resolve("r.json").toString();
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, "report", "fixtures/app.jsonl", "--json", target);
            assertEquals(2, result.exitCode(), mode);
            assertEquals("logscope: cannot write " + target, result.stderr(), mode);
        }
    }

    @Test
    void badFlagExitsOneWithUsage() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, "summary", "fixtures/app.jsonl", "--bogus", "x");
            assertEquals(1, result.exitCode(), mode);
            assertTrue(result.stderr().startsWith("logscope: unknown flag '--bogus'\nusage: logscope"),
                    mode + ": " + result.stderr());
        }
    }

    @Test
    void helpPrintsUsageAndSucceeds() throws Exception {
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, "help");
            assertEquals(0, result.exitCode(), mode);
            assertTrue(result.stdout().startsWith("usage: logscope <command> <file.jsonl>"), mode);
        }
    }

    @Test
    void theProjectsOwnTestsPassOnBothBackends() throws Exception {
        CliParityTest.Result suite = CliParityTest.siyoc(PROJECT, C_LOCALE, "test");
        assertEquals(0, suite.exitCode(), suite.stdout() + suite.stderr());
        assertTrue(suite.stdout().contains(" 0 failed"), suite.stdout());
        try (Stream<Path> tests = Files.list(PROJECT.resolve("tests"))) {
            for (Path test : tests.filter(p -> p.toString().endsWith("_test.siyo")).sorted().toList()) {
                CliParityTest.Result result = CliParityTest.siyoc(PROJECT, C_LOCALE, "interpret", test.toString());
                assertEquals(0, result.exitCode(), test + ": " + result.stdout() + result.stderr());
            }
        }
    }

    /** Asserts both backends print the golden file and exit 0. */
    private void assertGolden(String golden, String... args) throws Exception {
        String expected = read(EXPECTED.resolve(golden)).trim();
        for (String mode : List.of("run", "interpret")) {
            CliParityTest.Result result = logscope(mode, args);
            assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
            assertEquals(expected, result.stdout(), mode + " " + String.join(" ", args));
        }
    }

    private CliParityTest.Result logscope(String mode, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(mode);
        if (mode.equals("interpret")) command.add("src/main.siyo");
        command.addAll(List.of(args));
        return CliParityTest.siyoc(PROJECT, C_LOCALE, command.toArray(new String[0]));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
