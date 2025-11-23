package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AttendanceStatusService;
import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Attendance;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.AttendanceMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/event/{id}/attendance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class EventAttendanceResource {
    @Inject
    UserService userService;
    @Inject
    AttendanceStatusService attendanceStatusService;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    JsonWebToken jwt;

    @Inject
    AttendanceMapper mapper;

    @GET
    @Path("/")
    public Response getAttendances(@PathParam("id") Long eventId) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(eventId);
        return Response.ok(mapper.toDtoList(attendances)).build();
    }

    @GET
    @Path("/own")
    public Response getOwnAttendanceStatus(@PathParam("id") Long eventId) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Attendance attendance = this.attendanceStatusService.getAttendanceStatus(user, eventId);
        return Response.ok(mapper.toDto(attendance)).build();
    }

    @POST
    @Path("/")
    public Response postAttendances(@PathParam("id") Long eventId, @RequestBody AttendanceDto attendanceDto) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            this.attendanceStatusService.setAttendanceStatus(user, eventId,
                    this.attendanceStatusService.getAttendanceStatus(attendanceDto.status()));
            return Response.ok().build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
