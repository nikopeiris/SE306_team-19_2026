package business.validation;

import business.lavsexceptions.LavsException;

// thrown when input fails validation before being sent to the mainframe
public final class ValidationException extends LavsException {
    private final ValidationResult _result;

    public ValidationException(ValidationResult result) {
        super(String.join("\n", result.getErrors().values()));
        _result = result;
    }

    public ValidationResult getResult() {
        return _result;
    }
}
