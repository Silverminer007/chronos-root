package de.chronos_live.chronos_date_api.application.events;

public record TeamMemberJoinedEvent(Long teamId, String userOidcId) {
}
