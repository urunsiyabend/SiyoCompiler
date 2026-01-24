import codeanalysis.*;
import codeanalysis.syntax.SyntaxTree;
import codeanalysis.text.TextSpan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line interface for the Siyo compiler.
 * Compiles .siyo source files to JVM bytecode (.class files).
 *
 * Usage: siyoc compile &lt;input.siyo&gt; &lt;ClassName&gt;
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Siyoc {
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        switch (command) {
            case "compile" -> {
                if (args.length < 3) {
                    System.err.println("Error: compile requires <input> and <className> arguments");
                    printUsage();
                    System.exit(1);
                }
                compile(args[1], args[2]);
            }
            case "help", "--help", "-h" -> printUsage();
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    /**
     * Compiles a .siyo source file to a .class file.
     *
     * @param inputPath  Path to the source file.
     * @param className  Name of the output class (without .class extension).
     */
    private static void compile(String inputPath, String className) {
        // Read source file
        Path sourcePath = Paths.get(inputPath);
        String source;
        try {
            source = Files.readString(sourcePath);
        } catch (IOException e) {
            System.err.println("Error: Could not read file: " + inputPath);
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        // Parse and compile
        SyntaxTree tree = SyntaxTree.parse(source);
        Compilation compilation = new Compilation(tree);
        EmitResult result = compilation.emitBytecode(className);

        // Check for errors
        if (!result.isSuccess()) {
            DiagnosticBox diagnostics = result.diagnostics();
            while (diagnostics.hasNext()) {
                Diagnostic diagnostic = diagnostics.next();
                int lineIndex = tree.getText().getLineIndex(diagnostic.getSpan().getStart());
                var line = tree.getText().getLines().get(lineIndex);
                int lineNumber = lineIndex + 1;
                int character = diagnostic.getSpan().getStart() - line.getStart() + 1;

                System.err.println();
                System.err.printf("(%d, %d): %s%n", lineNumber, character, diagnostic);

                TextSpan prefixSpan = TextSpan.fromBounds(line.getStart(), diagnostic.getSpan().getStart());
                TextSpan suffixSpan = TextSpan.fromBounds(diagnostic.getSpan().getEnd(), line.getEnd());

                String prefix = tree.getText().toString(prefixSpan);
                String error = tree.getText().toString(diagnostic.getSpan());
                String suffix = tree.getText().toString(suffixSpan);

                System.err.print("    ");
                System.err.print(prefix);
                System.err.print(error);
                System.err.println(suffix);
                System.err.println(" ".repeat(4 + prefix.length()) + "^".repeat(Math.max(1, diagnostic.getSpan().getLength())));
            }
            System.exit(1);
            return;
        }

        // Write .class file
        Path outputPath = Paths.get(className + ".class");
        try {
            Files.write(outputPath, result.getBytecode());
            System.out.println("Compiled successfully: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error: Could not write output file: " + outputPath);
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Siyo Compiler");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  siyoc compile <input.siyo> <ClassName>  Compile source to .class file");
        System.out.println("  siyoc help                              Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  siyoc compile hello.siyo Program        Compiles hello.siyo to Program.class");
        System.out.println("  java Program                            Runs the compiled program");
    }
}
