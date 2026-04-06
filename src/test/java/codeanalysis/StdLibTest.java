package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for std/ standard library modules.
 * Verifies each function works on both bytecode and interpreter paths.
 */
class StdLibTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("siyo-stdlib-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up temp files
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stdLibTestData")
    void compiledOutputMatchesInterpreter(String name, String source) throws Exception {
        String interpreterOutput = runWithInterpreter(source);
        String compilerOutput = runCompiled(source, "StdTest_" + name);

        assertEquals(interpreterOutput, compilerOutput,
                "Output mismatch for: " + name + "\nInterpreter: " + interpreterOutput + "\nCompiler: " + compilerOutput);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stdLibTestData")
    void interpreterProducesExpectedOutput(String name, String source) throws Exception {
        String output = runWithInterpreter(source);
        assertFalse(output.isEmpty(), "No output for: " + name);
    }

    private String runWithInterpreter(String source) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            SyntaxTree tree = SyntaxTree.parse(source);
            ModuleRegistry registry = new ModuleRegistry();
            Compilation compilation = new Compilation(tree, registry, null);
            HashMap<VariableSymbol, Object> variables = new HashMap<>();
            EvaluationResult result = compilation.evaluate(variables);

            if (result._diagnostics.size() > 0) {
                fail("Interpreter diagnostics: " + result._diagnostics.get(0).getMessage());
            }
        } finally {
            System.setOut(oldOut);
        }

        return baos.toString().trim();
    }

    private String runCompiled(String source, String className) throws Exception {
        SyntaxTree tree = SyntaxTree.parse(source);
        ModuleRegistry registry = new ModuleRegistry();
        Compilation compilation = new Compilation(tree, registry, null);
        byte[] bytecode = compilation.compile(className);

        if (bytecode == null) {
            fail("Compilation failed for: " + className);
        }

        // Load and run with module support
        final byte[] mainBytes = bytecode;
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, mainBytes, 0, mainBytes.length);
                }
                for (ModuleSymbol module : registry.getAllModules()) {
                    if (name.equals(module.getClassName())) {
                        java.util.Map<FunctionSymbol, codeanalysis.binding.BoundBlockStatement> loweredBodies = new java.util.HashMap<>();
                        for (var entry : module.getFunctionBodies().entrySet()) {
                            loweredBodies.put(entry.getKey(), codeanalysis.lowering.Lowerer.lower(entry.getValue()));
                        }
                        codeanalysis.emitting.Emitter depEmitter = new codeanalysis.emitting.Emitter(
                                new codeanalysis.binding.BoundBlockStatement(new java.util.ArrayList<>()),
                                loweredBodies);
                        byte[] depBytes = depEmitter.emit(module.getClassName());
                        return defineClass(name, depBytes, 0, depBytes.length);
                    }
                }
                return super.findClass(name);
            }
        };

        Thread.currentThread().setContextClassLoader(loader);
        Class<?> cls = loader.loadClass(className);

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } finally {
            System.setOut(oldOut);
        }

        return baos.toString().trim();
    }

    static Object[][] stdLibTestData() {
        String tmpDir = System.getProperty("java.io.tmpdir").replace("\\", "/");
        String testFile = tmpDir + "/siyo_stdlib_test.txt";

        return new Object[][] {
            // std/math
            {"MathAbs", "import \"std/math\"\nprintln(toString(math.abs(-42)))\nprintln(toString(math.abs(7)))"},
            {"MathMinMax", "import \"std/math\"\nprintln(toString(math.min(3, 7)))\nprintln(toString(math.max(3, 7)))"},
            {"MathFloorCeil", "import \"std/math\"\nprintln(toString(math.floor(3.7)))\nprintln(toString(math.ceil(3.2)))"},
            {"MathPow", "import \"std/math\"\nprintln(toString(math.pow(2.0, 10.0)))"},
            {"MathSqrt", "import \"std/math\"\nprintln(toString(math.sqrt(144.0)))"},

            // std/strings
            {"StringsJoin", "import \"std/strings\"\nprintln(strings.join([\"a\", \"b\", \"c\"], \"-\"))"},
            {"StringsRepeat", "import \"std/strings\"\nprintln(strings.repeat(\"ab\", 3))"},
            {"StringsPadLeft", "import \"std/strings\"\nprintln(strings.padLeft(\"5\", 4, \"0\"))"},
            {"StringsPadRight", "import \"std/strings\"\nprintln(strings.padRight(\"hi\", 6, \".\"))"},
            {"StringsLines", "import \"std/strings\"\nmut ls = strings.lines(\"one\\ntwo\\nthree\")\nprintln(toString(len(ls)))"},

            // std/io (uses temp files)
            {"IoWriteRead", "import \"std/io\"\nio.writeFile(\"" + testFile + "\", \"hello\")\nprintln(io.readFile(\"" + testFile + "\"))"},
            {"IoFileExists", "import \"std/io\"\nio.writeFile(\"" + testFile + "\", \"x\")\nprintln(toString(io.fileExists(\"" + testFile + "\")))"},
            {"IoReadLines", "import \"std/io\"\nio.writeFile(\"" + testFile + "\", \"a\\nb\\nc\")\nmut ls = io.readLines(\"" + testFile + "\")\nprintln(toString(len(ls)))"},
            {"IoAppend", "import \"std/io\"\nio.writeFile(\"" + testFile + "\", \"first\")\nio.appendFile(\"" + testFile + "\", \"second\")\nprintln(io.readFile(\"" + testFile + "\"))"},

            // std/os
            {"OsCwd", "import \"std/os\"\nprintln(toString(len(os.cwd()) > 0))"},
            {"OsEnv", "import \"std/os\"\nprintln(toString(len(os.env(\"PATH\")) > 0))"},

            // std/net (get/post delegate to builtins — skip network tests, test that module loads)
            {"NetModuleLoad", "import \"std/net\"\nprintln(\"loaded\")"},

            // std/json
            {"JsonParse", "import \"std/json\"\nmut ob = chr(123)\nmut cb = chr(125)\nmut q = chr(34)\nmut input = ob + q + \"name\" + q + \": \" + q + \"Siyo\" + q + cb\nmut obj = json.parse(input)\nprintln(toString(obj.get(\"name\")))"},
            {"JsonStringify", "import \"std/json\"\nmut m = map()\nm.set(\"a\", 1)\nprintln(json.stringify(m))"},
            {"JsonRoundTrip", "import \"std/json\"\nmut ob = chr(123)\nmut cb = chr(125)\nmut q = chr(34)\nmut input = ob + q + \"x\" + q + \": 42\" + cb\nmut s = json.stringify(json.parse(input))\nprintln(contains(s, \"42\"))"},

            // std/testing
            {"TestAssert", "import \"std/testing\"\ntesting.assert(true, \"ok\")\nprintln(\"passed\")"},
            {"TestAssertEqual", "import \"std/testing\"\ntesting.assertEqual(\"a\", \"a\", \"eq\")\nprintln(\"passed\")"},
            {"TestTestFn", "import \"std/testing\"\ntesting.test(\"add\", fn() -> bool { return 1 + 1 == 2 })\nprintln(\"done\")"},
            {"TestTestFail", "import \"std/testing\"\ntesting.test(\"bad\", fn() -> bool { return false })\nprintln(\"done\")"},
        };
    }
}
