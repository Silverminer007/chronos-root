package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AttendanceStatusService;
import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.EventService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Attendance;
import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.EventAttendeeRole;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.AttendanceMapper;
import de.chronos_live.chronos_date_api.mapper.EventMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/event")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {
    private final EventService eventService;
    private final EventAccessService eventAccessService;
    private final UserService userService;
    private final AttendanceStatusService attendanceStatusService;

    @Inject
    JsonWebToken jwt;

    @Inject
    EventMapper eventMapper;

    @Inject
    AttendanceMapper attendanceMapper;

    public EventResource(EventService eventService, EventAccessService eventAccessService, UserService userService, AttendanceStatusService attendanceStatusService) {
        this.eventService = eventService;
        this.eventAccessService = eventAccessService;
        this.userService = userService;
        this.attendanceStatusService = attendanceStatusService;
    }

    @GET
    @Path("/{id}")
    public Response getEvent(@PathParam("id") int id, @QueryParam("attendances") Boolean includeAttendances) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventService.getEvent(id);

        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!eventAccessService.userHasAccessToEvent(user, event)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
        Attendance ownAttendance = this.attendanceStatusService.getAttendanceStatus(user, event.id);

        if(includeAttendances != null && includeAttendances) {
            EventDto eventDto = eventMapper.toDto(event)
                    .withOwnAttendanceStatus(ownAttendance.getStatus().name())
                    .withAttendances(attendanceMapper.toDtoList(attendances));
            return Response.ok(eventDto).build();
        } else {
            return Response.ok(eventMapper.toDto(event)).build();
        }
    }

    @POST
    @Path("/")
    public Response postEvent(@RequestBody EventDto eventDto) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventMapper.toEntity(eventDto);
        try {
            this.eventService.createEvent(event);
            this.eventAccessService.assignUserToEvent(user, event.id, user.id, EventAttendeeRole.RESPONSIBLE, true);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        return Response.status(Response.Status.CREATED).entity(eventMapper.toDto(event)).build();
    }

    @PATCH
    @Path("/{id}")
    public Response patchEvent(@RequestBody EventDto eventDto, @PathParam("id") Long id) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventMapper.toEntity(eventDto);
        event.id = id;

        if (!eventAccessService.userHasAccessToEvent(user, event)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            this.eventService.updateEvent(event);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        return Response.ok(eventMapper.toDto(event)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteEvent(@PathParam("id") int id) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!eventAccessService.userHasAccessToEvent(user, event)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        eventService.deleteEvent(event);
        return Response.ok().build();
    }

    // TODO Cancel Event
}
