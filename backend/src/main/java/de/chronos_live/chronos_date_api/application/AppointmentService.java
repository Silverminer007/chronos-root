package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
@Transactional
@Timed("service.appointment")
public class AppointmentService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentService.class);
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

    public Appointment createAppointment(CreateAppointmentDto dto, String creatorOidcId) {
        Appointment appointment = new Appointment();
        if (dto.getName().isBlank()) {
            throw new ValidationException("name", "Name cannot be blank");
        }
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            appointment.setDescription(dto.getDescription());
        }
        if (dto.getVenue() != null && !dto.getVenue().isBlank()) {
            appointment.setVenue(dto.getVenue());
        }
        appointment.setName(dto.getName());
        if (dto.getEnd() == null) throw new ValidationException("end", "End cannot be null");
        appointment.setEndTime(Instant.parse(dto.getEnd()));
        if (dto.getStart() == null) throw new ValidationException("start", "start cannot be null");
        appointment.setStartTime(Instant.parse(dto.getStart()));
        if (appointment.getEndTime().isBefore(appointment.getStartTime())) {
            throw new ValidationException("end", "End date cannot be before start date");
        }
        if (dto.getMinimal_attendees() != null && dto.getMinimal_attendees() < 0) {
            throw new ValidationException("minimal_attendees", "Minimal attendees must be positive");
        }
        appointment.setMinimalAttendees(dto.getMinimal_attendees());
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatedAt(Instant.now());
        appointment.setLastUpdate(Instant.now());
        appointment.persist();

        LOGGER.debugf("[Principal %s][Appointment %s] Created Appointment", creatorOidcId, appointment.id);
        appointmentCreatedEvent.fire(new AppointmentCreatedEvent(appointment.id, creatorOidcId));
        return appointment;
    }

    public Appointment updateAppointment(Long appointmentId, String actingUserOidcId, UpdateAppointmentDto dto) {
        LOGGER.debugf("[Principal %s][Appointment %s] Update Appointment", actingUserOidcId, appointmentId);

        authorizationService.requireUpdateAppointment(appointmentId, actingUserOidcId);
        Appointment appointment = getAppointment(appointmentId, actingUserOidcId, true, true, true);

        if (dto.getName() != null) {
            if (dto.getName().isBlank()) throw new ValidationException("name", "Name cannot be blank");
            appointment.setName(dto.getName());
        }
        if (dto.getDescription() != null) appointment.setDescription(dto.getDescription());
        if (dto.getVenue() != null) appointment.setVenue(dto.getVenue());

        Instant oldStartTime = appointment.getStartTime();
        Instant oldEndTime = appointment.getEndTime();
        Instant newStartTime = appointment.getStartTime();
        Instant newEndTime = appointment.getEndTime();
        if (dto.getStart() != null && dto.getEnd() != null) {
            newStartTime = Instant.parse(dto.getStart());
            newEndTime = Instant.parse(dto.getEnd());
        } else if (dto.getStart() != null) {
            newStartTime = Instant.parse(dto.getStart());
        } else if (dto.getEnd() != null) {
            newEndTime = Instant.parse(dto.getEnd());
        }
        if (newEndTime.isBefore(newStartTime)) {
            throw new ValidationException("end", "Start date cannot be after end date");
        }
        if (!oldStartTime.equals(newStartTime) || !oldEndTime.equals(newEndTime)) {
            appointmentMovedEvent.fire(new AppointmentMovedEvent(appointment.id, oldStartTime, oldEndTime, actingUserOidcId));
        }
        appointment.setStartTime(newStartTime);
        appointment.setEndTime(newEndTime);
        if (dto.getMinimal_attendees() != null) {
            appointment.setMinimalAttendees(dto.getMinimal_attendees());
        }
        appointment.setLastUpdate(Instant.now());
        appointmentEditedEvent.fire(new AppointmentEditedEvent(appointmentId));
        return appointment;
    }

    public void deleteAppointment(Long appointmentId, String actingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Delete Appointment", actingUserOidcId, appointmentId);
        authorizationService.requireDeleteAppointment(appointmentId, actingUserOidcId);
        Appointment appointment = Appointment.findById(appointmentId);
        if (appointment == null) return;
        appointment.setStatus(AppointmentStatus.DELETED);
        appointmentDeletedEvent.fire(new AppointmentDeletedEvent(appointment.id, actingUserOidcId));
    }

    public void cancelAppointment(Long appointmentId, String actingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Cancel Appointment", actingUserOidcId, appointmentId);
        authorizationService.requireCancelAppointment(appointmentId, actingUserOidcId);
        Appointment appointment = Appointment.findById(appointmentId);
        if (appointment == null) return;
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentCancelledEvent.fire(new AppointmentCancelledEvent(appointment.id, actingUserOidcId));
    }

    public Appointment getAppointment(Long appointmentId, String requestingUserOidcId,
                                      boolean messages, boolean participants, boolean groupParticipants) {
        LOGGER.debugf("[Principal %s][Appointment %s] Read Appointment", requestingUserOidcId, appointmentId);
        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);
        Appointment appointment = appointmentQueryService.getAppointment(appointmentId, messages, participants, groupParticipants);
        if (!messages) appointment.setMessages(null);
        if (!participants) appointment.setParticipants(null);
        if (!groupParticipants) appointment.setGroupParticipants(null);
        return appointment;
    }
}
