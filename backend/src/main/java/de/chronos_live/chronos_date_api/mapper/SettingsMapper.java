package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.AppointmentNotificationSetting;
import de.chronos_live.chronos_date_api.domain.NotificationSetting;
import de.chronos_live.chronos_date_api.domain.Settings;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface SettingsMapper {

    @Mapping(target = "appointmentMoved", source = "appointment_moved", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentMessage", source = "appointment_message", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentCancelled", source = "appointment_cancelled", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipantAdded", source = "appointment_participant_added", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationStatusChanged", source = "appointment_participation_status_changed", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationInvalid", source = "appointment_participation_invalid", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationStatusPending", source = "appointment_participation_status_pending", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentReminder", source = "appointment_reminder", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "groupMemberAdded", source = "group_member_added", qualifiedByName = "stringToNotificationSetting")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userOidcId", ignore = true)
    Settings toEntity(SettingsDto dto);

    @Mapping(target = "appointment_moved", source = "appointmentMoved", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_message", source = "appointmentMessage", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_cancelled", source = "appointmentCancelled", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_participant_added", source = "appointmentParticipantAdded", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_participation_status_changed", source = "appointmentParticipationStatusChanged", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_participation_invalid", source = "appointmentParticipationInvalid", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_participation_status_pending", source = "appointmentParticipationStatusPending", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "appointment_reminder", source = "appointmentReminder", qualifiedByName = "appointmentNotificationSettingToString")
    @Mapping(target = "group_member_added", source = "groupMemberAdded", qualifiedByName = "notificationSettingToString")
    SettingsDto toDto(Settings entity);

    @Mapping(target = "appointmentMoved", source = "appointment_moved", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentMessage", source = "appointment_message", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentCancelled", source = "appointment_cancelled", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipantAdded", source = "appointment_participant_added", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationStatusChanged", source = "appointment_participation_status_changed", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationInvalid", source = "appointment_participation_invalid", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentParticipationStatusPending", source = "appointment_participation_status_pending", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "appointmentReminder", source = "appointment_reminder", qualifiedByName = "stringToAppointmentNotificationSetting")
    @Mapping(target = "groupMemberAdded", source = "group_member_added", qualifiedByName = "stringToNotificationSetting")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userOidcId", ignore = true)
    void updateEntityFromDto(SettingsDto dto, @MappingTarget Settings entity);

    // Converter für AppointmentNotificationSetting
    @Named("stringToAppointmentNotificationSetting")
    default AppointmentNotificationSetting stringToAppointmentNotificationSetting(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AppointmentNotificationSetting.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Named("appointmentNotificationSettingToString")
    default String appointmentNotificationSettingToString(AppointmentNotificationSetting setting) {
        return setting != null ? setting.name() : null;
    }

    // Converter für NotificationSetting
    @Named("stringToNotificationSetting")
    default NotificationSetting stringToNotificationSetting(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationSetting.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Named("notificationSettingToString")
    default String notificationSettingToString(NotificationSetting setting) {
        return setting != null ? setting.name() : null;
    }
}
