package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.FriendshipService;
import de.chronos_live.chronos_date_api.dto.FriendDto;
import de.chronos_live.chronos_date_api.dto.FriendshipRequestDto;
import de.chronos_live.chronos_date_api.dto.UserDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v2/friendships")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.friendships")
public class FriendshipResource {

    @Inject
    FriendshipService friendshipService;
    @Inject
    PrincipalContext principalContext;

    @GET
    @Path("/suggestions")
    public Response findSuggestions(@QueryParam("search") String search,
                                    @QueryParam("limit") @DefaultValue("20") int limit) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<UserDto> suggestions;
        if (search == null || search.trim().length() < 2) {
            suggestions = friendshipService.findRecentNonFriends(oidcId, 5);
        } else {
            suggestions = friendshipService.findNonFriends(search.trim(), oidcId, limit);
        }
        return Response.ok(suggestions).build();
    }

    @POST
    @Path("/requests")
    public Response sendRequest(@QueryParam("email") String email,
                                @QueryParam("adresse_id") String addresseeOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        friendshipService.sendFriendshipRequest(oidcId, addresseeOidcId, email);
        return Response.status(201).build();
    }

    @POST
    @Path("/requests/{requestId}/accept")
    public Response acceptRequest(@PathParam("requestId") Long requestId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        friendshipService.acceptFriendshipRequest(requestId, oidcId);
        return Response.ok().build();
    }

    @POST
    @Path("/requests/{requestId}/decline")
    public Response declineRequest(@PathParam("requestId") Long requestId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        friendshipService.declineFriendshipRequest(requestId, oidcId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/requests/{requestId}")
    public Response cancelRequest(@PathParam("requestId") Long requestId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        friendshipService.cancelFriendshipRequest(requestId, oidcId);
        return Response.noContent().build();
    }

    @GET
    @Path("/requests/incoming")
    public Response getIncomingRequests() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<FriendshipRequestDto> requests = friendshipService.getIncomingRequests(oidcId);
        return Response.ok(requests).build();
    }

    @GET
    @Path("/requests/outgoing")
    public Response getOutgoingRequests() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<FriendshipRequestDto> requests = friendshipService.getOutgoingRequests(oidcId);
        return Response.ok(requests).build();
    }

    @GET
    @Path("/friends")
    public Response getFriends() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<FriendDto> friends = friendshipService.getFriends(oidcId);
        return Response.ok(friends).build();
    }

    @DELETE
    @Path("/friends/{friendId}")
    public Response endFriendship(@PathParam("friendId") String friendOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        friendshipService.removeFriendship(oidcId, friendOidcId);
        return Response.noContent().build();
    }
}
