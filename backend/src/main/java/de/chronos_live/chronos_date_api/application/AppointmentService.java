package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.AppointmentDto;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.UserDto;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
@Timed("service.appointment")
public class AppointmentService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentService.class);
    @Inject
    AuthorizationService authorizationService;
    @Inject
    AppointmentRepository appointmentRepository;
    @Inject
    GroupRepository groupRepository;
    @Inject
    IdentityPort identityPort;
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
        appointmentRepository.persist(appointment);

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
        Appointment appointment = appointmentRepository.findById(appointmentId);
        if (appointment == null) return;
        appointment.setStatus(AppointmentStatus.DELETED);
        appointmentDeletedEvent.fire(new AppointmentDeletedEvent(appointment.id, actingUserOidcId));
    }

    public void cancelAppointment(Long appointmentId, String actingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Cancel Appointment", actingUserOidcId, appointmentId);
        authorizationService.requireCancelAppointment(appointmentId, actingUserOidcId);
        Appointment appointment = appointmentRepository.findById(appointmentId);
        if (appointment == null) return;
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentCancelledEvent.fire(new AppointmentCancelledEvent(appointment.id, actingUserOidcId));
    }

    public Appointment getAppointment(Long appointmentId, String requestingUserOidcId,
                                      boolean messages, boolean participants, boolean groupParticipants) {
        LOGGER.debugf("[Principal %s][Appointment %s] Read Appointment", requestingUserOidcId, appointmentId);
        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);
        Appointment appointment = appointmentRepository.getAppointment(appointmentId, messages, participants, groupParticipants);
        if (!messages) appointment.setMessages(null);
        if (!participants) appointment.setParticipants(null);
        if (!groupParticipants) appointment.setGroupParticipants(null);
        return appointment;
    }

    public void enrichAppointmentDtos(List<AppointmentDto> dtos) {
        // Collect all unique oidcIds across participants, message senders, and group members
        Set<String> allOidcIds = new HashSet<>();
        dtos.stream()
                .filter(d -> d.getParticipants() != null)
                .flatMap(d -> d.getParticipants().stream())
                .map(UserParticipantDto::getUser_id)
                .filter(Objects::nonNull)
                .forEach(allOidcIds::add);
        dtos.stream()
                .filter(d -> d.getMessages() != null)
                .flatMap(d -> d.getMessages().stream())
                .map(MessageDto::sender_id)
                .filter(Objects::nonNull)
                .forEach(allOidcIds::add);
        dtos.stream()
                .filter(d -> d.getGroup_participants() != null)
                .flatMap(d -> d.getGroup_participants().stream())
                .filter(g -> g.getMembers() != null)
                .flatMap(g -> g.getMembers().stream())
                .map(UserDto::id)
                .filter(Objects::nonNull)
                .forEach(allOidcIds::add);

        if (allOidcIds.isEmpty()) return;

        Map<String, UserIdentity> userMap = identityPort.findByIds(new ArrayList<>(allOidcIds));

        // Batch-fetch group names for via_group_id references
        Set<Long> viaGroupIds = dtos.stream()
                .filter(d -> d.getParticipants() != null)
                .flatMap(d -> d.getParticipants().stream())
                .map(UserParticipantDto::getVia_group_id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> groupNameMap = new HashMap<>();
        if (!viaGroupIds.isEmpty()) {
            groupRepository.findByIds(viaGroupIds)
                    .forEach(g -> groupNameMap.put(g.id, g.getGroupName()));
        }

        for (AppointmentDto dto : dtos) {
            // Enrich individual participants
            if (dto.getParticipants() != null) {
                for (UserParticipantDto p : dto.getParticipants()) {
                    UserIdentity u = userMap.get(p.getUser_id());
                    if (u != null) {
                        p.setName(u.getName());
                        p.setProfile_picture_url(u.profilePictureUrl());
                    }
                    if (p.getVia_group_id() != null) {
                        p.setVia_group_name(groupNameMap.get(p.getVia_group_id()));
                    }
                }
            }

            // Enrich embedded message sender names (MessageDto is a record — replace instances)
            if (dto.getMessages() != null) {
                dto.setMessages(dto.getMessages().stream()
                        .map(m -> {
                            UserIdentity sender = userMap.get(m.sender_id());
                            if (sender == null) return m;
                            return new MessageDto(m.id(), m.sender_id(), sender.getName(),
                                    m.appointment_id(), m.body(), m.timestamp());
                        })
                        .toList());
            }

            // Enrich group participant member names (UserDto is a record — replace instances)
            if (dto.getGroup_participants() != null) {
                for (GroupDto groupDto : dto.getGroup_participants()) {
                    if (groupDto.getMembers() == null) continue;
                    groupDto.setMembers(groupDto.getMembers().stream()
                            .map(m -> {
                                UserIdentity u = userMap.get(m.id());
                                return u != null ? new UserDto(m.id(), u.firstName(), u.lastName()) : m;
                            })
                            .toList());
                }
            }
        }
    }
}
