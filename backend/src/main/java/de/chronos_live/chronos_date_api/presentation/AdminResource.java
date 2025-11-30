package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AdminService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v2/admin")
@RolesAllowed("ADMIN_API")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {
    @Inject
    AdminService adminService;

    @GET
    @Path("/events/count")
    public Response getEventCount() {
        return Response.ok(this.adminService.countEvents()).build();
    }

    @GET
    @Path("/statistics")
    public Response getStatistics() {
        AdminStatisticsDto statisticsDto = adminService.getStatistics();
        return Response.ok(statisticsDto).build();
    }
}
