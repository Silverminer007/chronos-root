package de.chronos_live.chronos_date_api.application.events;

public record GroupNameChangedEvent(Long groupId, String oldName, String newName, Long actingUserId) {
}
