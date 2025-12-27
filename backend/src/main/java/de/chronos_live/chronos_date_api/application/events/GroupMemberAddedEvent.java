package de.chronos_live.chronos_date_api.application.events;

public record GroupMemberAddedEvent(Long groupId, Long newMemberId, Long actingUserId) {
}
