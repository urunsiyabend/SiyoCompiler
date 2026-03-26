import codeanalysis.*;
import codeanalysis.syntax.SyntaxTree;
import codeanalysis.text.TextSpan;

import java.io.IOException;
import java.io.PrintWriter;
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
        if (args.length >= 2 && args[0].equals("run")) {
            runFile(args[1]);
            return;
        }
        if (args.length >= 1 && !args[0].equals("repl")) {
            runFile(args[0]);
            return;
        }
        repl();
    }

    private static void runFile(String path) {
        try {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
            SyntaxTree tree = SyntaxTree.parse(source);
            Compilation compilation = new Compilation(tree);
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

        while (true) {
            if (builder.length() == 0) {
                System.out.print("» ");
            } else {
                System.out.print("· ");
            }
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();

            boolean isBlank = input.equals("");
            if (builder.length() == 0) {
                if (isBlank) {
                    break;
                }
            }


            builder.append(input);
            builder.append("\n");
            var text = builder.toString();


            SyntaxTree tree = SyntaxTree.parse(text);
            if(!isBlank && tree.diagnostics().hasNext()) {
                continue;
            }


            Compilation compilation = previous == null ? new Compilation(tree) : previous.continueWith(tree);
            EvaluationResult result;
            try {
                result = compilation.evaluate(variables);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            DiagnosticBox diagnosticsIterator = result.diagnostics();
            PrintWriter printWriter = new PrintWriter(System.out);
            try {
                tree.getRoot().writeTo(printWriter);
                compilation.emitTree(printWriter);
                printWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (diagnosticsIterator.hasNext()) {
                while (diagnosticsIterator.hasNext()) {
                    Diagnostic diagnostic = diagnosticsIterator.next();
                    var lineIndex = tree.getText().getLineIndex(diagnostic.getSpan().getStart());
                    var line = tree.getText().getLines().get(lineIndex);
                    var lineNumber = lineIndex + 1;
                    var character = diagnostic.getSpan().getStart() - line.getStart() + 1;

                    System.out.println();
                    System.out.print(String.format("(%s, %s): ", lineNumber, character));
                    System.out.println(diagnostic);

                    var prefixSpan = TextSpan.fromBounds(line.getStart(), diagnostic.getSpan().getStart());
                    var suffixSpan = TextSpan.fromBounds(diagnostic.getSpan().getEnd(), line.getEnd());

                    String prefix = tree.getText().toString(prefixSpan);
                    String error = tree.getText().toString(diagnostic.getSpan());
                    String suffix = tree.getText().toString(suffixSpan);

                    System.out.print("    ");
                    System.out.print(prefix);
                    System.out.print(error);
                    System.out.print(suffix);
                    System.out.println();
                    System.out.println(" ".repeat(diagnostic.getSpan().getStart() + 4) + "^".repeat(diagnostic.getSpan().getLength()));
                }
            }
            else {
                previous = compilation;
                try {
                    System.out.println(result.getValue());
                }
                catch (Exception e) {
                    System.out.println(e);
                }
            }

            builder = new StringBuilder();
        }
    }
}
