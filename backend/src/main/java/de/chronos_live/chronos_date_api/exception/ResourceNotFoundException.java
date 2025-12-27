package de.chronos_live.chronos_date_api.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " mit ID " + id + " wurde nicht gefunden",
                "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}