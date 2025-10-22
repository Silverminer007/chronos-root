package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/api/v2/date")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DateResource {

    @GET
    @Path("/{id}")
    public String getDate(@PathParam("id") int id) {
        return "";
    }

    @POST
    @Path("/")
    public String postDate(@RequestBody String date) {
        return "";
    }

    @PATCH
    @Path("/{id}")
    public String patchDate(@RequestBody String date, @PathParam("id") int id) {
        return "";
    }

    @DELETE
    @Path("/{id}")
    public String deleteDate(@PathParam("id") int id) {
        return "";
    }
}
