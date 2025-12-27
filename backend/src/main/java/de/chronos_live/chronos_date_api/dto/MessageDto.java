package de.chronos_live.chronos_date_api.dto;

public record MessageDto(Long id, Long sender_id, String sender_name, Long appointment_id, String body, String timestamp) {
}
