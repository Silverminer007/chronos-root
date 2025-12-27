package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.AppointmentDto;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
@Transactional
public class AppointmentService {
    @Inject
    AuthorizationService authorizationService;
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    Event<AppointmentMovedEvent> appointmentMovedEvent;
    @Inject
    Event<AppointmentCancelledEvent> appointmentCancelledEvent;
    @Inject
    Event<AppointmentDeletedEvent> appointmentDeletedEvent;
    @Inject
    Event<AppointmentCreatedEvent> appointmentCreatedEvent;
    @Inject
    Event<AppointmentEditedEvent> appointmentEditedEvent;

    public Appointment createAppointment(CreateAppointmentDto createAppointmentDto, Long creatorId) {
        Appointment appointment = new Appointment();
        if (createAppointmentDto.getName().isBlank()) {
            throw new ValidationException("name", "Name cannot be blank");
        }
        appointment.setName(createAppointmentDto.getName());
        if (createAppointmentDto.getEnd() == null) {
            throw new ValidationException("end", "End cannot be null");
        }
        appointment.setEndTime(Instant.parse(createAppointmentDto.getEnd()));
        if (createAppointmentDto.getStart() == null) {
            throw new ValidationException("start", "start cannot be null");
        }
        appointment.setStartTime(Instant.parse(createAppointmentDto.getStart()));
        if (appointment.getEndTime().isBefore(appointment.getStartTime())) {
            throw new ValidationException("end", "End date cannot be before start date");
        }
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatedAt(Instant.now());
        appointment.setLastUpdate(Instant.now());
        appointment.persist();

        this.appointmentCreatedEvent.fireAsync(new AppointmentCreatedEvent(appointment.id, creatorId));

        return appointment;
    }

    public Appointment updateAppointment(Long appointmentId, Long actingUserId, UpdateAppointmentDto updateAppointmentDto) {
        this.authorizationService.requireUpdateAppointment(appointmentId, actingUserId);

        Appointment appointment = Appointment.findById(appointmentId);

        if (updateAppointmentDto.getName() != null) {
            if (updateAppointmentDto.getName().isBlank()) {
                throw new ValidationException("name", "Name cannot be blank");
            }
            appointment.setName(updateAppointmentDto.getName());
        }
        if (updateAppointmentDto.getDescription() != null) {
            appointment.setDescription(updateAppointmentDto.getDescription());
        }
        if (updateAppointmentDto.getVenue() != null) {
            appointment.setVenue(updateAppointmentDto.getVenue());
        }
        // Es könnte sein, dass nur Start-Datum oder nur End-Datum aktualisiert werden.
        // Deshalb müssen auch beide einzeln validiert werden
        Instant oldStartTime = appointment.getStartTime();
        Instant oldEndTime = appointment.getEndTime();
        Instant newStartTime = appointment.getStartTime();
        Instant newEndTime = appointment.getEndTime();
        if (updateAppointmentDto.getStart() != null && updateAppointmentDto.getEnd() != null) {
            newStartTime = Instant.parse(updateAppointmentDto.getStart());
            newEndTime = Instant.parse(updateAppointmentDto.getEnd());
        } else if (updateAppointmentDto.getStart() != null) {
            newStartTime = Instant.parse(updateAppointmentDto.getStart());
        } else if (updateAppointmentDto.getEnd() != null) {
            newEndTime = Instant.parse(updateAppointmentDto.getEnd());
        }
        if (newEndTime.isBefore(newEndTime)) {
            throw new ValidationException("end", "Start date cannot be after end date");
        }
        if(!oldStartTime.equals(newStartTime) || !oldEndTime.equals(newEndTime)) {
            this.appointmentMovedEvent.fireAsync(new AppointmentMovedEvent(appointment.id,
                    oldStartTime, oldEndTime, actingUserId));
        }
        appointment.setStartTime(newStartTime);
        appointment.setEndTime(newEndTime);
        if (updateAppointmentDto.getMinimal_attendees() != null) {
            appointment.setMinimalAttendees(updateAppointmentDto.getMinimal_attendees());
        }
        appointment.setLastUpdate(Instant.now());

        this.appointmentEditedEvent.fireAsync(
                new AppointmentEditedEvent(appointmentId)
        );

        return appointment;
    }

    public void deleteAppointment(Long appointmentId, Long actingUserId) {
        this.authorizationService.requireDeleteAppointment(appointmentId, actingUserId);

        Appointment appointment = Appointment.findById(appointmentId);
        if (appointment == null) {
            return;
        }
        appointment.setStatus(AppointmentStatus.DELETED);
        this.appointmentDeletedEvent.fireAsync(new AppointmentDeletedEvent(appointment.id, actingUserId));
    }

    public void cancelAppointment(Long appointmentId, Long actingUserId) {
        this.authorizationService.requireCancelAppointment(appointmentId, actingUserId);

        Appointment appointment = Appointment.findById(appointmentId);
        if (appointment == null) {
            return;
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        this.appointmentCancelledEvent.fireAsync(new AppointmentCancelledEvent(appointment.id, actingUserId));
    }

    public Appointment getAppointment(Long appointmentId, Long requestingUserId,
                                         boolean messages, boolean participants, boolean groupParticipants) {
        this.authorizationService.requireReadAppointment(appointmentId, requestingUserId);

        return this.appointmentQueryService.getAppointment(appointmentId, messages, participants, groupParticipants);

    }
}
