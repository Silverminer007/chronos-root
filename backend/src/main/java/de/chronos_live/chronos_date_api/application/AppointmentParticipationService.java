package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentParticipationRepository;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
@Timed("service.participation")
public class AppointmentParticipationService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentParticipationService.class);
    @Inject
    AuthorizationService authorizationService;
    @Inject
    GroupService groupService;
    @Inject
    IdentityPort identityPort;
    @Inject
    AppointmentRepository appointmentRepository;
    @Inject
    AppointmentParticipationRepository participationRepository;
    @Inject
    GroupRepository groupRepository;
    @Inject
    Event<AppointmentParticipationStatusChangedEvent> appointmentParticipationStatusChangedEvent;
    @Inject
    Event<AppointmentParticipationRoleChangedEvent> appointmentParticipationRoleChangedEvent;
    @Inject
    Event<AppointmentParticipationAddedEvent> appointmentParticipationAddedEvent;
    @Inject
    Event<AppointmentParticipationRemovedEvent> appointmentParticipationRemovedEvent;
    @Inject
    Event<AppointmentGroupParticipationAddedEvent> appointmentGroupParticipationAddedEvent;
    @Inject
    Event<AppointmentGroupParticipationRemovedEvent> appointmentGroupParticipationRemovedEvent;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentCreatedEvent event) {
        AppointmentParticipation participation = new AppointmentParticipation();
        participation.setStatus(ParticipationStatus.PENDING);
        participation.setRole(UserRole.RESPONSIBLE);
        participation.setAppointment(appointmentRepository.findById(event.appointmentId()));
        participation.setUserOidcId(event.creatorOidcId());
        participationRepository.persist(participation);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onGroupMemberAdded(@Observes GroupMemberAddedEvent event) {
        List<AppointmentGroupParticipation> groupParticipations =
                participationRepository.listGroupParticipationsByGroup(event.groupId());

        for (AppointmentGroupParticipation agp : groupParticipations) {
            if (participationRepository.existsByAppointmentAndUser(
                    agp.getAppointment().id, event.newMemberOidcId())) continue;

            AppointmentParticipation participation = new AppointmentParticipation();
            participation.setStatus(ParticipationStatus.PENDING);
            participation.setRole(agp.getRole());
            participation.setAppointment(agp.getAppointment());
            participation.setGroupParticipationId(event.groupId());
            participation.setUserOidcId(event.newMemberOidcId());
            participationRepository.persist(participation);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onGroupMemberRemoved(@Observes GroupMemberRemovedEvent event) {
        participationRepository.deleteByGroupAndUser(event.groupId(), event.removedMemberOidcId());
    }

    public void addUserToAppointment(String actingUserOidcId, Long appointmentId, String targetUserOidcId, UserRole role) {
        LOGGER.debugf("[Principal %s][Appointment %s][User %s][Role %s] Adding User Participant",
                actingUserOidcId, appointmentId, targetUserOidcId, role);

        authorizationService.requireAddUserToAppointment(appointmentId, actingUserOidcId, targetUserOidcId);

        if (participationRepository.existsByAppointmentAndUser(appointmentId, targetUserOidcId)) {
            throw new ValidationException("This user is already a participant of this appointment");
        }

        AppointmentParticipation participation = new AppointmentParticipation();
        participation.setStatus(ParticipationStatus.PENDING);
        participation.setRole(role);
        participation.setAppointment(appointmentRepository.findById(appointmentId));
        participation.setUserOidcId(targetUserOidcId);
        participationRepository.persist(participation);

        appointmentParticipationAddedEvent.fire(
                new AppointmentParticipationAddedEvent(appointmentId, targetUserOidcId, actingUserOidcId));
    }

    public void addGroupToAppointment(String actingUserOidcId, Long appointmentId, Long groupId, UserRole role) {
        LOGGER.debugf("[Principal %s][Appointment %s][Group %s][Role %s] Adding Group Participant",
                actingUserOidcId, appointmentId, groupId, role);

        authorizationService.requireAddGroupToAppointment(appointmentId, actingUserOidcId, groupId);

        if (participationRepository.existsGroupParticipation(appointmentId, groupId)) {
            throw new ValidationException("This group is already a participant of this appointment");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId);
        Group group = groupRepository.findById(groupId);

        AppointmentGroupParticipation agp = new AppointmentGroupParticipation();
        agp.setGroup(group);
        agp.setAppointment(appointment);
        agp.setRole(role);
        participationRepository.persistGroupParticipation(agp);

        for (UserIdentity user : groupService.getGroupUsers(actingUserOidcId, groupId)) {
            if (participationRepository.existsByAppointmentAndUser(appointmentId, user.oidcId())) continue;

            AppointmentParticipation participation = new AppointmentParticipation();
            participation.setStatus(ParticipationStatus.PENDING);
            participation.setRole(role);
            participation.setAppointment(appointment);
            participation.setUserOidcId(user.oidcId());
            participation.setGroupParticipationId(groupId);
            participationRepository.persist(participation);
        }

        appointmentGroupParticipationAddedEvent.fire(
                new AppointmentGroupParticipationAddedEvent(appointmentId, groupId, actingUserOidcId));
    }

    public void changeUserRole(String actingUserOidcId, Long appointmentId, String targetUserOidcId, UserRole newRole) {
        LOGGER.debugf("[Principal %s][Appointment %s][User %s][Role %s] Changing Role",
                actingUserOidcId, appointmentId, targetUserOidcId, newRole);

        authorizationService.requireChangeParticipantRoleAtAppointment(appointmentId, actingUserOidcId, targetUserOidcId, newRole);

        if (newRole == null) throw new BadRequestException("invalid role");

        AppointmentParticipation participation = participationRepository
                .findByAppointmentAndUser(appointmentId, targetUserOidcId)
                .orElseThrow(() -> new ValidationException("This user is not a participant of this event"));

        if (newRole.equals(participation.getRole())) {
            throw new ValidationException("this is already the users role");
        }

        appointmentParticipationRoleChangedEvent.fire(
                new AppointmentParticipationRoleChangedEvent(
                        appointmentId, targetUserOidcId, actingUserOidcId, participation.getRole()));
        participation.setRole(newRole);
    }

    public void removeUserFromAppointment(String actingUserOidcId, Long appointmentId, String targetUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s][User %s] Removing Participant",
                actingUserOidcId, appointmentId, targetUserOidcId);

        authorizationService.requireRemoveParticipantFromAppointment(appointmentId, actingUserOidcId, targetUserOidcId);

        long removed = participationRepository.deleteByAppointmentAndUser(appointmentId, targetUserOidcId);
        if (removed < 1) {
            throw new ValidationException("This user is not a participant of this event");
        }

        appointmentParticipationRemovedEvent.fire(
                new AppointmentParticipationRemovedEvent(appointmentId, targetUserOidcId, actingUserOidcId));
    }

    public void removeGroupFromAppointment(String actingUserOidcId, Long appointmentId, Long groupId) {
        LOGGER.debugf("[Principal %s][Appointment %s][Group %s] Removing Group Participant",
                actingUserOidcId, appointmentId, groupId);

        authorizationService.requireRemoveGroupFromAppointment(appointmentId, actingUserOidcId, groupId);

        long deleted = participationRepository.deleteGroupParticipation(appointmentId, groupId);
        if (deleted < 1) {
            throw new ValidationException("This group is not a participant of this appointment");
        }
        participationRepository.deleteByGroupAndAppointment(groupId, appointmentId);

        appointmentGroupParticipationRemovedEvent.fire(
                new AppointmentGroupParticipationRemovedEvent(appointmentId, groupId, actingUserOidcId));
    }

    public void changeParticipationStatus(String userOidcId, Long appointmentId, ParticipationStatus status) {
        LOGGER.debugf("[Principal %s][Appointment %s][RSVP %s] RSVP Changed", userOidcId, appointmentId, status);

        authorizationService.requireReadAppointment(appointmentId, userOidcId);

        if (status == null) throw new BadRequestException("invalid participation status");
        if (status.equals(ParticipationStatus.PENDING)) {
            throw new BadRequestException("you cannot set your participation status back to pending");
        }

        AppointmentParticipation participation = participationRepository
                .findByAppointmentAndUser(appointmentId, userOidcId)
                .orElseThrow(() -> new ValidationException("This user is not a participant of this event"));

        if (status.equals(participation.getStatus())) {
            throw new ValidationException("this is already your participation status");
        }

        ParticipationStatus oldStatus = participation.getStatus();
        participation.setStatus(status);

        AppointmentParticipationStatusChangedEvent event =
                new AppointmentParticipationStatusChangedEvent(appointmentId, userOidcId, status, oldStatus);
        appointmentParticipationStatusChangedEvent.fire(event);
        appointmentParticipationStatusChangedEvent.fireAsync(event);
    }

    public List<UserParticipantDto> getParticipants(Long appointmentId, String requestingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading participants", requestingUserOidcId, appointmentId);

        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);

        List<AppointmentParticipation> participations = participationRepository.listByAppointment(appointmentId);

        Map<String, UserIdentity> userMap = identityPort.findByIds(
                participations.stream().map(AppointmentParticipation::getUserOidcId).toList()
        );

        return participations.stream()
                .map(ap -> {
                    UserIdentity user = userMap.get(ap.getUserOidcId());
                    UserParticipantDto dto = new UserParticipantDto();
                    if (user != null) {
                        dto.setUser_id(user.oidcId());
                        dto.setName(user.getName());
                        dto.setProfile_picture_url(user.profilePictureUrl());
                    } else {
                        dto.setUser_id(ap.getUserOidcId());
                    }
                    dto.setRole(ap.getRole());
                    dto.setStatus(ap.getStatus());
                    if (ap.getGroupParticipationId() != null) {
                        Group group = groupRepository.findById(ap.getGroupParticipationId());
                        dto.setVia_group_id(group.id);
                        dto.setVia_group_name(group.getGroupName());
                    }
                    return dto;
                })
                .toList();
    }

    public List<GroupDto> getGroupParticipants(Long appointmentId, String requestingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading Group Participants", requestingUserOidcId, appointmentId);

        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);

        return participationRepository.listGroupParticipationsForUser(appointmentId, requestingUserOidcId)
                .stream()
                .map(agp -> {
                    GroupDto dto = new GroupDto();
                    dto.setId(agp.getGroup().id);
                    dto.setName(agp.getGroup().getGroupName());
                    return dto;
                })
                .toList();
    }
}
