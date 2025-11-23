package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Settings extends PanacheEntity {
    private User user;
    private boolean eventChangedNotifications;
    private boolean contactsNotifications;
    private boolean groupMembershipNotifications;
    private boolean messagesNotifications;
    private boolean attendanceStatusChangedNotifications;
    private boolean eventRemindersNotifications;
}