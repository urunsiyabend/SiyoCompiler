package codeanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FunctionTypeSignatureTest {

    @Test
    void encodesAndParsesFunctionTypeSignature() {
        String encoded = FunctionTypeSignature.encode(
                List.of("int", "string"),
                "bool"
        );

        assertEquals("fn(int,string)->bool", encoded);

        FunctionTypeSignature signature = FunctionTypeSignature.parse(encoded);

        assertEquals(List.of("int", "string"), signature.getParameterTypeNames());
        assertEquals(2, signature.getParameterCount());
        assertEquals("bool", signature.getReturnTypeName());
        assertEquals("fn(int, string) -> bool", signature.toString());
    }

    @Test
    void classifiesFunctionSignatures() {
        assertTrue(FunctionTypeSignature.isSignature("fn(int)->int"));
        assertTrue(FunctionTypeSignature.isFunctionArray("fn()[]"));
    }
}