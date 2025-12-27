package de.chronos_live.chronos_date_api.exception;

public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(message, "BAD_REQUEST");
    }
}