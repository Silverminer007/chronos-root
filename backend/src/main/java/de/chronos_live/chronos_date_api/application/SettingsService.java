package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Settings;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class SettingsService {
    public Settings getOrCreateSettings(User user) {
        return (Settings) Settings.find("user.id", user.id).firstResultOptional().orElseGet(() -> {
            Settings setting = new Settings();
            setting.setUser(user);
            setting.setAttendanceStatusChangedNotifications(false);
            setting.setContactsNotifications(true);
            setting.setEventChangedNotifications(false);
            setting.setEventRemindersNotifications(true);
            setting.setMessagesNotifications(true);
            setting.setGroupMembershipNotifications(true);
            setting.persist();
            return setting;
        });
    }

    public void updateSettings(User user, Settings settings) {
        Settings setting = getOrCreateSettings(user);
        setting.setAttendanceStatusChangedNotifications(settings.isAttendanceStatusChangedNotifications());
        setting.setContactsNotifications(settings.isContactsNotifications());
        setting.setEventChangedNotifications(settings.isEventChangedNotifications());
        setting.setMessagesNotifications(settings.isMessagesNotifications());
        setting.setGroupMembershipNotifications(settings.isGroupMembershipNotifications());
        setting.setEventRemindersNotifications(settings.isEventRemindersNotifications());
    }
}