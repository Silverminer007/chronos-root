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
import io.micrometer.core.annotation.Timed;
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
@Timed("api.push")
public class PushResource {

    @Inject
    PushSubscriptionService subscriptionService;

    @Inject
    WebPushService webPushService;

    @Inject
    UserService userService;

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
}