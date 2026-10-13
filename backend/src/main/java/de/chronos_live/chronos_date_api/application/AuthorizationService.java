package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import de.chronos_live.chronos_date_api.infrastructure.TeamRepository;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Timed("service.authorization")
public class AuthorizationService {
    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;
    @Inject
    GroupRepository groupRepository;
    @Inject
    TeamRepository teamRepository;
    @Inject
    PrincipalContext principalContext;

    private boolean isAdminRequest() {
        return principalContext.isAdminRequest();
    }

    public void requireReadAppointment(Long appointmentId, String requestingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, requestingUserOidcId);
        if (UserRole.NONE.equals(role)) {
            throw new ForbiddenException("You have no role at this appointment");
        }
    }

    public void requireUpdateAppointment(Long appointmentId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (role.ordinal() < UserRole.ATTENDANT.ordinal()) {
            throw new ForbiddenException("You have to be at least attendant of this appointment");
        }
    }

    public void requireDeleteAppointment(Long appointmentId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireCancelAppointment(Long appointmentId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireAddUserToAppointment(Long appointmentId, String actingUserOidcId, String targetUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
        if (!teamRepository.shareTeam(actingUserOidcId, targetUserOidcId)) {
            throw new ForbiddenException("Du kannst nur Mitglieder deines Teams einladen");
        }
    }

    public void requireAddGroupToAppointment(Long appointmentId, String actingUserOidcId, Long groupId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
        if (!groupRepository.isMember(groupId, actingUserOidcId)) {
            throw new ForbiddenException("You can only add groups, you're a member of");
        }
    }

    public void requireChangeParticipantRoleAtAppointment(Long appointmentId, String actingUserOidcId, String targetUserOidcId, UserRole targetRole) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireRemoveParticipantFromAppointment(Long appointmentId, String actingUserOidcId, String targetUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireRemoveGroupFromAppointment(Long appointmentId, String actingUserOidcId, Long groupId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireAddGroupMember(Long groupId, String actingUserOidcId, String targetUserOidcId) {
        if (isAdminRequest()) return;
        if (!groupRepository.isMember(groupId, actingUserOidcId)) {
            throw new ForbiddenException("You can only add members to groups you are a member of");
        }
        if (!teamRepository.shareTeam(actingUserOidcId, targetUserOidcId)) {
            throw new ForbiddenException("Du kannst nur Mitglieder deines Teams zur Gruppe hinzufügen");
        }
    }

    public void requireRemoveGroupMember(Long groupId, String actingUserOidcId, String targetUserOidcId) {
        if (isAdminRequest()) return;
        if (!groupRepository.isMember(groupId, actingUserOidcId)) {
            throw new ForbiddenException("Du bist kein Mitglied dieser Gruppe");
        }
    }

    public void requireReadGroupMembers(Long groupId, String requestingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupRepository.isMember(groupId, requestingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("You are not a member of this group");
    }

    public void requireEditGroup(Long groupId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupRepository.isMember(groupId, actingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("Du bist kein Mitglied dieser Gruppe und kannst sie nicht bearbeiten");
    }

    public void requireDeleteGroup(Long groupId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupRepository.isMember(groupId, actingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("Du bist kein Mitglied dieser Gruppe und kannst sie nicht löschen");
    }

    public void requireSendMessage(Long appointmentId, String requestingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, requestingUserOidcId);
        if (UserRole.NONE.equals(role)) {
            throw new ForbiddenException("You have no role at this appointment");
        }
    }
}
