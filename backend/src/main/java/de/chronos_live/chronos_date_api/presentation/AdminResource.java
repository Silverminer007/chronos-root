package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/v2/admin")
@RolesAllowed("ADMIN_API")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {
    private static final Logger log = Logger.getLogger(AdminResource.class);

    @GET
    @Path("/users")
    public String getAllUsers() {
        return "";
    }

    @GET
    @Path("/groups")
    public String getAllGroups() {
        return "";
    }

    @GET
    @Path("/dates")
    public String getAllDates() {
        return "";
    }

    @POST
    @Path("/import")
    public Response importFrom(ExportObjectDto exportObjectDto) {
        return Response.ok().build();
    }

    @GET
    @Path("/export")
    public Response export() {
        return Response.ok().build();
    }
}
