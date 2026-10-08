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
