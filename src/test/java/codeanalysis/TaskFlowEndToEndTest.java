package codeanalysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs TaskFlow (projects/taskflow) through the real CLI on both backends under
 * a C locale. Fixed fixtures are checked against goldens; generated graphs are
 * checked by rules worked out here, independently of the application, so a bug
 * in the planner cannot be hidden by a golden recorded from it.
 */
class TaskFlowEndToEndTest {
    private static final Path PROJECT = Path.of("projects", "taskflow").toAbsolutePath();
    private static final Path EXPECTED = PROJECT.resolve("fixtures").resolve("expected");
    private static final Map<String, String> C_LOCALE = Map.of("LC_ALL", "C", "LANG", "C");
    private static final List<String> MODES = List.of("run", "interpret");

    private static final String RELEASE = "fixtures/release.json";
    private static final String TASKS = "fixtures/tasks_app.json";
    private static final String DEPS = "fixtures/deps_app.json";
    private static final String BAD = "fixtures/adversarial/";

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("taskflow-e2e");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private record Case(String golden, int exit, String... args) {
    }

    private static final List<Case> GOLDEN = List.of(
            new Case("validate_release.txt", 0, "validate", RELEASE),
            new Case("validate_release.json", 0, "validate", RELEASE, "--json"),
            new Case("cycles_release.txt", 0, "cycles", RELEASE),
            new Case("plan_release.txt", 0, "plan", RELEASE),
            new Case("plan_release_workers.txt", 0, "plan", RELEASE, "--workers", "2"),
            new Case("plan_release_target.txt", 0, "plan", RELEASE, "--target", "tests", "--workers", "1"),
            new Case("plan_release.json", 0, "plan", RELEASE, "--json"),
            new Case("status_release.txt", 0, "status", RELEASE),
            new Case("status_release.json", 0, "status", RELEASE, "--json"),
            new Case("plan_app.txt", 0, "plan", TASKS, DEPS, "--workers", "2"),
            new Case("status_app.txt", 0, "status", TASKS, DEPS),
            new Case("status_app.json", 0, "status", TASKS, DEPS, "--json"),
            new Case("plan_empty.txt", 0, "plan", "fixtures/empty.json"),
            new Case("status_empty.txt", 0, "status", "fixtures/empty.json"),
            new Case("status_done.txt", 0, "status", "fixtures/done_all.json"),
            new Case("cycles_malformed.txt", 3, "cycles", BAD + "malformed.json"),
            new Case("cycles_malformed.json", 3, "cycles", BAD + "malformed.json", "--json"),
            new Case("plan_failed_target.txt", 0, "plan", BAD + "failed_chain.json", "--target", "root1"),
            new Case("validate_cycles.txt", 3, "validate", BAD + "cycles.json"),
            new Case("validate_cycles.json", 3, "validate", BAD + "cycles.json", "--json"),
            new Case("cycles_cycles.txt", 3, "cycles", BAD + "cycles.json"),
            new Case("cycles_cycles.json", 3, "cycles", BAD + "cycles.json", "--json"),
            new Case("validate_invalid.txt", 3, "validate", BAD + "invalid.json"),
            new Case("validate_invalid.json", 3, "validate", BAD + "invalid.json", "--json"),
            new Case("validate_trailing_comma.txt", 3, "validate", BAD + "trailing_comma.json"),
            new Case("validate_duplicate_key.txt", 3, "validate", BAD + "duplicate_key.json"),
            new Case("validate_duplicate_field.txt", 3, "validate", BAD + "duplicate_field.json"),
            new Case("validate_leading_zero.txt", 3, "validate", BAD + "leading_zero.json"),
            new Case("validate_scalar.txt", 3, "validate", BAD + "scalar.json"),
            new Case("validate_blank.txt", 3, "validate", BAD + "blank.json"),
            new Case("validate_trailing_garbage.txt", 3, "validate", BAD + "trailing_garbage.json"),
            new Case("validate_wrong_sections.txt", 3, "validate", BAD + "wrong_sections.json"),
            new Case("validate_clash.txt", 3, "validate", BAD + "clash_a.json", BAD + "clash_b.json"),
            new Case("plan_bom.txt", 0, "plan", BAD + "bom.json"),
            new Case("status_hostile.txt", 0, "status", BAD + "hostile_text.json"),
            new Case("plan_hostile.json", 0, "plan", BAD + "hostile_text.json", "--json"),
            new Case("status_failed.txt", 0, "status", BAD + "failed_chain.json"),
            new Case("plan_failed.json", 0, "plan", BAD + "failed_chain.json", "--json"),
            new Case("plan_ties.txt", 0, "plan", BAD + "ties.json", "--workers", "2"));

