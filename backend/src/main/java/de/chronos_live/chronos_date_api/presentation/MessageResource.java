package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.MessageService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.mapper.MessageMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/appointments/{id}/messages")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.messages")
public class MessageResource {
    @Inject
    UserService userService;
    @Inject
    MessageService messageService;
    @Inject
    JsonWebToken jwt;
    @Inject
    MessageMapper messageMapper;

    @GET
    @Path("/")
    public Response getMessagesForEvent(@PathParam("id") Long appointmentId) {
        User user = this.userService.getUser(jwt.getSubject());

        List<Message> messages = this.messageService.getMessages(appointmentId, user.id);
        return Response.ok(messageMapper.toDtoList(messages)).build();
    }

    @POST
    @Path("/")
    public Response sendMessage(@PathParam("id") Long appointmentId, @RequestBody MessageDto messageDto) {
        User user = this.userService.getUser(jwt.getSubject());

        Message newMessage = this.messageService.sendMessage(appointmentId, messageDto.body(), user.id);

        return Response.ok(this.messageMapper.toDto(newMessage)).build();
    }
}
