package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v2/dates")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DatesResource {
    @GET
    @Path("/")
    public String getAgenda(@QueryParam("page") int page, @QueryParam("size") int size,
                            @QueryParam("start") String start, @QueryParam("end") String end) {
        return "";
    }

    @GET
    @Path("/{year}")
    public String getDates(@PathParam("year") int year) {
        return "";
    }

    @GET
    @Path("/{year}/{month}")
    public String getDates(@PathParam("year") int year, @PathParam("month") int month) {
        return "";
    }

    @GET
    @Path("/{year}/{month}/{dayOfMonth}")
    public String getDates(@PathParam("year") int year, @PathParam("month") int month, @PathParam("dayOfMonth") int dayOfMonth) {
        return "";
    }
}