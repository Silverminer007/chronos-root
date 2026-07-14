package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.MessageService;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.mapper.MessageMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/appointments/{id}/messages")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.messages")
public class MessageResource {
    @Inject
    MessageService messageService;
    @Inject
    PrincipalContext principalContext;
    @Inject
    MessageMapper messageMapper;

    @GET
    @Path("/")
    public Response getMessagesForEvent(@PathParam("id") Long appointmentId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<Message> messages = messageService.getMessages(appointmentId, oidcId);
        return Response.ok(messageMapper.toDtoList(messages)).build();
    }

    @POST
    @Path("/")
    public Response sendMessage(@PathParam("id") Long appointmentId, @RequestBody MessageDto messageDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Message newMessage = messageService.sendMessage(appointmentId, messageDto.body(), oidcId);
        return Response.ok(messageMapper.toDto(newMessage)).build();
    }
}
