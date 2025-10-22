package de.chronos_live.chronos_date_api.presentation;

public record AttendanceDto(Long id, Long user_id, Long date_id, String status, String lastChanged) {
}
