package com.urunsiyabend;

import com.urunsiyabend.codeanalysis.Evaluator;
import com.urunsiyabend.codeanalysis.SyntaxNode;
import com.urunsiyabend.codeanalysis.SyntaxToken;
import com.urunsiyabend.codeanalysis.SyntaxTree;

import java.util.Iterator;
import java.util.Scanner;

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
        while (true) {
            System.out.print("> ");
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();
            if (line.equals("")) {
                return;
            }

            SyntaxTree tree = SyntaxTree.parse(line);
            prettyPrint(tree.getRoot(), "", true);

            Iterator<String> diagnosticsIterator = tree.diagnostics();
            if (diagnosticsIterator.hasNext()) {
                while (diagnosticsIterator.hasNext()) {
                    System.out.println(diagnosticsIterator.next());
                }
            }
            else {
                Evaluator evaluator = new Evaluator(tree.getRoot());
                try {
                    int result = evaluator.evaluate();
                    System.out.println(result);
                }
                catch (Exception e) {
                    System.out.println(e);
                }

            }
        }
    }

    /**
     * Recursively pretty prints the syntax tree.
     *
     * @param node   The syntax node to print.
     * @param indent The indentation string.
     * @param isLast Specifies if the current node is the last child of its parent.
     */
    static void prettyPrint(SyntaxNode node, String indent, boolean isLast) {
        String marker = isLast ? "└──" : "├──";

        System.out.print(indent);
        System.out.print(marker);
        System.out.print(node.getType());


        if (node instanceof SyntaxToken t && t.getValue() != null) {
            System.out.print(" ");
            System.out.print(t.getValue());
        }

        System.out.println();

        indent += isLast ? "    ": "│   ";

        Iterator<SyntaxNode> iterator = node.getChildren();
        while (iterator.hasNext()) {
            SyntaxNode child = iterator.next();
            boolean last = !iterator.hasNext();
            prettyPrint(child, indent, last);
        }
    }
}
