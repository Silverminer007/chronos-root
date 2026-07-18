package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.infrastructure.SettingsRepository;
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
    @Inject
    SettingsRepository settingsRepository;

    private Settings getDefaultSetting() {
        Settings s = new Settings();
        s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
        s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
        s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
        s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.RESPONSIBLE);
        s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
        s.setGroupMemberAdded(NotificationSetting.DISABLED);
        return s;
    }

    public Settings getOrCreateSettings(String userOidcId) {
        return settingsRepository.findByUserOidcId(userOidcId).orElseGet(() -> {
            Settings setting = getDefaultSetting();
            setting.setUserOidcId(userOidcId);
            settingsRepository.persist(setting);
            return setting;
        });
    }

    public void updateSettings(String userOidcId, SettingsDto settingsDto) {
        LOGGER.debugf("[Principal %s] Updating Settings", userOidcId);
        Settings setting = getOrCreateSettings(userOidcId);
        settingsMapper.updateEntityFromDto(settingsDto, setting);
    }

    private boolean checkSetting(AppointmentNotificationSetting setting, AppointmentParticipation participation) {
        if (setting == null || participation == null) return false;
        if (AppointmentNotificationSetting.DISABLED.equals(setting)) return false;
        if (AppointmentNotificationSetting.ALL.equals(setting))
            return participation.getRole().ordinal() >= UserRole.GUEST.ordinal();
        if (AppointmentNotificationSetting.RESPONSIBLE.equals(setting))
            return participation.getRole().ordinal() >= UserRole.RESPONSIBLE.ordinal();
        if (AppointmentNotificationSetting.HELPER.equals(setting))
            return participation.getRole().ordinal() >= UserRole.HELPER.ordinal();
        if (AppointmentNotificationSetting.ATTENDANT.equals(setting))
            return participation.getRole().ordinal() >= UserRole.ATTENDANT.ordinal();
        return false;
    }

    public boolean sendAppointmentMovedNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentMoved(), ap);
    }

    public boolean sendAppointmentMessageSentNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentMessage(), ap);
    }

    public boolean sendAppointmentCancelledNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentCancelled(), ap);
    }

    public boolean sendAppointmentParticipantAddedEventNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentParticipantAdded(), ap);
    }

    public boolean sendGroupMemberAddedNotification(GroupMember gm) {
        return NotificationSetting.ENABLED.equals(getOrCreateSettings(gm.getUserOidcId()).getGroupMemberAdded());
    }

    public boolean sendAppointmentParticipationStatusChangedNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentParticipationStatusChanged(), ap);
    }

    public boolean sendAppointmentParticipationInvalidNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentParticipationInvalid(), ap);
    }

    public boolean sendAppointmentParticipationStatusPendingReminderNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentParticipationStatusPending(), ap);
    }

    public boolean sendAppointmentReminderNotification(AppointmentParticipation ap) {
        return checkSetting(getOrCreateSettings(ap.getUserOidcId()).getAppointmentReminder(), ap);
    }

    public boolean sendAppointmentParticipationStatusRecheckRequestedNotification(AppointmentParticipation ap) {
        return true;
    }
}
