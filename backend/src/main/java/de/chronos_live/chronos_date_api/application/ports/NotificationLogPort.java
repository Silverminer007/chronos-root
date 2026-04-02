package de.chronos_live.chronos_date_api.application.ports;

public interface NotificationLogPort {
    void log(Long userId, String notificationType, String payload,
             String endpoint, Integer httpStatusCode, boolean success, String errorMessage);
}
