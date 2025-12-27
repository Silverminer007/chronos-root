package de.chronos_live.chronos_date_api.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard Error Response für alle API-Fehler
 */
@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    // Getters und Setters
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String errorCode;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;  // Nur bei ValidationException

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String error, String errorCode,
                         String message, String path) {
        this();
        this.status = status;
        this.error = error;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
    }

    // Builder für einfache Konstruktion
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ErrorResponse response = new ErrorResponse();

        public Builder status(int status) {
            response.status = status;
            return this;
        }

        public Builder error(String error) {
            response.error = error;
            return this;
        }

        public Builder errorCode(String errorCode) {
            response.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            response.message = message;
            return this;
        }

        public Builder path(String path) {
            response.path = path;
            return this;
        }

        public Builder fieldErrors(Map<String, String> fieldErrors) {
            response.fieldErrors = fieldErrors;
            return this;
        }

        public ErrorResponse build() {
            return response;
        }
    }

}