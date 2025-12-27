package de.chronos_live.chronos_date_api.application.events;

public record GroupMemberRemovedEvent(Long groupId, Long removedMemberId, Long actingUserId) {
}
