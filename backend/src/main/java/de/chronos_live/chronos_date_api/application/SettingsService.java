package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.mapper.SettingsMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
@Timed("service.settings")
public class SettingsService {
    private static final Logger LOGGER = Logger.getLogger(SettingsService.class);

    @Inject
    SettingsMapper settingsMapper;

    private Settings getDefaultSetting() {
        Settings defaultSettings = new Settings();
        defaultSettings.setAppointmentMoved(AppointmentNotificationSetting.ALL);
        defaultSettings.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
        defaultSettings.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
        defaultSettings.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
        defaultSettings.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.RESPONSIBLE);
        defaultSettings.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
        defaultSettings.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
        defaultSettings.setAppointmentReminder(AppointmentNotificationSetting.ALL);
        defaultSettings.setGroupMemberAdded(NotificationSetting.DISABLED);
        return defaultSettings;
    }

    public Settings getOrCreateSettings(Long userId) {
        return (Settings) Settings.find("user.id", userId).firstResultOptional().orElseGet(() -> {
            Settings setting = this.getDefaultSetting();
            User user = User.findById(userId);
            setting.setUser(user);
            setting.persist();
            return setting;
        });
    }

    public void updateSettings(Long userId, SettingsDto settingsDto) {
        LOGGER.debugf("[Principal %s] Updating Settings", userId);
        Settings setting = getOrCreateSettings(userId);

        this.settingsMapper.updateEntityFromDto(settingsDto, setting);
    }

    private boolean checkSetting(AppointmentNotificationSetting setting, AppointmentParticipation appointmentParticipation) {
        if (setting == null || appointmentParticipation == null) {
            return false;
        }
        if (AppointmentNotificationSetting.DISABLED.equals(setting)) {
            return false;
        }
        if (AppointmentNotificationSetting.ALL.equals(setting)) {
            return appointmentParticipation.getRole().ordinal() >= UserRole.GUEST.ordinal();
        }
        if (AppointmentNotificationSetting.RESPONSIBLE.equals(setting)) {
            return appointmentParticipation.getRole().ordinal() >= UserRole.RESPONSIBLE.ordinal();
        }
        if (AppointmentNotificationSetting.HELPER.equals(setting)) {
            return appointmentParticipation.getRole().ordinal() >= UserRole.HELPER.ordinal();
        }
        if (AppointmentNotificationSetting.ATTENDANT.equals(setting)) {
            return appointmentParticipation.getRole().ordinal() >= UserRole.ATTENDANT.ordinal();
        }
        return false;
    }

    public boolean sendAppointmentMovedNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentMoved(), appointmentParticipation);
    }

    public boolean sendAppointmentMessageSentNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentMessage(), appointmentParticipation);
    }

    public boolean sendAppointmentCancelledNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentCancelled(), appointmentParticipation);
    }

    public boolean sendAppointmentParticipantAddedEventNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentParticipantAdded(), appointmentParticipation);
    }

    public boolean sendGroupMemberAddedNotification(GroupMember groupMember) {
        return NotificationSetting.ENABLED.equals(this.getOrCreateSettings(groupMember.getUser().id).getGroupMemberAdded());
    }

    public boolean sendAppointmentParticipationStatusChangedNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentParticipationStatusChanged(), appointmentParticipation);
    }

    public boolean sendAppointmentParticipationInvalidNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentParticipationInvalid(), appointmentParticipation);
    }

    public boolean sendAppointmentParticipationStatusPendingReminderNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentParticipationStatusPending(), appointmentParticipation);
    }

    public boolean sendAppointmentReminderNotification(AppointmentParticipation appointmentParticipation) {
        Settings settings = this.getOrCreateSettings(appointmentParticipation.getUser().id);
        return this.checkSetting(settings.getAppointmentReminder(), appointmentParticipation);
    }

    public boolean sendAppointmentParticipationStatusRecheckRequestedNotification(AppointmentParticipation appointmentParticipation) {
        return true;
    }
}
