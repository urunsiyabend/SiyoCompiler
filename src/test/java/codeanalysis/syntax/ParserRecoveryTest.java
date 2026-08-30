package codeanalysis.syntax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Malformed sources must terminate with diagnostics instead of spinning in a
 * parser loop that never consumes a token. Before 0.4.0 several list parsers
 * (parameters, arguments, fields, enum members, match arms, literals) could
 * report the same unexpected token forever and exhaust the heap.
 */
class ParserRecoveryTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Dotted method declaration — the pre-0.4.0 documented impl spelling.
            "struct P { x: int }\nfn P.get() -> int { self.x }\n",
            // Parameter list that never reaches ')'
            "fn f(: int) {}\n",
            "fn f(x int) {}\n",
            // Argument list that never reaches ')'
            "fn main() { println(1 2) }\n",
            // Array literal that never reaches ']'
            "fn main() { mut a = [1 2] }\n",
            // Struct declaration fields
            "struct S { x int }\n",
            // Struct literal fields
            "struct S { x: int }\nfn main() { mut s = S { x: } }\n",
            // Enum members
            "enum E { A 1 }\n",
            // Actor fields
            "actor A { count int }\n",
            // Impl body that is not a function declaration
            "struct S { x: int }\nimpl S { x: int }\n",
            // Map literal entries
            "fn main() { mut m = {\"a\" 1} }\n",
            // Match arms
            "fn main() { mut v = match 1 { 1 2 } }\n",
    })
    void malformedSourcesTerminateWithDiagnostics(String source) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            SyntaxTree tree = SyntaxTree.parse(source);
            assertTrue(tree.diagnostics().hasNext(),
                    "expected diagnostics for malformed source: " + source.replace("\n", "\\n"));
        });
    }
}
