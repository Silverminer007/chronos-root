package de.chronos_live.chronos_date_api.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN");
    }
}