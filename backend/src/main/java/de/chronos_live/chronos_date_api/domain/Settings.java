package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
public class Settings extends PanacheEntity {
    @Column(name = "user_oidcid")
    private String userOidcId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_moved")
    private AppointmentNotificationSetting appointmentMoved;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_message")
    private AppointmentNotificationSetting appointmentMessage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_cancelled")
    private AppointmentNotificationSetting appointmentCancelled;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_participant_added")
    private AppointmentNotificationSetting appointmentParticipantAdded;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_participation_status_changed")
    private AppointmentNotificationSetting appointmentParticipationStatusChanged;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_participation_invalid")
    private AppointmentNotificationSetting appointmentParticipationInvalid;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_participation_status_pending")
    private AppointmentNotificationSetting appointmentParticipationStatusPending;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "appointment_reminder")
    private AppointmentNotificationSetting appointmentReminder;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "group_member_added")
    private NotificationSetting groupMemberAdded;
}