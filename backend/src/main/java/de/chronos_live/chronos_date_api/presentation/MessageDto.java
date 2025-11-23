package de.chronos_live.chronos_date_api.presentation;

public record MessageDto(Long id, Long sender_id, Long event_id, String title, String message, String timeStamp) {
}