    @TestFactory
    Stream<DynamicTest> everyFixtureMatchesItsGoldenOnBothBackends() {
        return GOLDEN.stream().map(c -> DynamicTest.dynamicTest(c.golden(), () -> {
            String expected = Files.readString(EXPECTED.resolve(c.golden()), StandardCharsets.UTF_8).trim();
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, c.args());
                assertEquals(c.exit(), result.exitCode(), mode + ": " + result.stderr());
                assertEquals(expected, result.stdout(), mode + " " + String.join(" ", c.args()));
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
            for (Path test : tests.filter(p -> p.toString().endsWith("_test.siyo")).sorted().toList()) {
                CliParityTest.Result result = CliParityTest.siyoc(PROJECT, C_LOCALE, "interpret", test.toString());
                assertEquals(0, result.exitCode(), test + ": " + result.stdout() + result.stderr());
                assertTrue(result.stdout().contains(" passed, 0 failed"), test + ": " + result.stdout());
            }
        }
    }

    // --- errors --------------------------------------------------------------

    @Test
    void planAndStatusRefuseInvalidInputOnStandardError() throws Exception {
        for (String command : List.of("plan", "status")) {
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, command, BAD + "cycles.json");
                assertEquals(3, result.exitCode(), mode);
                assertEquals("", result.stdout(), mode);
                assertEquals("cycle: a -> b -> c -> a\ncycle: p -> q -> p\ncycle: self -> self\n"
                        + "taskflow: invalid input, nothing planned", result.stderr(), mode);
            }
        }
    }

    @Test
    void anUnreadableFileExitsTwo() throws Exception {
        for (String mode : MODES) {
            CliParityTest.Result missing = taskflow(mode, "validate", "fixtures/missing.json");
            assertEquals(2, missing.exitCode(), mode);
            assertEquals("taskflow: cannot read fixtures/missing.json", missing.stderr(), mode);
            CliParityTest.Result directory = taskflow(mode, "plan", "fixtures");
            assertEquals(2, directory.exitCode(), mode);
            assertEquals("taskflow: cannot read fixtures", directory.stderr(), mode);
        }
    }

    @Test
    void usageErrorsExitOneWithUsage() throws Exception {
        for (String[] args : List.of(new String[]{"fly"}, new String[]{"plan"},
                new String[]{"plan", RELEASE, "--workers", "0"}, new String[]{"plan", RELEASE, "--workers", "12x"},
                new String[]{"status", RELEASE, "--target", "x"}, new String[]{"plan", RELEASE, "--target", "--json"})) {
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, args);
                assertEquals(1, result.exitCode(), mode + String.join(" ", args));
                assertEquals("", result.stdout(), mode);
                assertTrue(result.stderr().contains("\nusage: taskflow"), mode + ": " + result.stderr());
            }
        }
    }

    @Test
    void anUnknownTargetIsInvalidInput() throws Exception {
        for (String mode : MODES) {
            CliParityTest.Result result = taskflow(mode, "plan", RELEASE, "--target", "nope");
            assertEquals(3, result.exitCode(), mode);
            assertEquals("taskflow: unknown target task \"nope\"", result.stderr(), mode);
        }
    }

    @Test
    void helpSucceeds() throws Exception {
        for (String mode : MODES) {
            CliParityTest.Result result = taskflow(mode, "help");
            assertEquals(0, result.exitCode(), mode);
            assertTrue(result.stdout().startsWith("usage: taskflow <command>"), mode);
        }
    }

    // --- hostile input -------------------------------------------------------

    @Test
    void textOutputCarriesNoControlCharacterFromTheInput() throws Exception {
        for (String mode : MODES) {
            for (String[] args : List.of(new String[]{"status", BAD + "hostile_text.json"},
                    new String[]{"plan", BAD + "hostile_text.json"},
                    new String[]{"validate", BAD + "hostile_invalid.json"},
                    new String[]{"cycles", BAD + "hostile_invalid.json"})) {
                String command = String.join(" ", args);
                String out = taskflow(mode, args).stdout();
                assertTrue(out.length() > 5, mode + " " + command);
                for (char c : out.toCharArray()) {
                    assertFalse((c < 0x20 && c != '\n') || (c >= 0x7f && c < 0xa0),
                            mode + " " + command + " wrote U+" + Integer.toHexString(c));
                }
            }
        }
    }

    @Test
    void errorMessagesCarryNoControlCharacters() throws Exception {
        String esc = "\u001b[31m";
        for (String mode : MODES) {
            for (String[] args : List.of(new String[]{"plan", RELEASE, "-" + esc}, new String[]{esc},
                    new String[]{"plan", RELEASE, "--workers", esc}, new String[]{"plan", RELEASE, "--target", esc},
                    new String[]{"plan", "missing" + esc + ".json"})) {
                CliParityTest.Result result = taskflow(mode, args);
                assertFalse(result.stderr().contains("\u001b"), mode + " " + String.join(" ", args));
                assertTrue(result.stderr().contains("\\u001b[31m"), mode + ": " + result.stderr());
                assertTrue(result.exitCode() != 0, mode);
            }
        }
    }

    @Test
    void aHostileProjectStaysRoundTrippableInJson() throws Exception {
        for (String mode : MODES) {
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) StrictJson.parse(
                    taskflow(mode, "plan", BAD + "hostile_text.json", "--json").stdout());
            assertEquals("evil\u001b[31m", plan.get("project"), mode);
        }
    }

    @Test
    void aDocumentNestedTooDeeplyIsReportedNotACrash() throws Exception {
        Path deep = tempDir.resolve("deep.json");
        Files.writeString(deep, "[".repeat(300_000));
        for (String mode : MODES) {
            CliParityTest.Result result = taskflow(mode, "validate", deep.toString());
            assertEquals(3, result.exitCode(), mode + ": " + result.stderr());
            assertTrue(result.stdout().contains("json: nested too deeply"), mode + ": " + result.stdout());
        }
    }

    @Test
    void invalidUtf8DoesNotCrash() throws Exception {
        Path file = tempDir.resolve("latin1.json");
        Files.write(file, new byte[]{'[', '{', '"', 'i', 'd', '"', ':', '"', (byte) 0xff, (byte) 0xc0, '"', '}', ']'});
        for (String mode : MODES) {
            assertEquals(0, taskflow(mode, "validate", file.toString()).exitCode(), mode);
        }
    }

    @Test
    void estimatesThatOverflowAnIntStillAddUp() throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 3000; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"t").append(i).append("\",\"estimate\":1000000}");
        }
        Path file = tempDir.resolve("big.json");
        Files.writeString(file, json.append(']').toString());
        for (String mode : MODES) {
            String out = taskflow(mode, "status", file.toString()).stdout();
            assertTrue(out.contains("hours: 0 done, 3000000000 left"), mode + ": " + out);
        }
    }

    // --- deep and wide -------------------------------------------------------

    @Test
    void aLongChainPlansInEitherDirection() throws Exception {
        int n = 10_000;
        // Ascending: each task needs the one before. Descending: the first task
        // visited needs the next, so a recursive walk goes n calls deep.
        for (boolean ascending : List.of(true, false)) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0) json.append(',');
                int dep = ascending ? i - 1 : i + 1;
                json.append("{\"id\":\"").append(id(i)).append('"');
                if (dep >= 0 && dep < n) json.append(",\"deps\":[\"").append(id(dep)).append("\"]");
                json.append('}');
            }
            Path file = tempDir.resolve("chain" + ascending + ".json");
            Files.writeString(file, json.append(']').toString());
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, "plan", file.toString());
                assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
                assertTrue(result.stdout().contains("batch " + n + ":"), mode);
                assertTrue(result.stdout().contains("critical path (" + n + "h)"), mode);
            }
        }
    }

    @Test
    void aLongCycleIsFoundWhole() throws Exception {
        int n = 5000;
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"").append(id(i)).append("\",\"deps\":[\"").append(id((i + 1) % n)).append("\"]}");
        }
        Path file = tempDir.resolve("ring.json");
        Files.writeString(file, json.append(']').toString());
        for (String mode : MODES) {
            CliParityTest.Result result = taskflow(mode, "cycles", file.toString(), "--json");
            assertEquals(3, result.exitCode(), mode + ": " + result.stderr());
            List<?> cycles = (List<?>) ((Map<?, ?>) StrictJson.parse(result.stdout())).get("cycles");
            assertEquals(1, cycles.size(), mode);
            assertEquals(n + 1, ((List<?>) cycles.get(0)).size(), mode);
        }
    }

    @Test
    void aWideGraphWithAWorkerLimitPlans() throws Exception {
        int n = 5000;
        StringBuilder json = new StringBuilder("[");
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < n; i++) {
            json.append("{\"id\":\"").append(id(i)).append("\"},");
            all.append(i > 0 ? "," : "").append('"').append(id(i)).append('"');
        }
        json.append("{\"id\":\"zz\",\"deps\":[").append(all).append("]}]");
        Path file = tempDir.resolve("wide.json");
        Files.writeString(file, json.toString());
        for (String mode : MODES) {
            String out = taskflow(mode, "plan", file.toString(), "--workers", "3").stdout();
            assertTrue(out.contains("batch 1668:\n  zz (1h)"), mode);
            assertTrue(out.contains("critical path (2h): " + id(0) + " -> zz"), mode);
        }
    }

    // --- rules checked independently of the application ------------------------

    private record Generated(String id, String status, int priority, int estimate, List<String> deps) {
    }

    private static List<Generated> randomGraph(long seed, int n, double density, boolean cyclic) {
        Random random = new Random(seed);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(id(i));
        Collections.shuffle(ids, random);
        String[] statuses = {"todo", "todo", "todo", "doing", "done", "failed"};
        List<Generated> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<String> deps = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                boolean allowed = cyclic ? j != i || random.nextInt(20) == 0 : j < i;
                if (allowed && random.nextDouble() < density) deps.add(ids.get(j));
            }
            tasks.add(new Generated(ids.get(i), statuses[random.nextInt(statuses.length)],
                    random.nextInt(7) - 3, random.nextInt(9), deps));
        }
        return tasks;
    }

    private static String taskJson(Generated t) {
        StringBuilder deps = new StringBuilder();
        for (String d : t.deps()) deps.append(deps.isEmpty() ? "" : ",").append('"').append(d).append('"');
        return "{\"id\":\"" + t.id() + "\",\"status\":\"" + t.status() + "\",\"priority\":" + t.priority()
                + ",\"estimate\":" + t.estimate() + ",\"deps\":[" + deps + "]}";
    }

    private Path writeTasks(String name, List<Generated> tasks) throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (Generated t : tasks) json.append(json.length() > 1 ? "," : "").append(taskJson(t));
        Path file = tempDir.resolve(name);
        Files.writeString(file, json.append(']').toString());
        return file;
    }

    @Test
    void generatedPlansObeyEveryRule() throws Exception {
        for (long seed = 1; seed <= 6; seed++) {
            List<Generated> tasks = randomGraph(seed, 60, 0.05, false);
            Path file = writeTasks("g" + seed + ".json", tasks);
            int workers = (int) seed;
            String expected = null;
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, "plan", file.toString(), "--json", "--workers", "" + workers);
                assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
                if (expected == null) expected = result.stdout();
                assertEquals(expected, result.stdout(), "backends differ, seed " + seed);
            }
            checkPlan(tasks, workers, expected, "seed " + seed);
        }
    }

    @Test
    void generatedTargetPlansObeyEveryRule() throws Exception {
        for (long seed = 21; seed <= 24; seed++) {
            List<Generated> tasks = randomGraph(seed, 50, 0.08, false);
            Generated target = tasks.get(tasks.size() - 1);
            Map<String, Generated> byId = new HashMap<>();
            for (Generated t : tasks) byId.put(t.id(), t);
            // What the target needs, not looking through work already done.
            Set<String> scope = new HashSet<>();
            List<String> stack = new ArrayList<>(List.of(target.id()));
            while (!stack.isEmpty()) {
                String v = stack.remove(stack.size() - 1);
                if (scope.add(v) && !byId.get(v).status().equals("done")) stack.addAll(byId.get(v).deps());
            }
            List<Generated> inScope = new ArrayList<>();
            for (Generated t : tasks) if (scope.contains(t.id())) inScope.add(t);
            Path file = writeTasks("t" + seed + ".json", tasks);
            String expected = null;
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, "plan", file.toString(), "--json", "--target", target.id());
                assertEquals(0, result.exitCode(), mode + ": " + result.stderr());
                if (expected == null) expected = result.stdout();
                assertEquals(expected, result.stdout(), "backends differ, seed " + seed);
            }
            checkPlan(inScope, Integer.MAX_VALUE, expected, "target, seed " + seed);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkPlan(List<Generated> tasks, int workers, String json, String where) {
        Map<String, Object> plan = (Map<String, Object>) StrictJson.parse(json);
        Map<String, Generated> byId = new HashMap<>();
        for (Generated t : tasks) byId.put(t.id(), t);

        // What must run: not done, not failed, and nothing it needs is failed or blocked.
        Set<String> blocked = new HashSet<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Generated t : tasks) {
                if (blocked.contains(t.id()) || t.status().equals("done") || t.status().equals("failed")) continue;
                for (String d : t.deps()) {
                    if (byId.get(d).status().equals("failed") || blocked.contains(d)) {
                        grew = blocked.add(t.id());
                        break;
                    }
                }
            }
        }
        Set<String> mustRun = new HashSet<>();
        long hours = 0;
        for (Generated t : tasks) {
            if ((t.status().equals("todo") || t.status().equals("doing")) && !blocked.contains(t.id())) {
                mustRun.add(t.id());
                hours += t.estimate();
            }
        }

        Set<String> finished = new HashSet<>();
        Map<String, Long> longest = new HashMap<>();
        long longestAll = 0;
        List<List<Map<String, Object>>> batches = (List<List<Map<String, Object>>>) plan.get("batches");
        for (List<Map<String, Object>> batch : batches) {
            assertTrue(batch.size() <= workers, where + ": a batch is over the worker limit");
            assertFalse(batch.isEmpty(), where + ": an empty batch");
            // A batch takes the best-ranked of everything ready: nothing waits needlessly.
            List<Generated> ready = new ArrayList<>();
            for (String id : mustRun) {
                boolean isReady = true;
                for (String d : byId.get(id).deps()) {
                    if (byId.get(d).status().matches("todo|doing") && !finished.contains(d)) isReady = false;
                }
                if (isReady) ready.add(byId.get(id));
            }
            ready.sort(TaskFlowEndToEndTest::rankCompare);
            List<String> wanted = new ArrayList<>();
            for (Generated t : ready.subList(0, Math.min(workers, ready.size()))) wanted.add(t.id());
            List<String> actual = new ArrayList<>();
            for (Map<String, Object> entry : batch) actual.add((String) entry.get("id"));
            assertEquals(wanted, actual, where + ": a batch is not the best of what was ready");
            Generated previous = null;
            for (Map<String, Object> entry : batch) {
                Generated t = byId.get((String) entry.get("id"));
                assertTrue(mustRun.remove(t.id()), where + ": " + t.id() + " should not run, or ran twice");
                for (String d : t.deps()) {
                    assertTrue(!byId.get(d).status().matches("todo|doing") || finished.contains(d),
                            where + ": " + t.id() + " ran before " + d);
                }
                if (previous != null) {
                    assertTrue(rankCompare(previous, t) < 0, where + ": " + previous.id() + " before " + t.id());
                }
                previous = t;
            }
            for (Map<String, Object> entry : batch) {
                Generated t = byId.get((String) entry.get("id"));
                finished.add(t.id());
                long upstream = 0;
                for (String d : t.deps()) upstream = Math.max(upstream, longest.getOrDefault(d, 0L));
                longest.put(t.id(), upstream + t.estimate());
                longestAll = Math.max(longestAll, upstream + t.estimate());
            }
        }
        assertTrue(mustRun.isEmpty(), where + ": never ran " + mustRun);

        assertEquals(new BigDecimal(hours), plan.get("remainingHours"), where + ": hours");
        assertEquals(new BigDecimal(longestAll), plan.get("criticalHours"), where + ": critical hours");
        Set<String> blockedSeen = new HashSet<>();
        for (Map<String, Object> b : (List<Map<String, Object>>) plan.get("blocked")) {
            String id = (String) b.get("id");
            blockedSeen.add(id);
            // Named: the failed tasks reached through unfinished work, stopping at each.
            Set<String> causes = new HashSet<>();
            Set<String> seen = new HashSet<>();
            List<String> stack = new ArrayList<>(byId.get(id).deps());
            while (!stack.isEmpty()) {
                String v = stack.remove(stack.size() - 1);
                if (!seen.add(v)) continue;
                if (byId.get(v).status().equals("failed")) causes.add(v);
                else if (!byId.get(v).status().equals("done")) stack.addAll(byId.get(v).deps());
            }
            assertEquals(causes, new HashSet<>((List<String>) b.get("by")), where + ": why " + id + " is blocked");
        }
        assertEquals(blocked, blockedSeen, where + ": blocked");
        Set<String> failedSeen = new HashSet<>((List<String>) plan.get("failed"));
        Set<String> failed = new HashSet<>();
        for (Generated t : tasks) if (t.status().equals("failed")) failed.add(t.id());
        assertEquals(failed, failedSeen, where + ": failed");

        // The named critical path is a real chain of the length it claims.
        List<String> path = (List<String>) plan.get("criticalPath");
        long sum = 0;
        for (int i = 0; i < path.size(); i++) {
            sum += byId.get(path.get(i)).estimate();
            if (i > 0) assertTrue(byId.get(path.get(i)).deps().contains(path.get(i - 1)), where + ": path has no edge");
        }
        assertEquals(longestAll, sum, where + ": critical path sum");
    }

    private static int rankCompare(Generated a, Generated b) {
        boolean aDoing = a.status().equals("doing");
        boolean bDoing = b.status().equals("doing");
        if (aDoing != bDoing) return aDoing ? -1 : 1;
        if (a.priority() != b.priority()) return b.priority() - a.priority();
        return a.id().compareTo(b.id());
    }

    @Test
    void theOrderOfFilesAndTasksDoesNotChangeTheResult() throws Exception {
        List<Generated> tasks = randomGraph(42, 40, 0.08, false);
        List<Generated> first = tasks.subList(0, 20);
        List<Generated> second = tasks.subList(20, 40);
        Path a = writeTasks("a.json", first);
        Path b = writeTasks("b.json", second);
        List<Generated> reshuffled = new ArrayList<>(tasks);
        Collections.shuffle(reshuffled, new Random(9));
        Path single = writeTasks("one.json", reshuffled);
        for (String mode : MODES) {
            CliParityTest.Result forwardRun = taskflow(mode, "plan", a.toString(), b.toString());
            assertEquals(0, forwardRun.exitCode(), mode + ": " + forwardRun.stderr());
            String forward = forwardRun.stdout();
            assertTrue(forward.contains("batch 1:"), mode + ": " + forward);
            assertEquals(forward, taskflow(mode, "plan", b.toString(), a.toString()).stdout(), mode + ": argument order");
            assertEquals(forward, taskflow(mode, "plan", single.toString()).stdout(), mode + ": task order");
            assertEquals(taskflow(mode, "status", a.toString(), b.toString(), "--json").stdout(),
                    taskflow(mode, "status", single.toString(), "--json").stdout(), mode + ": status");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedCyclesMatchAnIndependentSearch() throws Exception {
        for (long seed = 1; seed <= 8; seed++) {
            List<Generated> tasks = randomGraph(seed * 31, 30, seed % 2 == 0 ? 0.03 : 0.012, true);
            Map<String, List<String>> edges = new HashMap<>();
            for (Generated t : tasks) edges.put(t.id(), t.deps());
            // v and w are in one group when each reaches the other.
            Map<String, Set<String>> reach = new HashMap<>();
            for (String v : edges.keySet()) {
                Set<String> seen = new HashSet<>();
                List<String> stack = new ArrayList<>(edges.get(v));
                while (!stack.isEmpty()) {
                    String w = stack.remove(stack.size() - 1);
                    if (seen.add(w)) stack.addAll(edges.get(w));
                }
                reach.put(v, seen);
            }
            Set<String> groupLeaders = new HashSet<>();
            for (String v : edges.keySet()) {
                if (!reach.get(v).contains(v)) continue;
                String leader = v;
                for (String w : reach.get(v)) {
                    if (reach.get(w).contains(v) && w.compareTo(leader) < 0) leader = w;
                }
                groupLeaders.add(leader);
            }

            Path file = writeTasks("c" + seed + ".json", tasks);
            String expected = null;
            for (String mode : MODES) {
                CliParityTest.Result result = taskflow(mode, "cycles", file.toString(), "--json");
                assertEquals(groupLeaders.isEmpty() ? 0 : 3, result.exitCode(), mode + ": " + result.stderr());
                if (expected == null) expected = result.stdout();
                assertEquals(expected, result.stdout(), "backends differ, seed " + seed);
            }
            List<List<String>> cycles = (List<List<String>>) ((Map<String, Object>) StrictJson.parse(expected)).get("cycles");
            Set<String> starts = new HashSet<>();
            String previousStart = "";
            for (List<String> cycle : cycles) {
                String start = cycle.get(0);
                assertTrue(starts.add(start), "one cycle per group, seed " + seed);
                assertTrue(groupLeaders.contains(start), "a cycle starts at its group's smallest id, seed " + seed);
                assertTrue(start.compareTo(previousStart) > 0, "sorted, seed " + seed);
                previousStart = start;
                assertEquals(cycle.get(cycle.size() - 1), start, "closed, seed " + seed);
                for (int i = 0; i + 1 < cycle.size(); i++) {
                    assertTrue(edges.get(cycle.get(i)).contains(cycle.get(i + 1)), "real edge, seed " + seed);
                    assertTrue(reach.get(start).contains(cycle.get(i)) && reach.get(cycle.get(i)).contains(start), "same group");
                }
            }
            assertEquals(groupLeaders, starts, "every group named, seed " + seed);
        }
    }

    private static String id(int i) {
        return String.format("t%06d", i);
    }

    private CliParityTest.Result taskflow(String mode, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(mode);
        if (mode.equals("interpret")) command.add("src/main.siyo");
        command.addAll(List.of(args));
        return CliParityTest.siyoc(PROJECT, C_LOCALE, command.toArray(new String[0]));
    }
}
