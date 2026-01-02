package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
@Transactional
public class AppointmentParticipationService {
    @Inject
    AuthorizationService authorizationService;
    @Inject
    GroupService groupService;
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

    public void onAppointmentCreated(@ObservesAsync AppointmentCreatedEvent appointmentCreatedEvent) {
        AppointmentParticipation appointmentParticipation = new AppointmentParticipation();
        appointmentParticipation.setStatus(ParticipationStatus.PENDING);
        appointmentParticipation.setRole(UserRole.RESPONSIBLE);
        Appointment appointment = new Appointment();
        appointment.id = appointmentCreatedEvent.appointmentId();
        User creator = new User();
        creator.id = appointmentCreatedEvent.creatorId();
        appointmentParticipation.setAppointment(appointment);
        appointmentParticipation.setUser(creator);
        appointmentParticipation.persist();
    }

    public void onGroupMemberAdded(@ObservesAsync GroupMemberAddedEvent groupMemberAddedEvent) {
        User newMember = new User();
        newMember.id = groupMemberAddedEvent.newMemberId();

        List<AppointmentGroupParticipation> groupParticipationList =
                AppointmentGroupParticipation.find("group.id = ?1", groupMemberAddedEvent.groupId()).list();

        for (AppointmentGroupParticipation appointmentGroupParticipation : groupParticipationList) {
            if (AppointmentParticipation
                    .find("appointment.id = ?1 AND user.id = ?2",
                            appointmentGroupParticipation.getAppointment().id,
                            groupMemberAddedEvent.newMemberId()
                    )
                    .count() > 0) {
                continue;
            }
            AppointmentParticipation appointmentParticipation = new AppointmentParticipation();
            appointmentParticipation.setStatus(ParticipationStatus.PENDING);
            appointmentParticipation.setRole(appointmentGroupParticipation.getRole());
            appointmentParticipation.setAppointment(appointmentGroupParticipation.getAppointment());
            appointmentParticipation.setGroupParticipationId(groupMemberAddedEvent.groupId());
            appointmentParticipation.setUser(newMember);
            appointmentParticipation.persist();
        }
    }

    public void onGroupMemberRemoved(@ObservesAsync GroupMemberRemovedEvent event) {
        AppointmentParticipation
                .delete("groupParticipationId = ?1 AND user.id = ?2", event.groupId(), event.removedMemberId());
    }

    // User zu Termin hinzufügen
    public void addUserToAppointment(Long actingUserId, Long appointmentId, Long targetUserId, UserRole role) {
        this.authorizationService.requireAddUserToAppointment(appointmentId, actingUserId, targetUserId);

        if (AppointmentParticipation.find("appointment.id = ?1 AND user.id = ?2", appointmentId, targetUserId).count() > 0) {
            throw new ValidationException("This user is already a participant of this appointment");
        }

        AppointmentParticipation appointmentParticipation = new AppointmentParticipation();
        appointmentParticipation.setStatus(ParticipationStatus.PENDING);
        appointmentParticipation.setRole(role);
        Appointment appointment = new Appointment();
        appointment.id = appointmentId;
        User targetUser = new User();
        targetUser.id = targetUserId;
        appointmentParticipation.setAppointment(appointment);
        appointmentParticipation.setUser(targetUser);
        appointmentParticipation.persist();

        this.appointmentParticipationAddedEvent.fireAsync(
                new AppointmentParticipationAddedEvent(appointmentId, targetUserId, actingUserId)
        );
    }

    // Gruppe zu Termin hinzufügen (alle Gruppenmitglieder bekommen eine Rolle)
    public void addGroupToAppointment(Long actingUserId, Long appointmentId, Long groupId, UserRole role) {
        this.authorizationService.requireAddGroupToAppointment(appointmentId, actingUserId, groupId);

        if (AppointmentGroupParticipation.find("appointment.id = ?1 AND group.id = ?2", appointmentId, groupId).count() > 0) {
            throw new ValidationException("This group is already a participant of this appointment");
        }

        AppointmentGroupParticipation appointmentGroupParticipation = new AppointmentGroupParticipation();

        Appointment appointment = new Appointment();
        appointment.id = appointmentId;

        Group group = new Group();
        group.id = groupId;

        appointmentGroupParticipation.setGroup(group);
        appointmentGroupParticipation.setAppointment(appointment);
        appointmentGroupParticipation.setRole(role);

        appointmentGroupParticipation.persist();

        for (User user : this.groupService.getGroupUsers(actingUserId, groupId)) {
            if (AppointmentParticipation.find("appointment.id = ?1 AND user.id = ?2", appointmentId, user.id).count() > 0) {
                continue;
            }
            AppointmentParticipation appointmentParticipation = new AppointmentParticipation();
            appointmentParticipation.setStatus(ParticipationStatus.PENDING);
            appointmentParticipation.setRole(role);
            appointmentParticipation.setAppointment(appointment);
            appointmentParticipation.setUser(user);
            appointmentParticipation.setGroupParticipationId(groupId);
            appointmentParticipation.persist();
        }

        this.appointmentGroupParticipationAddedEvent.fireAsync(
                new AppointmentGroupParticipationAddedEvent(appointmentId, groupId, actingUserId)
        );
    }

