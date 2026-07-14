package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AppointmentQueryService;
import de.chronos_live.chronos_date_api.application.AppointmentService;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.PagedResponse;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.mapper.AppointmentMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.time.Instant;

@Path("/api/v2/appointments")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.appointments")
public class AppointmentsResource {
    @Inject
    AppointmentService appointmentService;
    @Inject
    AppointmentQueryService appointmentQueryService;
    @Inject
    PrincipalContext principalContext;
    @Inject
    AppointmentMapper appointmentMapper;

    @GET
    @Path("/")
    public Response getAgenda(@QueryParam("page") Integer page, @QueryParam("size") Integer size,
                              @QueryParam("start") String start, @QueryParam("end") String end,
                              @QueryParam("search") String search, @QueryParam("participants") Boolean participants,
                              @QueryParam("messages") Boolean messages, @QueryParam("groups") Boolean groups) {
        String oidcId = principalContext.getPrincipal().oidcId();

        Instant after = start != null ? Instant.parse(start) : Instant.now();
        Instant before = end != null ? Instant.parse(end) : Instant.now().plusSeconds(60L * 60 * 24 * 365 * 1000);
        if (size == null) size = 10;
        if (page == null) page = 0;

        AppointmentQueryService.SearchResult result = appointmentQueryService.search(
                oidcId, search, after, before, page, size,
                messages != null && messages,
                participants != null && participants,
                groups != null && groups);

        var response = new PagedResponse<>(
                appointmentMapper.toDtoList(result.items()),
                new PagedResponse.Meta(page, size, result.total()));
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    public Response getAppointment(@PathParam("id") Long id,
                                   @QueryParam("participants") Boolean includeParticipants,
                                   @QueryParam("messages") Boolean includeMessages,
                                   @QueryParam("group_participants") Boolean includeGroupParticipants) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Appointment appointment = appointmentService.getAppointment(
                id, oidcId,
                includeMessages != null && includeMessages,
                includeParticipants != null && includeParticipants,
                includeGroupParticipants != null && includeGroupParticipants);
        return Response.ok(appointmentMapper.toDto(appointment)).build();
    }

    @POST
    @Path("/")
    public Response postEvent(@RequestBody CreateAppointmentDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Appointment created = appointmentService.createAppointment(dto, oidcId);
        return Response.status(Response.Status.CREATED).entity(appointmentMapper.toDto(created)).build();
    }

    @PATCH
    @Path("/{id}")
    public Response patchEvent(@RequestBody UpdateAppointmentDto dto, @PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Appointment updated = appointmentService.updateAppointment(appointmentId, oidcId, dto);
        return Response.ok(appointmentMapper.toDto(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteEvent(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentService.deleteAppointment(appointmentId, oidcId);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancelEvent(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        appointmentService.cancelAppointment(appointmentId, oidcId);
        return Response.ok().build();
    }
}
