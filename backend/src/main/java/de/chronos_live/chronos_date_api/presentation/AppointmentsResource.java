package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.*;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.mapper.*;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Path("/api/v2/appointments")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppointmentsResource {
    @Inject
    AppointmentService appointmentService;
    @Inject
    AppointmentQueryService appointmentQueryService;
    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    @Inject
    AppointmentMapper appointmentMapper;


    @GET
    @Path("/")
    public Response getAgenda(@QueryParam("page") Integer page, @QueryParam("size") Integer size,
                              @QueryParam("start") String start, @QueryParam("end") String end,
                              @QueryParam("search") String search, @QueryParam("participants") Boolean participants,
                              @QueryParam("messages") Boolean messages, @QueryParam("groups") Boolean groups) {
        User user = this.userService.getUser(jwt.getSubject());

        Instant after;
        if (start != null) {
            after = Instant.parse(start);
        } else {
            after = Instant.now();
        }
        Instant before;
        if (end != null) {
            before = Instant.parse(end);
        } else {
            before = Instant.now().plusSeconds(60L * 60 * 24 * 365 * 1000);
        }

        if (size == null) {
            size = 10;
        }
        if (page == null) {
            page = 0;
        }

        List<Appointment> appointmentList =
                this.appointmentQueryService.search(user.id, search, after, before,
                        page, size, messages, participants, groups);
        return Response.ok(this.appointmentMapper.toDtoList(appointmentList)).build();
    }

    @GET
    @Path("/{id}")
    public Response getAppointment(@PathParam("id") Long id, @QueryParam("participants") Boolean includeParticipants,
                                   @QueryParam("messages") Boolean includeMessages, @QueryParam("group_participants") Boolean includeGroupParticipants) {
        User user = userService.getUser(jwt.getSubject());

        if (includeMessages == null) {
            includeMessages = false;
        }
        if (includeGroupParticipants == null) {
            includeGroupParticipants = false;
        }
        if (includeParticipants == null) {
            includeParticipants = false;
        }

        Appointment appointment = appointmentService.getAppointment(id, user.id, includeMessages, includeParticipants, includeGroupParticipants);

        return Response.ok(this.appointmentMapper.toDto(appointment)).build();
    }

    @POST
    @Path("/")
    public Response postEvent(@RequestBody CreateAppointmentDto createAppointmentDto) {
        User user = userService.getUser(jwt.getSubject());

        Appointment createdAppointment = this.appointmentService.createAppointment(createAppointmentDto, user.id);

        return Response.status(Response.Status.CREATED).entity(this.appointmentMapper.toDto(createdAppointment)).build();
    }

    @PATCH
    @Path("/{id}")
    public Response patchEvent(@RequestBody UpdateAppointmentDto updateAppointmentDto, @PathParam("id") Long appointmentId) {
        User user = userService.getUser(jwt.getSubject());

        Appointment newAppointment = this.appointmentService.updateAppointment(appointmentId, user.id, updateAppointmentDto);
        return Response.ok(this.appointmentMapper.toDto(newAppointment)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteEvent(@PathParam("id") Long appointmentId) {
        User user = userService.getUser(jwt.getSubject());

        appointmentService.deleteAppointment(appointmentId, user.id);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancelEvent(@PathParam("id") Long appointmentId) {
        User user = userService.getUser(jwt.getSubject());

        this.appointmentService.cancelAppointment(appointmentId, user.id);
        return Response.ok().build();
    }
}
