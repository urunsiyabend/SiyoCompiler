import codeanalysis.*;
import codeanalysis.syntax.SyntaxTree;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Entry point of program.
 */
public class Main {
    /**
     * Main method to execute the code analysis and evaluation.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        Map<VariableSymbol, Object> variables = new HashMap<>();

        while (true) {
            System.out.print("> ");
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();
            if (line.equals("")) {
                return;
            }

            SyntaxTree tree = SyntaxTree.parse(line);
            Compilation compilation = new Compilation(tree);
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
                printWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (diagnosticsIterator.hasNext()) {
                while (diagnosticsIterator.hasNext()) {
                    Diagnostic diagnostic = diagnosticsIterator.next();
                    System.out.println();
                    System.out.println(diagnostic);

                    String prefix = line.substring(0, diagnostic.getSpan().getStart());
                    String error = line.substring(diagnostic.getSpan().getStart(), diagnostic.getSpan().getEnd());
                    String suffix = line.substring(diagnostic.getSpan().getEnd());

                    System.out.print("    ");
                    System.out.print(prefix);
                    System.out.print(error);
                    System.out.print(suffix);
                    System.out.println();
                    System.out.println(" ".repeat(diagnostic.getSpan().getStart() + 4) + "^".repeat(diagnostic.getSpan().getLength()));
                }
            }
            else {
                try {
                    System.out.println(result.getValue());
                }
                catch (Exception e) {
                    System.out.println(e);
                }

            }
        }
    }
}
