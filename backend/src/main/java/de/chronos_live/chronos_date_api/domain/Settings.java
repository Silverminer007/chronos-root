package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
public class Settings extends PanacheEntity {
    @OneToOne(cascade = CascadeType.REMOVE)
    private User user;
    private boolean eventChangedNotifications;
    private boolean contactsNotifications;
    private boolean groupMembershipNotifications;
    private boolean messagesNotifications;
    private boolean attendanceStatusChangedNotifications;
    private boolean eventRemindersNotifications;
}