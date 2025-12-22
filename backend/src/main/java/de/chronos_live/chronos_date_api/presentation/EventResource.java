package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.*;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.mapper.*;
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
    @Inject
    EventService eventService;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    UserService userService;
    @Inject
    AttendanceStatusService attendanceStatusService;
    @Inject
    MessageService messageService;

    @Inject
    JsonWebToken jwt;

    @Inject
    EventMapper eventMapper;

    @Inject
    AttendanceMapper attendanceMapper;

    @Inject
    MessageMapper messageMapper;

    @Inject
    EventUserAttendeesMapper eventUserAttendeesMapper;

    @Inject
    EventGroupAttendeesMapper eventGroupAttendeesMapper;

    @GET
    @Path("/{id}")
    public Response getEvent(@PathParam("id") Long id, @QueryParam("attendances") Boolean includeAttendances,
                             @QueryParam("messages") Boolean includeMessages, @QueryParam("attendees") Boolean includeAttendees) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventService.getEvent(id);

        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!eventAccessService.userHasAccessToEvent(user, event)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EventDto eventDto = eventMapper.toDto(event);

        if (includeAttendances != null && includeAttendances) {
            List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
            Attendance ownAttendance = this.attendanceStatusService.getAttendanceStatus(user, event.id);
            eventDto.setOwn_attendance_status(ownAttendance.getStatus().name());
            eventDto.setAttendances(attendanceMapper.toDtoList(attendances));
        }

        if(includeMessages != null && includeMessages) {
            List<Message> messages = this.messageService.getMessages(id);
            eventDto.setMessages(messageMapper.toDtoList(messages));
        }

        if(includeAttendees != null && includeAttendees) {
            List<EventUserAttendees> eventUserAttendeesList = this.eventAccessService.getUserAttendees(id);
            List<EventGroupAttendees> eventGroupAttendeesList = this.eventAccessService.getGroupAttendees(id);
            eventDto.setUserAttendees(this.eventUserAttendeesMapper.toDtoList(eventUserAttendeesList));
            eventDto.setGroupAttendees(this.eventGroupAttendeesMapper.toDtoListWithOwner(eventGroupAttendeesList, user.id));
        }

        return Response.ok(eventDto).build();
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
            Event newEvent = this.eventService.updateEvent(event);
            return Response.ok(eventMapper.toDto(newEvent)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
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

        eventService.deleteEvent(event.id);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancelEvent(@PathParam("id") Long id) {
        User user = userService.getUser(jwt.getSubject());

        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!eventAccessService.userHasAccessToEvent(user, event)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        this.eventService.cancelEvent(id, user);
        return Response.ok().build();
    }
}
