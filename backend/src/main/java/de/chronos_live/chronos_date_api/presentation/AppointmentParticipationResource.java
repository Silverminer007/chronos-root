package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AppointmentParticipationQueryService;
import de.chronos_live.chronos_date_api.application.AppointmentParticipationService;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.*;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

/**
 * Participation/RSVP endpoints for appointments.
 *
 * Handles:
 * - User RSVP status changes (yes/maybe/no)
 * - Role management (organizer/participant)
 * - Participant management (add/remove users and groups)
 *
 * Fires events for all state changes with complete information including:
 * - Actor (current user who made the change)
 * - Old/new status for change events
 *
 * Enforces authorization:
 * - Only users can RSVP to appointments they're invited to
 * - Only appointment organizers can add/remove participants or change roles
 */
@PermitAll
@Timed("api.participation")
public class AppointmentParticipationResource {
    @Inject
    PrincipalContext principalContext;
    @Inject
    AppointmentParticipationService appointmentParticipationService;
    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    /**
     * POST /api/v2/appointments/{id}/participation
     *
     * RSVP endpoint - change current user's participation status.
     *
     * Only users with PENDING status can RSVP.
     * Status can be: APPROVED (yes), REJECTED (no).
     *
     * Fires: AppointmentParticipationStatusChangedEvent with:
     * - Actor: current user
     * - Old status: previous participation status
     * - New status: new participation status
     *
     * @param appointmentId the appointment ID
     * @param statusDto contains the new participation status (APPROVED or REJECTED)
     * @return 200 OK on success
     */
    @POST
    @Path("/api/v2/appointments/{id}/participation")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response rsvpToAppointment(@PathParam("id") Long appointmentId,
                                      @RequestBody ParticipationStatusDto statusDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        ParticipationStatus status = ParticipationStatus.valueOf(statusDto.getStatus());
        appointmentParticipationService.changeParticipationStatus(oidcId, appointmentId, status);
        return Response.ok().build();
    }

    /**
     * PUT /api/v2/appointments/{id}/participation/{userId}/role
     *
     * Change a participant's role.
     *
     * Only appointment organizer can change roles.
     * Role can be: ORGANIZER, PARTICIPANT, RESPONSIBLE.
     *
     * Fires: AppointmentParticipationRoleChangedEvent with:
     * - Actor: current user (organizer)
     * - Target user: participant whose role is being changed
     * - Old role: previous role
     *
     * @param appointmentId the appointment ID
     * @param targetUserOidcId the OIDC ID of the participant to update
     * @param roleDto contains the new role
     * @return 200 OK on success
     */
    @PUT
    @Path("/api/v2/appointments/{id}/participation/{userId}/role")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeParticipationRole(@PathParam("id") Long appointmentId,
                                            @PathParam("userId") String targetUserOidcId,
                                            @RequestBody ParticipantRoleDto roleDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole role = UserRole.valueOf(roleDto.getRole());
        appointmentParticipationService.changeUserRole(oidcId, appointmentId, targetUserOidcId, role);
        return Response.ok().build();
    }

    /**
     * POST /api/v2/appointments/{id}/participants/{userId}
     *
     * Add a participant to an appointment.
     *
     * Only appointment organizer can add participants.
     * New participants start with PENDING status.
     *
     * Fires: AppointmentParticipationAddedEvent with:
     * - Actor: current user (organizer)
     * - Target user: participant being added
     *
     * @param appointmentId the appointment ID
     * @param targetUserOidcId the OIDC ID of the user to add
     * @param addParticipantDto contains the role for the new participant
     * @return 200 OK on success
     */
    @POST
    @Path("/api/v2/appointments/{id}/participants/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addParticipant(@PathParam("id") Long appointmentId,
                                   @PathParam("userId") String targetUserOidcId,
                                   @RequestBody AddParticipantDto addParticipantDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole userRole = UserRole.valueOf(addParticipantDto.getUser_role());
        appointmentParticipationService.addUserToAppointment(oidcId, appointmentId, targetUserOidcId, userRole);
        return Response.ok().build();
    }

