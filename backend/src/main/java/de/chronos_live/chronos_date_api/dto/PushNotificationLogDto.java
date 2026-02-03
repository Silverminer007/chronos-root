package de.chronos_live.chronos_date_api.dto;

public record PushNotificationLogDto(
        Long id,
        Long user_id,
        String notification_type,
        String payload,
        String endpoint,
        Integer http_status_code,
        boolean success,
        String error_message,
        String created_at
) {
}
