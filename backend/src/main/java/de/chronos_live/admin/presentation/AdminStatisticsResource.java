package de.chronos_live.admin.presentation;

import de.chronos_live.admin.application.AdminStatisticsService;
import de.chronos_live.chronos_date_api.dto.AdminStatisticsDto;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v2/admin/statistics")
@RolesAllowed("ADMIN_API")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.admin.statistics")
public class AdminStatisticsResource {

    @Inject
    AdminStatisticsService adminStatisticsService;

    @GET
    @Path("/")
    public Response getStatistics() {
        AdminStatisticsDto statistics = adminStatisticsService.getStatistics();
        return Response.ok(statistics).build();
    }
}
