package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AppointmentParticipationQueryService;
import de.chronos_live.chronos_date_api.application.AppointmentParticipationService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.*;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/appointments/{id}/participants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class AppointmentParticipationResource {
    @Inject
    UserService userService;
    @Inject
    AppointmentParticipationService appointmentParticipationService;
    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;
    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/")
    public Response getParticipants(@PathParam("id") Long appointmentId) {
        User user = this.userService.getUser(jwt.getSubject());

        List<UserParticipantDto> userParticipantDtoList =
                this.appointmentParticipationService.getParticipants(appointmentId, user.id);
        return Response.ok(userParticipantDtoList).build();
    }

    @GET
    @Path("/status")
    public Response getParticipationStatus(@PathParam("id") Long appointmentId) {
        User user = this.userService.getUser(jwt.getSubject());

        ParticipationStatus participationStatus =
                this.appointmentParticipationQueryService.getUserStatus(appointmentId, user.id);
        AppointmentDto appointmentDto = new  AppointmentDto();
        appointmentDto.setStatus(participationStatus.toString());
        return Response.ok(appointmentDto).build();
    }

    @POST
    @Path("/approve")
    @Timed(value = "api.participation.approve", description = "Time for the full approve participation request")
    public Response approveAppointment(@PathParam("id") Long appointmentId) {
        User user = this.userService.getUser(jwt.getSubject());

        this.appointmentParticipationService
                .changeParticipationStatus(user.id, appointmentId, ParticipationStatus.APPROVED);
        return Response.ok().build();
    }

    @POST
    @Path("/reject")
    @Timed(value = "api.participation.reject", description = "Time for the full reject participation request")
    public Response rejectAppointment(@PathParam("id") Long appointmentId) {
        User user = this.userService.getUser(jwt.getSubject());

        this.appointmentParticipationService
                .changeParticipationStatus(user.id, appointmentId, ParticipationStatus.REJECTED);
        return Response.ok().build();
    }

    @POST
    @Path("/users")
    public Response addUserParticipant(@PathParam("id") Long appointmentId,
                                       @RequestBody AddParticipantDto addParticipantDto) {
        User user = this.userService.getUser(jwt.getSubject());

        UserRole userRole = UserRole.valueOf(addParticipantDto.getUser_role());

        this.appointmentParticipationService
                .addUserToAppointment(user.id, appointmentId, addParticipantDto.getUser_id(), userRole);
        return Response.ok().build();
    }

    @POST
    @Path("/groups")
    public Response addGroupParticipant(@PathParam("id") Long appointmentId,
                                        @RequestBody AddGroupParticipantDto addGroupParticipantDto) {
        User user = this.userService.getUser(jwt.getSubject());

        UserRole role = UserRole.valueOf(addGroupParticipantDto.getUser_role());

        this.appointmentParticipationService
                .addGroupToAppointment(user.id, appointmentId, addGroupParticipantDto.getGroup_id(), role);
        return Response.ok().build();
    }

    @PATCH
    @Path("/users/{userId}")
    public Response changeParticipantRole(@PathParam("id") Long appointmentId, @PathParam("userId") Long userId,
                                          @RequestBody ParticipantRoleDto roleDto) {
        User user = this.userService.getUser(jwt.getSubject());

        UserRole role = UserRole.valueOf(roleDto.getRole());

        this.appointmentParticipationService.changeUserRole(user.id, appointmentId, userId, role);

        return Response.ok().build();
    }

    @DELETE
    @Path("/users/{userId}")
    public Response removeParticipant(@PathParam("id") Long appointmentId, @PathParam("userId") Long userId) {
        User user = this.userService.getUser(jwt.getSubject());

        this.appointmentParticipationService.removeUserFromAppointment(user.id, appointmentId, userId);

        return Response.ok().build();
    }

    @DELETE
    @Path("/groups/{groupId}")
    public Response removeGroupParticipant(@PathParam("id") Long appointmentId, @PathParam("groupId") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());

        this.appointmentParticipationService.removeGroupFromAppointment(user.id, appointmentId, groupId);

        return Response.ok().build();
    }
}
