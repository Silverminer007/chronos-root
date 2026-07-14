package de.chronos_live.admin.presentation;

import de.chronos_live.admin.dto.AdminAddGroupParticipantDto;
import de.chronos_live.admin.dto.AdminChangeParticipationStatusDto;
import de.chronos_live.chronos_date_api.application.AppointmentParticipationService;
import de.chronos_live.chronos_date_api.application.AppointmentService;
import de.chronos_live.chronos_date_api.application.MessageService;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.mapper.AppointmentMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.time.Instant;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/appointments")
@Timed("api.admin.appointments")
public class AdminAppointmentResource {
    @Inject
    PrincipalContext principalContext;
    @Inject
    AppointmentService appointmentService;
    @Inject
    AppointmentParticipationService appointmentParticipationService;
    @Inject
    MessageService messageService;
    @Inject
    AppointmentMapper appointmentMapper;

    @POST
    @Path("/")
    public Response createAppointment(@RequestBody CreateAppointmentDto appointmentDto) {
        Appointment createdAppointment = this.appointmentService.createAppointment(appointmentDto, this.principalContext.getPrincipal().oidcId());
        return Response.ok(this.appointmentMapper.toDto(createdAppointment)).build();
    }

    @POST
    @Path("/{appointmentId}/participants/groups")
    public Response addGroupParticipant(@PathParam("appointmentId") Long appointmentId, @RequestBody AdminAddGroupParticipantDto dto) {
        this.appointmentParticipationService
                .addGroupToAppointment(
                        this.principalContext.getPrincipal().oidcId(),
                        appointmentId,
                        dto.getGroup_id(),
                        UserRole.valueOf(dto.getUser_role())
                );
        return Response.ok().build();
    }

    @POST
    @Path("/participants/status")
    public Response changeParticipationStatus(@RequestBody AdminChangeParticipationStatusDto dto) {
        this.appointmentParticipationService
                .changeParticipationStatus(
                        dto.getUserId(),
                        dto.getAppointmentId(),
                        ParticipationStatus.valueOf(dto.getParticipationStatus())
                );
        return Response.ok().build();
    }

    @POST
    @Path("/message")
    public Response sendMessage(@RequestBody MessageDto dto) {
        this.messageService.sendMessage(dto.appointment_id(), dto.body(), dto.sender_id(), Instant.parse(dto.timestamp()));
        return Response.ok().build();
    }
}
