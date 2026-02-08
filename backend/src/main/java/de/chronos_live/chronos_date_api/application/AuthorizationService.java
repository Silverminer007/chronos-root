package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

@ApplicationScoped
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

    @Timed(value = "service.authorization.requireReadAppointment", description = "Time for read-appointment authorization check")
    public void requireReadAppointment(Long appointmentId, Long requestingUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, requestingUserId);

        if(UserRole.NONE.equals(role)) {
            throw new ForbiddenException("You have no role at this appointment");
        }
    }

    public void requireUpdateAppointment(Long appointmentId, Long actingUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(role.ordinal() < UserRole.ATTENDANT.ordinal()) {
            throw new ForbiddenException("You have to be at least attendant of this appointment");
        }
    }

    public void requireDeleteAppointment(Long appointmentId, Long actingUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireCancelAppointment(Long appointmentId, Long actingUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireAddUserToAppointment(Long appointmentId, Long actingUserId, Long targetUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }

        if(!this.friendshipQueryService.areFriends(actingUserId, targetUserId)) {
            throw new ForbiddenException("You can only add friends to appointments");
        }
    }

    public void requireAddGroupToAppointment(Long appointmentId, Long actingUserId, Long groupId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }

        if(!this.groupQueryService.isGroupMember(groupId, actingUserId)) {
            throw new ForbiddenException("You can only add groups, you're a member of");
        }
    }

    public void requireChangeParticipantRoleAtAppointment(Long appointmentId, Long actingUserId, Long targetUserId, UserRole targetRole) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireRemoveParticipantFromAppointment(Long appointmentId, Long actingUserId, Long targetUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireRemoveGroupFromAppointment(Long appointmentId, Long actingUserId, Long groupId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, actingUserId);

        if(!UserRole.RESPONSIBLE.equals(role)) {
            throw new ForbiddenException("You have to be responsible for this appointment");
        }
    }

    public void requireAddGroupMember(Long groupId, Long actingUserId, Long targetUserId) {
        if (isAdminRequest()) return;
        if(!this.groupQueryService.isGroupMember(groupId, actingUserId)) {
            throw new ForbiddenException("You can only add members to group you are a member of");
        }

        if(!this.friendshipQueryService.areFriends(actingUserId, targetUserId)) {
            throw new ForbiddenException("You can only add friends to groups");
        }
    }

    public void requireRemoveGroupMember(Long groupId, Long actingUserId, Long targetUserId) {
        if (isAdminRequest()) return;
        if(Objects.equals(actingUserId, targetUserId)) {
            throw new ForbiddenException("You cannot remove yourself from a group");
        }
        if(this.groupQueryService.isGroupOwner(groupId, actingUserId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can remove members");

    }

    public void requireReadGroupMembers(Long groupId, Long requestingUserId) {
        if (isAdminRequest()) return;
        if(this.groupQueryService.isGroupMember(groupId, requestingUserId)) {
            return;
        }
        throw new ForbiddenException("You are not a member of this group");
    }

    public void requireEditGroup(Long groupId, Long actingUserId) {
        if (isAdminRequest()) return;
        if(this.groupQueryService.isGroupOwner(groupId, actingUserId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can edit a group");
    }

    public void requireDeleteGroup(Long groupId, Long actingUserId) {
        if (isAdminRequest()) return;
        if(this.groupQueryService.isGroupOwner(groupId, actingUserId)) {
            return;
        }
        throw new ForbiddenException("Only the group owner can delete a group");
    }

    public void requireSendMessage(Long appointmentId, Long requestingUserId) {
        if (isAdminRequest()) return;
        UserRole role = this.appointmentParticipationQueryService.getUserRole(appointmentId, requestingUserId);

        if(UserRole.NONE.equals(role)) {
            throw new ForbiddenException("You have no role at this appointment");
        }
    }
}