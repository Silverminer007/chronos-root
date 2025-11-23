package de.chronos_live.chronos_date_api.presentation;

public record SettingsDto(
        boolean eventChangedNotifications,
        boolean contactsNotifications,
        boolean groupMembershipNotifications,
        boolean messagesNotifications,
        boolean attendanceStatusChangedNotifications,
        boolean eventRemindersNotifications
) {
}