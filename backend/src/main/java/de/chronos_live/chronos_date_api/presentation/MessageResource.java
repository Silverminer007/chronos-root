package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.MessageService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.NotificationCategory;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.MessageMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/event/{id}/messages")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {
    @Inject
    UserService userService;
    @Inject
    MessageService messageService;
    @Inject
    JsonWebToken jwt;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    MessageMapper messageMapper;

    @GET
    @Path("/")
    public Response getMessagesForEvent(@PathParam("id") Long eventId) {
        User user = this.userService.getUser(jwt.getSubject());

        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<Message> messages = this.messageService.getMessages(eventId);
        return Response.ok(messageMapper.toDtoList(messages)).build();
    }

    @POST
    @Path("/")
    public Response sendMessage(@PathParam("id") Long eventId, @RequestBody MessageDto messageDto) {
        User user = this.userService.getUser(jwt.getSubject());
        if (!this.eventAccessService.userHasAccessToEvent(user, eventId)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        this.messageService.sendMessage(eventId, messageDto.title(), messageDto.message(), user, NotificationCategory.MESSAGE);
        return Response.ok().build();
    }
}
