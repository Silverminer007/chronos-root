package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v2/date/{id}/attendance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DateAttendanceResource {
    @GET
    @Path("/")
    @PermitAll
    public String getAttendances(@PathParam("id") int id) {
        return "";
    }

    @GET
    @Path("/{username}")
    @PermitAll
    public String getAttendances(@PathParam("id") int id, @PathParam("username") String username) {
        return "";
    }

    @POST
    @Path("/{username}")
    @PermitAll
    public String postAttendances(@PathParam("id") int id, @PathParam("username") String username) {
        return "";
    }
}
