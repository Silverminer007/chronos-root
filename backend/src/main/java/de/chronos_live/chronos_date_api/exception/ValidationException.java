package de.chronos_live.chronos_date_api.exception;

import java.util.HashMap;
import java.util.Map;

public class ValidationException extends BusinessException {
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR");
        this.fieldErrors = new HashMap<>();
    }

    public ValidationException(Map<String, String> fieldErrors) {
        super("Validierungsfehler", "VALIDATION_ERROR");
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String field, String error) {
        super("Validierungsfehler bei Feld: " + field, "VALIDATION_ERROR");
        this.fieldErrors = Map.of(field, error);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