    // Rolle ändern
    public void changeUserRole(Long actingUserId, Long appointmentId, Long targetUserId, UserRole newRole) {
        this.authorizationService.requireChangeParticipantRoleAtAppointment(appointmentId, actingUserId, targetUserId, newRole);

        if (newRole == null) {
            throw new BadRequestException("invalid role");
        }

        AppointmentParticipation appointmentParticipation =
                (AppointmentParticipation) AppointmentParticipation
                        .find("appointment.id = ?1 AND user.id = ?2", appointmentId, targetUserId)
                        .firstResultOptional()
                        .orElseThrow(() -> new ValidationException("This user is not a participant of this event"));

        if (newRole.equals(appointmentParticipation.getRole())) {
            throw new ValidationException("this is already the users role");
        }
        this.appointmentParticipationRoleChangedEvent.fireAsync(new AppointmentParticipationRoleChangedEvent(
                appointmentId,
                targetUserId,
                actingUserId,
                appointmentParticipation.getRole()
        ));
        appointmentParticipation.setRole(newRole);
    }

    // User entfernen
    public void removeUserFromAppointment(Long actingUserId, Long appointmentId, Long targetUserId) {
        this.authorizationService.requireRemoveParticipantFromAppointment(appointmentId, actingUserId, targetUserId);

        long removedParticipationCount = AppointmentParticipation
                .delete("appointment.id = ?1 AND user.id = ?2", appointmentId, targetUserId);
        if (removedParticipationCount < 1) {
            throw new ValidationException("This user is not a participant of this event");
        }

        this.appointmentParticipationRemovedEvent.fireAsync(
                new AppointmentParticipationRemovedEvent(appointmentId, targetUserId, actingUserId)
        );
    }

    // Gruppe entfernen
    public void removeGroupFromAppointment(Long actingUserId, Long appointmentId, Long groupId) {
        this.authorizationService.requireRemoveGroupFromAppointment(appointmentId, actingUserId, groupId);

        long deletedGroupParticipationCount = AppointmentGroupParticipation
                .delete("appointment.id = ?1 AND group.id = ?2", appointmentId, groupId);
        if (deletedGroupParticipationCount < 1) {
            throw new ValidationException("This group is not a participant of this appointment");
        }

        AppointmentParticipation.delete("groupParticipationId = ?1 AND appointment.id = ?2", groupId, appointmentId);

        this.appointmentGroupParticipationRemovedEvent.fireAsync(
                new AppointmentGroupParticipationRemovedEvent(appointmentId, groupId, actingUserId)
        );
    }

    // Teilnahmestatus ändern (User ändert seinen eigenen Status)
    public void changeParticipationStatus(Long userId, Long appointmentId, ParticipationStatus status) {
        this.authorizationService.requireReadAppointment(appointmentId, userId);

        if (status == null) {
            throw new BadRequestException("invalid participation status");
        }
        if (status.equals(ParticipationStatus.PENDING)) {
            throw new BadRequestException("you cannot set your participation status back to pending");
        }

        AppointmentParticipation appointmentParticipation =
                (AppointmentParticipation) AppointmentParticipation
                        .find("appointment.id = ?1 AND user.id = ?2", appointmentId, userId)
                        .firstResultOptional()
                        .orElseThrow(() -> new ValidationException("This user is not a participant of this event"));

        if (status.equals(appointmentParticipation.getStatus())) {
            throw new ValidationException("this is already your participation status");
        }

        this.appointmentParticipationStatusChangedEvent.fireAsync(
                new AppointmentParticipationStatusChangedEvent(appointmentId, userId, status, appointmentParticipation.getStatus())
        );

        appointmentParticipation.setStatus(status);
    }

    // Alle Teilnehmer abrufen
    public List<UserParticipantDto> getParticipants(Long appointmentId, Long requestingUserId) {
        this.authorizationService.requireReadAppointment(appointmentId, requestingUserId);

        List<AppointmentParticipation> appointmentParticipationList =
                AppointmentParticipation.list("appointment.id = ?1", appointmentId);

        return appointmentParticipationList
                .stream()
                .map(ap -> {
                    UserParticipantDto dto = new UserParticipantDto();

                    User user = ap.getUser();
                    dto.setUser_id(user.id);
                    dto.setName(user.getName());
                    dto.setProfile_picture_url(user.getProfilePictureUrl());

                    dto.setRole(ap.getRole());
                    dto.setStatus(ap.getStatus());

                    if (ap.getGroupParticipationId() != null) {
                        Group group = Group.findById(ap.getGroupParticipationId());
                        dto.setVia_group_id(group.id);
                        dto.setVia_group_name(group.getGroupName());
                    }

                    return dto;
                }).toList();
    }

    public List<GroupDto> getGroupParticipants(Long appointmentId, Long requestingUserId) {
        this.authorizationService.requireReadAppointment(appointmentId, requestingUserId);

        List<AppointmentGroupParticipation> appointmentGroupParticipationList =
                AppointmentGroupParticipation.list(
                        "SELECT agp FROM AppointmentGroupParticipation agp " +
                                "JOIN agp.group g JOIN g.members m " +
                                "WHERE agp.appointment.id = ?1 AND m.user.id = ?2",
                        appointmentId, requestingUserId);

        return appointmentGroupParticipationList
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