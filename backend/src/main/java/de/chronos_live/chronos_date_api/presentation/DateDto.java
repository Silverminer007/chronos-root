package de.chronos_live.chronos_date_api.presentation;

public record DateDto(Long id, String name, String description, String start, String end, String venue, Long group_id,
                      Long linked_to, String status) {
}
