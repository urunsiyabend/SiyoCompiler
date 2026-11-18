package codeanalysis;


import codeanalysis.text.SourceText;
import codeanalysis.text.TextSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticTest {
    @Test
    void DiagnosticTest_getSpanReturnsTheSuppliedSpan() {
        var span = new TextSpan(3, 5);
        String message = "Unexpected token";
        var sourceFile = "test.lang";
        var sourceText = SourceText.from("one\r\ntwo");

        Diagnostic diagnostic = new Diagnostic(span, message);
        diagnostic.setSource(sourceFile, sourceText);

        assertEquals(span, diagnostic.getSpan());
        assertEquals(message, diagnostic.getMessage());
        assertEquals(message, diagnostic.toString());
        assertEquals(sourceFile, diagnostic.getSourceFile());
        assertEquals(sourceText, diagnostic.getSourceText());
    }
}
