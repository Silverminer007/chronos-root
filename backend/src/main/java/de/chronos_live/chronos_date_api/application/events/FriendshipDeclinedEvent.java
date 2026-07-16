package de.chronos_live.chronos_date_api.application.events;

public record FriendshipDeclinedEvent(Long requestId, String requesterOidcId, String addresseeOidcId) {
}
