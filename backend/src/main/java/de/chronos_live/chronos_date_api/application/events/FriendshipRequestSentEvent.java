package de.chronos_live.chronos_date_api.application.events;

public record FriendshipRequestSentEvent(Long requestId, String requesterOidcId, String addresseeOidcId, String requesterName) {
}
