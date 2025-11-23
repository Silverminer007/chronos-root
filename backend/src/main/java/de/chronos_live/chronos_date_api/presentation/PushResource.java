package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.PushSubscriptionService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.application.WebPushService;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/push")
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
    JsonWebToken jwt;

    @POST
    @Path("/subscribe")
    public void subscribe(PushSubscriptionDto dto) {
        User user = this.userService.getUser(this.jwt.getSubject());
        subscriptionService.saveSubscription(user.id, dto);
    }

    @DELETE
    @Path("/unsubscribe")
    public void unsubscribe(@QueryParam("endpoint") String endpoint) {
        subscriptionService.deleteByEndpoint(endpoint);
    }

    @POST
    @Path("/test/{userId}")
    public void sendTest(@PathParam("userId") Long userId) {
        webPushService.sendToUser(
                userId,
                "Testbenachrichtigung",
                "Push funktioniert!"
        );
    }
}