package lavs.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// the outcome of validating some input: a set of field names mapped to error messages
public final class ValidationResult {
    private final Map<String, String> _errors = new LinkedHashMap<>();

    // records an error against a field; the first error for a field is kept
    public ValidationResult addError(String field, String message) {
        _errors.putIfAbsent(field, message);
        return this;
    }

    // records an error against a field if the value is null or blank
    public ValidationResult requireNotBlank(String field, String value, String message) {
        if (value == null || value.isBlank()) {
            addError(field, message);
        }
        return this;
    }

    public boolean isValid() {
        return _errors.isEmpty();
    }

    public Map<String, String> getErrors() {
        return Collections.unmodifiableMap(_errors);
    }

    // gets the error message for a field, or null if the field is valid
    public String getError(String field) {
        return _errors.get(field);
    }
}
