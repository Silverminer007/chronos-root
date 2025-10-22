package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/api/v2/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class UserResource {

    @GET
    public String getUser() {
        return "";
    }

    @PATCH
    public String patchUser(@RequestBody String user) {
        return "";
    }

    @DELETE
    public String deleteUser() {
        return "";
    }
}