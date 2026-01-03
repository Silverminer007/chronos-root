package de.chronos_live.chronos_date_api.dto;

public record AdminStatisticsDto(
        UserStatistics users,
        AppointmentStatistics appointments,
        ParticipationStatistics participations,
        GroupStatistics groups,
        FriendshipStatistics friendships,
        MessageStatistics messages,
        PushSubscriptionStatistics pushSubscriptions
) {
    public record UserStatistics(long total) {}

    public record AppointmentStatistics(
            long total,
            long planned,
            long cancelled,
            long deleted,
            long notEnoughAttendees
    ) {}

    public record ParticipationStatistics(
            long total,
            long pending,
            long approved,
            long rejected
    ) {}

    public record GroupStatistics(
            long total,
            long totalMembers
    ) {}

    public record FriendshipStatistics(
            long total,
            long pending,
            long accepted,
            long declined
    ) {}

    public record MessageStatistics(long total) {}

    public record PushSubscriptionStatistics(long total) {}
}
