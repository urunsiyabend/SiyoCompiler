package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import codeanalysis.project.SiyoProject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every shipped example compiles and its class passes JVM verification.
 *
 * <p>Examples are advertised in the README, so a broken one is a broken promise.
 * {@code examples/web_server.siyo} was unrunnable across at least two releases —
 * it failed verification before its first instruction — because nothing checked
 * them.
 *
 * <p>The class is defined but not invoked: several examples start servers or
 * read stdin. Defining is enough to run the bytecode verifier, which is where
 * that class of defect surfaced.
 */
class ExamplesSmokeTest {

    @TestFactory
    Stream<DynamicTest> everyExampleCompilesAndVerifies() throws Exception {
        Path examples = Path.of("examples");
        if (!Files.isDirectory(examples)) return Stream.empty();

        List<Path> sources = new ArrayList<>();
        try (var stream = Files.list(examples)) {
            stream.filter(path -> path.toString().endsWith(".siyo"))
                    .filter(ExamplesSmokeTest::needsNoExternalJar)
                    .sorted()
                    .forEach(sources::add);
        }

        return sources.stream().map(source -> DynamicTest.dynamicTest(
                source.getFileName().toString(), () -> compileAndVerify(source)));
    }

    /**
     * Examples that import a driver from an external JAR are documented as
     * needing {@code siyoc -cp}, so they cannot compile in a bare test run.
     */
    private static boolean needsNoExternalJar(Path source) {
        try {
            return !Files.readString(source).contains("import java \"org.sqlite");
        } catch (Exception e) {
            return true;
        }
    }

    private void compileAndVerify(Path source) throws Exception {
        SiyoProject.setCurrent(null);
        String text = Files.readString(source);
        SyntaxTree tree = SyntaxTree.parse(text);
        if (tree.diagnostics().hasNext()) {
            fail("Parse error: " + tree.diagnostics().get(0).getMessage());
        }

        String fileName = source.getFileName().toString();
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        String className = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        if (className.equals("Main")) className = "Siyo_Main";

        Compilation compilation = new Compilation(
                tree, new ModuleRegistry(), source.toAbsolutePath().toString());
        byte[] bytecode = compilation.compile(className);
        if (bytecode == null) {
            DiagnosticBox diagnostics = compilation.getGlobalScope().getDiagnostics();
            String message = diagnostics.hasNext()
                    ? diagnostics.get(0).getMessage()
                    : compilation.getEmitDiagnostics() != null
                    ? compilation.getEmitDiagnostics().get(0).getMessage()
                    : "unknown error";
            fail("Compilation failed: " + message);
        }

        // Defining the class runs the bytecode verifier, which is what caught
        // nothing for two releases.
        final String name = className;
        final byte[] bytes = bytecode;
        ClassLoader loader = new ClassLoader(ExamplesSmokeTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (requested.equals(name)) return defineClass(requested, bytes, 0, bytes.length);
                for (ModuleSymbol module : compilation.getRegistry().getAllModules()) {
                    if (!requested.equals(module.getClassName())) continue;
                    var bodies = new java.util.HashMap<FunctionSymbol, codeanalysis.binding.BoundBlockStatement>();
                    for (var entry : module.getFunctionBodies().entrySet()) {
                        bodies.put(entry.getKey(), codeanalysis.lowering.Lowerer.lower(entry.getValue()));
                    }
                    var topLevel = module.getTopLevelBlock() != null
                            ? module.getTopLevelBlock()
                            : new codeanalysis.binding.BoundBlockStatement(new ArrayList<>());
                    var emitter = new codeanalysis.emitting.Emitter(topLevel, bodies);
                    emitter.setModuleClass(true);
                    emitter.setImportedModuleClasses(module.getImportedClassNames());
                    byte[] moduleBytes = emitter.emit(module.getClassName());
                    return defineClass(requested, moduleBytes, 0, moduleBytes.length);
                }
                return super.findClass(requested);
            }
        };

        // getMethod forces the class to be linked and verified.
        Class<?> loaded = loader.loadClass(className);
        assertNotNull(loaded.getMethod("main", String[].class));
    }
}
