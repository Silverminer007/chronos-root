package de.chronos_live.chronos_date_api.presentation;

public record PushSubscriptionDto(
        String endpoint,
        Keys keys
) {
    public record Keys(String p256dh, String auth) {
    }
}