package de.chronos_live.admin.presentation;

import de.chronos_live.chronos_date_api.application.WebPushService;
import de.chronos_live.chronos_date_api.domain.PushNotificationLog;
import de.chronos_live.chronos_date_api.dto.PushNotificationLogDto;
import de.chronos_live.chronos_date_api.infrastructure.PushNotificationLogRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/push")
@Timed("api.admin.push")
public class AdminPushResource {

    @Inject
    WebPushService webPushService;

    @Inject
    PushNotificationLogRepository pushNotificationLogRepository;

    @POST
    @Path("/test/{userId}")
    @RolesAllowed("ADMIN_API")
    public void sendTest(@PathParam("userId") String userId, @RequestBody String payload) {
        if(payload == null || payload.isEmpty()) {
            webPushService.sendToUser(
                    userId,
                    """
                            "Testbenachrichtigung"
                            """,
                    """
                            "Push" funktioniert!
                            """
            );
        } else {
            webPushService.sendNotification(userId, payload);
        }
    }

    @GET
    @Path("/log")
    @RolesAllowed("ADMIN_API")
    public Response getNotificationLog(
            @QueryParam("user_id") String userId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("success") Boolean success,
            @QueryParam("notification_type") String notificationType,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {

        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;

        List<PushNotificationLogDto> logs = pushNotificationLogRepository
                .findFiltered(userId, fromInstant, toInstant, success, notificationType, page, size)
                .stream()
                .map(this::toDto)
                .toList();

        return Response.ok(logs).build();
    }

    private PushNotificationLogDto toDto(PushNotificationLog entry) {
        return new PushNotificationLogDto(
                entry.id,
                entry.getUserOidcId(),
                entry.getNotificationType(),
                entry.getPayload(),
                entry.getEndpoint(),
                entry.getHttpStatusCode(),
                entry.isSuccess(),
                entry.getErrorMessage(),
                entry.getCreatedAt() != null ? entry.getCreatedAt().toInstant().toString() : null
        );
    }
}
