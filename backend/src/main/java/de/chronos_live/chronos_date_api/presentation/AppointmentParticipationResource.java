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

@Path("/api/v2/appointments/{id}/participants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Timed("api.participation")
public class AppointmentParticipationResource {
    @Inject
    PrincipalContext principalContext;
    @Inject
    AppointmentParticipationService appointmentParticipationService;
    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @GET
    @Path("/")
    public Response getParticipants(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<UserParticipantDto> list = appointmentParticipationService.getParticipants(appointmentId, oidcId);
        return Response.ok(list).build();
    }

    @GET
    @Path("/status")
    public Response getParticipationStatus(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        ParticipationStatus status = appointmentParticipationQueryService.getUserStatus(appointmentId, oidcId);
        AppointmentDto dto = new AppointmentDto();
        dto.setStatus(status.toString());
        return Response.ok(dto).build();
    }

    @POST
    @Path("/approve")
    public Response approveAppointment(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.changeParticipationStatus(oidcId, appointmentId, ParticipationStatus.APPROVED);
        return Response.ok().build();
    }

    @POST
    @Path("/reject")
    public Response rejectAppointment(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.changeParticipationStatus(oidcId, appointmentId, ParticipationStatus.REJECTED);
        return Response.ok().build();
    }

    @POST
    @Path("/users")
    public Response addUserParticipant(@PathParam("id") Long appointmentId,
                                       @RequestBody AddParticipantDto addParticipantDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole userRole = UserRole.valueOf(addParticipantDto.getUser_role());
        appointmentParticipationService.addUserToAppointment(oidcId, appointmentId, addParticipantDto.getUser_id(), userRole);
        return Response.ok().build();
    }

    @POST
    @Path("/groups")
    public Response addGroupParticipant(@PathParam("id") Long appointmentId,
                                        @RequestBody AddGroupParticipantDto addGroupParticipantDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole role = UserRole.valueOf(addGroupParticipantDto.getUser_role());
        appointmentParticipationService.addGroupToAppointment(oidcId, appointmentId, addGroupParticipantDto.getGroup_id(), role);
        return Response.ok().build();
    }

    @PATCH
    @Path("/users/{userId}")
    public Response changeParticipantRole(@PathParam("id") Long appointmentId,
                                          @PathParam("userId") String targetUserOidcId,
                                          @RequestBody ParticipantRoleDto roleDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UserRole role = UserRole.valueOf(roleDto.getRole());
        appointmentParticipationService.changeUserRole(oidcId, appointmentId, targetUserOidcId, role);
        return Response.ok().build();
    }

    @DELETE
    @Path("/users/{userId}")
    public Response removeParticipant(@PathParam("id") Long appointmentId,
                                      @PathParam("userId") String targetUserOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.removeUserFromAppointment(oidcId, appointmentId, targetUserOidcId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/groups/{groupId}")
    public Response removeGroupParticipant(@PathParam("id") Long appointmentId,
                                           @PathParam("groupId") Long groupId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentParticipationService.removeGroupFromAppointment(oidcId, appointmentId, groupId);
        return Response.ok().build();
    }
}
