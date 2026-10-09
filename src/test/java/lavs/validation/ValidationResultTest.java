package lavs.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {
    @Test
    void newResultIsValid() {
        ValidationResult result = new ValidationResult();

        assertAll(
                () -> assertTrue(result.isValid()),
                () -> assertTrue(result.getErrors().isEmpty()),
                () -> assertNull(result.getError("name"))
        );
    }

    @Test
    void addErrorMakesResultInvalidAndKeepsFirstMessage() {
        ValidationResult result = new ValidationResult()
                .addError("name", "first")
                .addError("name", "second");

        assertAll(
                () -> assertFalse(result.isValid()),
                () -> assertEquals("first", result.getError("name")),
                () -> assertEquals(1, result.getErrors().size())
        );
    }

    @Test
    void requireNotBlankFlagsNullAndBlankValues() {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("a", null, "a missing")
                .requireNotBlank("b", "   ", "b missing")
                .requireNotBlank("c", "value", "c missing");

        assertAll(
                () -> assertEquals("a missing", result.getError("a")),
                () -> assertEquals("b missing", result.getError("b")),
                () -> assertNull(result.getError("c"))
        );
    }

    @Test
    void errorsCannotBeModifiedExternally() {
        ValidationResult result = new ValidationResult().addError("a", "x");

        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().put("b", "y"));
    }

    @Test
    void validationExceptionJoinsAllMessages() {
        ValidationResult result = new ValidationResult()
                .addError("a", "first")
                .addError("b", "second");

        ValidationException ex = new ValidationException(result);

        assertAll(
                () -> assertEquals("first\nsecond", ex.getMessage()),
                () -> assertSame(result, ex.getResult())
        );
    }
}
