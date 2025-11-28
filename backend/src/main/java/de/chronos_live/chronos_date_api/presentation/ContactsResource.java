package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.ContactService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.UserMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/v2/contacts")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContactsResource {

    @Inject
    ContactService contactService;
    @Inject
    UserService userService;
    @Inject
    UserMapper userMapper;
    @Inject
    JsonWebToken jwt;

    @GET
    public Response getContacts() {
        User user = this.userService.getUser(jwt.getSubject());

        List<User> contacts = this.contactService.getContacts(user);
        return Response.ok(userMapper.toDtoList(contacts)).build();
    }

    @POST
    public Response postContact(String contactEmail) {
        User user = this.userService.getUser(jwt.getSubject());

        try {
            this.contactService.addContact(user, contactEmail);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.status(Response.Status.CREATED).build();
    }
}