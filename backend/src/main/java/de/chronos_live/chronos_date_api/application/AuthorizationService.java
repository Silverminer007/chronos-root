package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

@ApplicationScoped
@Timed("service.authorization")
public class AuthorizationService {
    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;
    @Inject
    GroupQueryService groupQueryService;
    @Inject
    FriendshipQueryService friendshipQueryService;
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
        if (!friendshipQueryService.areFriends(actingUserOidcId, targetUserOidcId)) {
            throw new ForbiddenException("You can only add friends to appointments");
        }
    }

    public void requireAddGroupToAppointment(Long appointmentId, String actingUserOidcId, Long groupId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, actingUserOidcId);
        if (!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
        if (!groupQueryService.isGroupMember(groupId, actingUserOidcId)) {
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
        if (!groupQueryService.isGroupMember(groupId, actingUserOidcId)) {
            throw new ForbiddenException("You can only add members to group you are a member of");
        }
        if (!friendshipQueryService.areFriends(actingUserOidcId, targetUserOidcId)) {
            throw new ForbiddenException("You can only add friends to groups");
        }
    }

    public void requireRemoveGroupMember(Long groupId, String actingUserOidcId, String targetUserOidcId) {
        if (isAdminRequest()) return;
        if (Objects.equals(actingUserOidcId, targetUserOidcId)) {
            throw new ForbiddenException("You cannot remove yourself from a group");
        }
        if (groupQueryService.isGroupOwner(groupId, actingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can remove members");
    }

    public void requireReadGroupMembers(Long groupId, String requestingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupQueryService.isGroupMember(groupId, requestingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("You are not a member of this group");
    }

    public void requireEditGroup(Long groupId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupQueryService.isGroupOwner(groupId, actingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can edit a group");
    }

    public void requireDeleteGroup(Long groupId, String actingUserOidcId) {
        if (isAdminRequest()) return;
        if (groupQueryService.isGroupOwner(groupId, actingUserOidcId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can delete a group");
    }

    public void requireSendMessage(Long appointmentId, String requestingUserOidcId) {
        if (isAdminRequest()) return;
        UserRole role = appointmentParticipationQueryService.getUserRole(appointmentId, requestingUserOidcId);
        if (UserRole.NONE.equals(role)) {
            throw new ForbiddenException("You have no role at this appointment");
        }
    }
}
