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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the {@code siyoc} command line hands a program, checked for {@code run}
 * and {@code interpret} alike. A CLI tool that sees its arguments compiled and
 * loses them interpreted cannot be debugged on the interpreter.
 */
class CliParityTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-cli");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void interpretPassesProgramArgs() throws Exception {
        Path file = tempDir.resolve("args.siyo");
        Files.writeString(file, """
                import "std/os"
                fn main() {
                    imut a = os.args()
                    println(toString(len(a)))
                    for x in a { println(x) }
                }
                """);
        Result compiled = siyoc("run", file.toString(), "one", "two");
        assertEquals("2\none\ntwo", compiled.stdout, compiled.stderr);
        Result interpreted = siyoc("interpret", file.toString(), "one", "two");
        assertEquals("2\none\ntwo", interpreted.stdout, interpreted.stderr);
    }

    @Test
    void anImportedModulesStateIsInitialisedWhenInterpreted() throws Exception {
        Files.writeString(tempDir.resolve("base.siyo"), """
                pub mut greeting = "hello"
                fn init() { println("init base") }
                """);
        Files.writeString(tempDir.resolve("hooks.siyo"), """
                import "base"
                mut hook = fn() { println("default hook") }
                mut count = 7
                pub fn callHook() { hook() }
                pub fn getCount() -> int { count }
                pub fn setHook(f: fn()) { hook = f }
                pub fn greet() -> string { base.greeting + " " + toString(count) }
                fn init() { count = count + 1
                    println("init hooks") }
                """);
        Path main = tempDir.resolve("main.siyo");
        Files.writeString(main, """
                import "hooks"
                fn main() {
                    println(toString(hooks.getCount()))
                    hooks.callHook()
                    hooks.setHook(fn() { println("custom hook") })
                    hooks.callHook()
                    println(hooks.greet())
                }
                """);
        String expected = "init base\ninit hooks\n8\ndefault hook\ncustom hook\nhello 8";
        Result compiled = siyoc("run", main.toString());
        assertEquals(expected, compiled.stdout, compiled.stderr);
        Result interpreted = siyoc("interpret", main.toString());
        assertEquals(expected, interpreted.stdout, interpreted.stderr);
    }

    @Test
    void aModuleCallsIntoItsOwnImportWhenInterpreted() throws Exception {
        Files.writeString(tempDir.resolve("base.siyo"), """
                pub fn twice(n: int) -> int { n * 2 }
                """);
        Files.writeString(tempDir.resolve("mid.siyo"), """
                import "base"
                import "base" as b
                pub fn quad(n: int) -> int { base.twice(b.twice(n)) }
                """);
        Path main = tempDir.resolve("main.siyo");
        Files.writeString(main, """
                import "mid"
                fn main() { println(toString(mid.quad(3))) }
                """);
        Result compiled = siyoc("run", main.toString());
        assertEquals("12", compiled.stdout, compiled.stderr);
        Result interpreted = siyoc("interpret", main.toString());
        assertEquals("12", interpreted.stdout, interpreted.stderr);
    }

    @Test
    void aSyntaxErrorInAnImportedModuleIsReported() throws Exception {
        Files.writeString(tempDir.resolve("broken.siyo"), """
                pub fn f() -> int { 1 +
                }
                """);
        Path main = tempDir.resolve("main.siyo");
        Files.writeString(main, """
                import "broken"
                fn main() { println(toString(broken.f())) }
                """);
        for (String mode : List.of("run", "interpret")) {
            Result result = siyoc(mode, main.toString());
            assertEquals(1, result.exitCode, mode + ": " + result.stdout);
            assertEquals(true, result.stderr.contains("broken.siyo(2, 1)"), mode + ": " + result.stderr);
        }
    }

    @Test
    void aFailingTestSuiteExitsNonZero() throws Exception {
        Files.createDirectories(tempDir.resolve("tests"));
        Files.writeString(tempDir.resolve("tests/a_test.siyo"), """
                import "std/testing"
                fn fails() { testing.assertEq("1", "2", "one is two") }
                fn main() { testing.run("a", [fails]) }
                """);
        Files.writeString(tempDir.resolve("tests/b_test.siyo"), """
                import "std/testing"
                fn passes() { testing.assertEq("1", "1", "one is one") }
                fn main() { testing.run("b", [passes]) }
                """);
        Result suite = siyoc(tempDir, "test");
        assertEquals(1, suite.exitCode, suite.stdout);
        // Every file still runs: the failure in a_test does not hide b_test.
        assertEquals(true, suite.stdout.contains("=== b ==="), suite.stdout);
        String failing = tempDir.resolve("tests/a_test.siyo").toString();
        assertEquals(1, siyoc("run", failing).exitCode);
        assertEquals(1, siyoc("interpret", failing).exitCode);
        String passing = tempDir.resolve("tests/b_test.siyo").toString();
        assertEquals(0, siyoc("run", passing).exitCode);
        assertEquals(0, siyoc("interpret", passing).exitCode);
    }

    @Test
    void aFailingTestCaseExitsNonZero() throws Exception {
        Path file = tempDir.resolve("one_test.siyo");
        Files.writeString(file, """
                import "std/testing"
                fn main() { testing.test("no", fn() -> bool { false }) }
                """);
        assertEquals(1, siyoc("run", file.toString()).exitCode);
        assertEquals(1, siyoc("interpret", file.toString()).exitCode);
    }

    record Result(int exitCode, String stdout, String stderr) {}

    /** Runs {@code Main} in a fresh JVM, so {@code System.exit} ends only that process. */
    static Result siyoc(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("Main");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        if (cwd != null) builder.directory(cwd.toFile());
        Process process = builder.start();
        byte[] out = process.getInputStream().readAllBytes();
        byte[] err = process.getErrorStream().readAllBytes();
        int code = process.waitFor();
        return new Result(code,
                new String(out, StandardCharsets.UTF_8).trim(),
                new String(err, StandardCharsets.UTF_8).trim());
    }

    private Result siyoc(String... args) throws Exception {
        return siyoc(null, args);
    }
}
