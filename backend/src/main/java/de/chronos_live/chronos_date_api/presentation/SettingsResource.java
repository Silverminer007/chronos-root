package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.SettingsService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Settings;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.mapper.SettingsMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/v2/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {

    @Inject
    SettingsService settingsService;

    @Inject
    SettingsMapper settingsMapper;

    @Inject
    UserService userService;
    @Inject
    JsonWebToken jwt;

    @GET
    public Response getMySettings() {
        User user = this.userService.getUser(jwt.getSubject());
        Settings settings = this.settingsService.getOrCreateSettings(user.id);
        return Response.ok(settingsMapper.toDto(settings)).build();
    }

    @PUT
    public Response updateMySettings(SettingsDto dto) {
        User user = this.userService.getUser(jwt.getSubject());
        this.settingsService.updateSettings(user.id, dto);
        return Response.ok().build();
    }
}