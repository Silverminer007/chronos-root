package de.chronos_live.chronos_date_api.application.events;

public record TeamCreatedEvent(Long teamId, String actingUserOidcId) {
}
