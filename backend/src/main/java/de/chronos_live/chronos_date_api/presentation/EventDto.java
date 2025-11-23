package de.chronos_live.chronos_date_api.presentation;

public record EventDto(Long id, String name, String description, String start, String end, String venue,
                       String status, Integer minimalAttendees) {
}