    /**
     * DELETE /api/v2/appointments/{id}/participants/{userId}
     *
     * Remove a participant from an appointment.
     *
     * Only appointment organizer can remove participants.
     *
     * Fires: AppointmentParticipationRemovedEvent with:
     * - Actor: current user (organizer)
     * - Target user: participant being removed
     *
     * @param appointmentId the appointment ID
     * @param targetUserOidcId the OIDC ID of the participant to remove
     * @return 200 OK on success
     */
    @DELETE
    @Path("/api/v2/appointments/{id}/participants/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeParticipant(@PathParam("id") Long appointmentId,
                                      @PathParam("userId") String targetUserOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.removeUserFromAppointment(oidcId, appointmentId, targetUserOidcId);
        return Response.ok().build();
    }

    /**
     * POST /api/v2/appointments/{id}/groups/{groupId}
     *
     * Add a group to an appointment.
     *
     * Only appointment organizer can add groups.
     * All current members of the group will be added as participants.
     * Future group members will be automatically added when they join the group.
     *
     * Fires: AppointmentGroupParticipationAddedEvent with:
     * - Actor: current user (organizer)
     * - Group ID: group being added
     *
     * @param appointmentId the appointment ID
     * @param groupId the ID of the group to add
     * @param addGroupParticipantDto contains the role for group members
     * @return 200 OK on success
     */
    @POST
    @Path("/api/v2/appointments/{id}/groups/{groupId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addGroup(@PathParam("id") Long appointmentId,
                             @PathParam("groupId") Long groupId,
                             @RequestBody AddGroupParticipantDto addGroupParticipantDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole role = UserRole.valueOf(addGroupParticipantDto.getUser_role());
        appointmentParticipationService.addGroupToAppointment(oidcId, appointmentId, groupId, role);
        return Response.ok().build();
    }

    /**
     * DELETE /api/v2/appointments/{id}/groups/{groupId}
     *
     * Remove a group from an appointment.
     *
     * Only appointment organizer can remove groups.
     * All members of the group will be removed as participants.
     *
     * Fires: AppointmentGroupParticipationRemovedEvent with:
     * - Actor: current user (organizer)
     * - Group ID: group being removed
     *
     * @param appointmentId the appointment ID
     * @param groupId the ID of the group to remove
     * @return 200 OK on success
     */
    @DELETE
    @Path("/api/v2/appointments/{id}/groups/{groupId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeGroup(@PathParam("id") Long appointmentId,
                                @PathParam("groupId") Long groupId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.removeGroupFromAppointment(oidcId, appointmentId, groupId);
        return Response.ok().build();
    }

    /**
     * GET /api/v2/appointments/{id}/participants
     *
     * Get all participants for an appointment.
     *
     * Includes user information (name, profile picture) and participation status/role.
     * Participants added via groups include the group information (via_group_id, via_group_name).
     *
     * @param appointmentId the appointment ID
     * @return 200 OK with list of UserParticipantDto objects
     */
    @GET
    @Path("/api/v2/appointments/{id}/participants")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getParticipants(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<UserParticipantDto> list = appointmentParticipationService.getParticipants(appointmentId, oidcId);
        return Response.ok(list).build();
    }

    /**
     * GET /api/v2/appointments/{id}/participation/status
     *
     * Get current user's participation status for an appointment.
     *
     * Status can be:
     * - PENDING: User has not yet responded
     * - APPROVED: User confirmed attendance (yes)
     * - REJECTED: User declined attendance (no)
     *
     * @param appointmentId the appointment ID
     * @return 200 OK with participation status in response body
     */
    @GET
    @Path("/api/v2/appointments/{id}/participation/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getParticipationStatus(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        ParticipationStatus status = appointmentParticipationQueryService.getUserStatus(appointmentId, oidcId);
        AppointmentDto dto = new AppointmentDto();
        dto.setStatus(status.toString());
        return Response.ok(dto).build();
    }
}
