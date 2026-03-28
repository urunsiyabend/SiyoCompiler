import codeanalysis.*;
import codeanalysis.syntax.SyntaxTree;

import java.util.*;

/**
 * The Main class is the entry point of the program.
 * It starts a REPL loop that reads input from the user, parses it, and prints the result.
 * The loop terminates when the user enters a blank line.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Main {
    /**
     * Entry point of program.
     * Starts a REPL loop that reads input from the user, parses it, and prints the result.
     * The loop terminates when the user enters a blank line.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        if (System.getenv("SIYO_DEBUG") != null) {
            System.err.println("[debug] args=" + java.util.Arrays.toString(args));
        }
        // Parse -cp flag: siyoc -cp lib.jar run file.siyo
        String classpath = null;
        java.util.List<String> remaining = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("-cp") || args[i].equals("--classpath")) && i + 1 < args.length) {
                classpath = args[++i];
            } else {
                remaining.add(args[i]);
            }
        }

        // Register external classpath for ASM metadata resolution
        if (classpath != null) {
            codeanalysis.JavaClasspath.addClasspath(classpath);
        }

        String[] cargs = remaining.toArray(new String[0]);
        if (cargs.length >= 2 && cargs[0].equals("run")) {
            compileAndRun(cargs[1]); // bytecode default (26x faster)
            return;
        }
        if (cargs.length >= 2 && cargs[0].equals("interpret")) {
            runFile(cargs[1]); // interpreter path (for debugging)
            return;
        }
        if (cargs.length >= 2 && cargs[0].equals("compile")) {
            compileFile(cargs[1]);
            return;
        }
        if (cargs.length >= 2 && (cargs[0].equals("-c") || cargs[0].equals("exec"))) {
            compileAndRun(cargs[1]);
            return;
        }
        if (cargs.length >= 1 && !cargs[0].equals("repl")) {
            runFile(cargs[0]);
            return;
        }
        repl();
    }

    private static void compileAndRun(String path) {
        try {
            String absPath = java.nio.file.Paths.get(path).toAbsolutePath().toString();
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(absPath)));
            SyntaxTree tree = SyntaxTree.parse(source);
            codeanalysis.ModuleRegistry registry = new codeanalysis.ModuleRegistry();
            Compilation compilation = new Compilation(tree, registry, absPath);

            String fileName = java.nio.file.Paths.get(path).getFileName().toString();
            String classNameRaw = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String classNameBase = Character.toUpperCase(classNameRaw.charAt(0)) + classNameRaw.substring(1);
            // Avoid collision with compiler's own Main class
            final String className = classNameBase.equals("Main") ? "Siyo_Main" : classNameBase;

            byte[] bytecode = compilation.compile(className);
            if (bytecode == null) {
                DiagnosticBox diagnostics = tree.diagnostics().addAll(compilation.getGlobalScope().getDiagnostics());
                while (diagnostics.hasNext()) {
                    System.err.println(diagnostics.next());
                }
                System.exit(1);
            }

            // Load and run in-memory (no .class file written)
            final byte[] mainBytes = bytecode;
            ClassLoader loader = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.equals(className)) {
                        return defineClass(name, mainBytes, 0, mainBytes.length);
                    }
                    // Load dependency modules
                    for (codeanalysis.ModuleSymbol module : registry.getAllModules()) {
                        if (name.equals(module.getClassName())) {
                            java.util.Map<codeanalysis.FunctionSymbol, codeanalysis.binding.BoundBlockStatement> loweredBodies = new java.util.HashMap<>();
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

            Class<?> cls = loader.loadClass(className);
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() != null) {
                System.err.println("Error: " + e.getCause().getMessage());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void compileFile(String path) {
        try {
            String absPath = java.nio.file.Paths.get(path).toAbsolutePath().toString();
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(absPath)));
            SyntaxTree tree = SyntaxTree.parse(source);
            codeanalysis.ModuleRegistry registry = new codeanalysis.ModuleRegistry();
            Compilation compilation = new Compilation(tree, registry, absPath);

            String fileName = java.nio.file.Paths.get(path).getFileName().toString();
            String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            className = Character.toUpperCase(className.charAt(0)) + className.substring(1);

            byte[] bytecode = compilation.compile(className);
            if (bytecode == null) {
                DiagnosticBox diagnostics = tree.diagnostics().addAll(compilation.getGlobalScope().getDiagnostics());
                while (diagnostics.hasNext()) {
                    System.err.println(diagnostics.next());
                }
                System.exit(1);
            }

            // Write main .class file
            String outputPath = className + ".class";
            java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), bytecode);
            System.out.println("Compiled to " + outputPath);

            // Write dependency .class files
            for (codeanalysis.ModuleSymbol module : registry.getAllModules()) {
                // Lower function bodies before emitting
                java.util.Map<codeanalysis.FunctionSymbol, codeanalysis.binding.BoundBlockStatement> loweredBodies = new java.util.HashMap<>();
                for (var entry : module.getFunctionBodies().entrySet()) {
                    loweredBodies.put(entry.getKey(), codeanalysis.lowering.Lowerer.lower(entry.getValue()));
                }
                codeanalysis.emitting.Emitter depEmitter = new codeanalysis.emitting.Emitter(
                        new codeanalysis.binding.BoundBlockStatement(new java.util.ArrayList<>()),
                        loweredBodies);
                byte[] depBytes = depEmitter.emit(module.getClassName());
                String depPath = module.getClassName() + ".class";
                java.nio.file.Files.write(java.nio.file.Paths.get(depPath), depBytes);
                System.out.println("Compiled to " + depPath);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runFile(String path) {
        try {
            String absPath = java.nio.file.Paths.get(path).toAbsolutePath().toString();
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(absPath)));
            SyntaxTree tree = SyntaxTree.parse(source);
            codeanalysis.ModuleRegistry registry = new codeanalysis.ModuleRegistry();
            Compilation compilation = new Compilation(tree, registry, absPath);
            Map<VariableSymbol, Object> variables = new HashMap<>();
            EvaluationResult result = compilation.evaluate(variables);

            if (result.diagnostics().hasNext()) {
                DiagnosticBox diagnostics = result.diagnostics();
                while (diagnostics.hasNext()) {
                    Diagnostic diagnostic = diagnostics.next();
                    var lineIndex = tree.getText().getLineIndex(diagnostic.getSpan().getStart());
                    var lineNumber = lineIndex + 1;
                    var line = tree.getText().getLines().get(lineIndex);
                    var character = diagnostic.getSpan().getStart() - line.getStart() + 1;
                    System.err.printf("(%d, %d): %s%n", lineNumber, character, diagnostic);
                }
                System.exit(1);
            }

            if (result.getValue() != null) {
                System.out.println(result.getValue());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void repl() {
        Map<VariableSymbol, Object> variables = new HashMap<>();
        StringBuilder builder = new StringBuilder();
        Compilation previous = null;
        Scanner scanner = new Scanner(System.in);

        System.out.println("Siyo REPL v0.1.0 (type 'exit' to quit)");

        while (true) {
            System.out.print(builder.length() == 0 ? ">>> " : "... ");
            System.out.flush();

            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine();

            if (input.equals("exit") && builder.length() == 0) break;

            boolean isBlank = input.isEmpty();
            if (builder.length() == 0 && isBlank) continue;

            builder.append(input).append("\n");
            var text = builder.toString();

            SyntaxTree tree = SyntaxTree.parse(text);
            if (!isBlank && tree.diagnostics().hasNext()) continue;

            Compilation compilation = previous == null ? new Compilation(tree) : previous.continueWith(tree);
            try {
                EvaluationResult result = compilation.evaluate(variables);
                DiagnosticBox diagnostics = result.diagnostics();

                if (diagnostics.hasNext()) {
                    while (diagnostics.hasNext()) {
                        System.out.println("  " + diagnostics.next());
                    }
                } else {
                    previous = compilation;
                    if (result.getValue() != null) {
                        System.out.println(result.getValue());
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }

            builder = new StringBuilder();
        }
    }
}
