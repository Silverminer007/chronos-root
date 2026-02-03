package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.PushSubscriptionService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.application.WebPushService;
import de.chronos_live.chronos_date_api.domain.PushNotificationLog;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.PushNotificationLogDto;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionDto;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionStatusDto;
import de.chronos_live.chronos_date_api.infrastructure.PushNotificationLogRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.List;

@Path("/api/v2/push")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PushResource {

    @Inject
    PushSubscriptionService subscriptionService;

    @Inject
    WebPushService webPushService;

    @Inject
    UserService userService;

    @Inject
    PushNotificationLogRepository pushNotificationLogRepository;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/public-key")
    public Response getPublicKey() {
        return Response.ok(this.webPushService.getPublicKey()).build();
    }

    @POST
    @Path("/subscribe")
    public void subscribe(PushSubscriptionDto dto) {
        User user = this.userService.getUser(this.jwt.getSubject());
        subscriptionService.saveSubscription(user.id, dto);
    }

    @GET
    @Path("/status")
    public Response status(@QueryParam("endpoint") String endpoint) {
        PushSubscriptionStatusDto dto = new  PushSubscriptionStatusDto(this.subscriptionService.isSubscriptionKnown(endpoint));
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/unsubscribe")
    public void unsubscribe(@QueryParam("endpoint") String endpoint) {
        subscriptionService.deleteByEndpoint(endpoint);
    }

    @POST
    @Path("/test/{userId}")
    @RolesAllowed("ADMIN_API")
    public void sendTest(@PathParam("userId") Long userId) {
        webPushService.sendToUser(
                userId,
                """
                        "Testbenachrichtigung"
                        """,
                """
                        "Push" funktioniert!
                        """
        );
    }

    @GET
    @Path("/log")
    @RolesAllowed("ADMIN_API")
    public Response getNotificationLog(
            @QueryParam("user_id") Long userId,
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
                entry.getUserId(),
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