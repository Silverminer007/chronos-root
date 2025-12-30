package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "appointment")
public class Appointment extends PanacheEntity {
    private String name, description, venue;
    @Column(name = "start_time")
    private Instant startTime;
    @Column(name = "end_time")
    private Instant endTime;
    private AppointmentStatus status;
    private Integer minimalAttendees;

    private Instant lastUpdate, createdAt;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "appointment")
    @EqualsAndHashCode.Exclude
    private Set<Message> messages;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "appointment")
    @EqualsAndHashCode.Exclude
    private Set<AppointmentParticipation> participants;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "appointment")
    @EqualsAndHashCode.Exclude
    private Set<AppointmentGroupParticipation> groupParticipants;

    public Appointment(Appointment appointment) {
        this.name = appointment.name;
        this.description = appointment.description;
        this.venue = appointment.venue;
        this.startTime = appointment.startTime;
        this.endTime = appointment.endTime;
        this.status = appointment.status;
        this.minimalAttendees = appointment.minimalAttendees;
        this.messages = appointment.messages;
        this.participants = appointment.participants;
        this.groupParticipants = appointment.groupParticipants;
        this.lastUpdate = Instant.now();
        this.createdAt = Instant.now();
    }

    public Appointment(){}
}
