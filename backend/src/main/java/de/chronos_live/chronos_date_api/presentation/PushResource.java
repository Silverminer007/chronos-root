package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.PushSubscriptionService;
import de.chronos_live.chronos_date_api.application.WebPushService;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionDto;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionStatusDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
    PrincipalContext principalContext;

    @GET
    @Path("/public-key")
    public Response getPublicKey() {
        return Response.ok(webPushService.getPublicKey()).build();
    }

    @POST
    @Path("/subscribe")
    public void subscribe(PushSubscriptionDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        subscriptionService.saveSubscription(oidcId, dto);
    }

    @GET
    @Path("/status")
    public Response status(@QueryParam("endpoint") String endpoint) {
        return Response.ok(new PushSubscriptionStatusDto(subscriptionService.isSubscriptionKnown(endpoint))).build();
    }

    @DELETE
    @Path("/unsubscribe")
    public void unsubscribe(@QueryParam("endpoint") String endpoint) {
        subscriptionService.deleteByEndpoint(endpoint);
    }
}
