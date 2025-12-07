package de.chronos_live.chronos_date_api.presentation;

public record AttendanceDto(Long id, Long user_id, String user_name, Long event_id, String status) {
}
