package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.SettingsService;
import de.chronos_live.chronos_date_api.domain.Settings;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.mapper.SettingsMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v2/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.settings")
public class SettingsResource {

    @Inject
    SettingsService settingsService;
    @Inject
    SettingsMapper settingsMapper;
    @Inject
    PrincipalContext principalContext;

    @GET
    public Response getMySettings() {
        String oidcId = principalContext.getPrincipal().oidcId();
        Settings settings = settingsService.getOrCreateSettings(oidcId);
        return Response.ok(settingsMapper.toDto(settings)).build();
    }

    @PUT
    public Response updateMySettings(SettingsDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        settingsService.updateSettings(oidcId, dto);
        return Response.ok().build();
    }
}
