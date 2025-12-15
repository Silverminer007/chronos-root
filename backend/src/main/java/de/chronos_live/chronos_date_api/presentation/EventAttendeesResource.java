package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.EventAttendeeRole;
import de.chronos_live.chronos_date_api.domain.EventGroupAttendees;
import de.chronos_live.chronos_date_api.domain.EventUserAttendees;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.EventGroupAttendeesMapper;
import de.chronos_live.chronos_date_api.mapper.EventUserAttendeesMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/event/{id}/attendees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class EventAttendeesResource {
    @Inject
    UserService userService;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    JsonWebToken jwt;
    @Inject
    EventUserAttendeesMapper eventUserAttendeesMapper;
    @Inject
    EventGroupAttendeesMapper eventGroupAttendeesMapper;

    @GET
    @Path("/")
    public Response getEventAttendees(@PathParam("id") Long eventId) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<EventUserAttendees> userAttendees = this.eventAccessService.getUserAttendees(eventId);
        List<EventGroupAttendees> groupAttendees = this.eventAccessService.getGroupAttendees(eventId);
        EventAttendeesDto eventAttendeesDto = new EventAttendeesDto(
                this.eventGroupAttendeesMapper.toDtoListWithOwner(groupAttendees, user.id),
                this.eventUserAttendeesMapper.toDtoList(userAttendees)
        );
        return Response.ok(eventAttendeesDto).build();
    }

    @POST
    @Path("/group")
    public Response addGroupAttendee(@PathParam("id") Long eventId, @RequestBody EventGroupAttendeesDto groupAttendees) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        EventGroupAttendees eventGroupAttendees = this.eventGroupAttendeesMapper.toEntity(groupAttendees);
        try {
            this.eventAccessService.assignGroupToEvent(user, eventId, eventGroupAttendees.id, eventGroupAttendees.getRole());
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @POST
    @Path("/user")
    public Response addUserAttendee(@PathParam("id") Long eventId, @RequestBody EventUserAttendeesDto userAttendees) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        EventUserAttendees eventUserAttendees = this.eventUserAttendeesMapper.toEntity(userAttendees);
        try {
            this.eventAccessService.assignUserToEvent(user, eventId, eventUserAttendees.id, eventUserAttendees.getRole(), false);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @PATCH
    @Path("/group/{group_id}")
    public Response updateGroupAttendeesRole(@PathParam("id") Long eventId, @PathParam("group_id") Long groupId, @RequestBody EventGroupAttendeesDto groupAttendees) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        EventAttendeeRole newRole = EventAttendeeRole.valueOf(groupAttendees.role());
        try {
            this.eventAccessService.updateGroupEventRole(user, eventId, groupId, newRole);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @PATCH
    @Path("/user/{user_id}")
    public Response updateUserAttendeesRole(@PathParam("id") Long eventId, @PathParam("user_id") Long userId, @RequestBody EventUserAttendeesDto userAttendees) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        EventAttendeeRole newRole = EventAttendeeRole.valueOf(userAttendees.role());
        try {
            this.eventAccessService.updateUserEventRole(user, eventId, userId, newRole);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @DELETE
    @Path("/group/{groupId}")
    public Response removeGroupAttendee(@PathParam("id") Long eventId, @PathParam("groupId") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            this.eventAccessService.unassignGroupToEvent(user, eventId, groupId);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }

    @DELETE
    @Path("/user/{userId}")
    public Response removeUserAttendee(@PathParam("id") Long eventId, @PathParam("userId") Long userId) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            this.eventAccessService.unassignUserToEvent(user, eventId, userId);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok().build();
    }
}
