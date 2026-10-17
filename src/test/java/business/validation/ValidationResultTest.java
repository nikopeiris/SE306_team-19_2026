package business.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationResultTest {

    @Test
    void requirePositiveRejectsMissingValue() {
        ValidationResult result = new ValidationResult()
                .requirePositive("addressId", null, "Address id");

        assertEquals("Address id cannot be empty", result.getError("addressId"));
    }

    @Test
    void requirePositiveRejectsZeroAndNegativeValues() {
        ValidationResult result = new ValidationResult()
                .requirePositive("page", 0L, "Page")
                .requirePositive("size", -5L, "Size");

        assertEquals("Page must be a positive integer", result.getError("page"));
        assertEquals("Size must be a positive integer", result.getError("size"));
    }

    @Test
    void requirePositiveAcceptsPositiveValue() {
        ValidationResult result = new ValidationResult()
                .requirePositive("addressId", 1L, "Address id");

        assertTrue(result.isValid());
        assertNull(result.getError("addressId"));
    }

    @Test
    void throwIfInvalidThrowsWithTheResultWhenErrorsWereRecorded() {
        ValidationResult result = new ValidationResult().addError("city", "City cannot be empty");

        ValidationException exception = assertThrows(ValidationException.class,
                result::throwIfInvalid);

        assertSame(result, exception.getResult());
    }

    @Test
    void throwIfInvalidDoesNothingWhenValid() {
        assertDoesNotThrow(() -> new ValidationResult().throwIfInvalid());
    }
}
