package de.chronos_live.chronos_date_api.application.events;

public record TeamMemberRemovedEvent(Long teamId, String removedUserOidcId, String actingUserOidcId) {}
