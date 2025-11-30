package de.chronos_live.chronos_date_api.presentation;

public record AdminStatisticsDto(long eventsCount, long userCount, long groupCount, long contactsCount, long pushSubscriptionCount) {
}
