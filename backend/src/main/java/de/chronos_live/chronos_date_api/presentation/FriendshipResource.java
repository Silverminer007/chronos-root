package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.FriendshipService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.FriendDto;
import de.chronos_live.chronos_date_api.dto.FriendshipRequestDto;
import de.chronos_live.chronos_date_api.dto.UserDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/v2/friendships")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FriendshipResource {

    @Inject
    FriendshipService friendshipService;

    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/suggestions")
    public Response findSuggestions(@QueryParam("search") String search) {
        User user = this.userService.getUser(jwt.getSubject());

        List<UserDto> suggestions = this.friendshipService.findNonFriends(search, user.id);

        return Response.ok(suggestions).build();
    }

    /**
     * Sendet Freundschaftsanfrage
     */
    @POST
    @Path("/requests")
    public Response sendRequest(@QueryParam("email") String email, @QueryParam("adresse_id") Long addresseeId) {
        User user = this.userService.getUser(jwt.getSubject());

        friendshipService.sendFriendshipRequest(user.id, addresseeId, email);

        return Response.status(201).build();
    }

    /**
     * Nimmt Freundschaftsanfrage an
     */
    @POST
    @Path("/requests/{requestId}/accept")
    public Response acceptRequest(@PathParam("requestId") Long requestId) {
        User user = this.userService.getUser(jwt.getSubject());

        friendshipService.acceptFriendshipRequest(requestId, user.id);

        return Response.ok().build();
    }

    /**
     * Lehnt Freundschaftsanfrage ab
     */
    @POST
    @Path("/requests/{requestId}/decline")
    public Response declineRequest(@PathParam("requestId") Long requestId) {
        User user = this.userService.getUser(jwt.getSubject());

        friendshipService.declineFriendshipRequest(requestId, user.id);

        return Response.ok().build();
    }

    /**
     * Zieht eigene Freundschaftsanfrage zurück
     */
    @DELETE
    @Path("/requests/{requestId}")
    public Response cancelRequest(@PathParam("requestId") Long requestId) {
        User user = this.userService.getUser(jwt.getSubject());

        friendshipService.cancelFriendshipRequest(requestId, user.id);

        return Response.noContent().build();
    }

    /**
     * Lädt eingehende Anfragen
     */
    @GET
    @Path("/requests/incoming")
    public Response getIncomingRequests() {
        User user = this.userService.getUser(jwt.getSubject());

        List<FriendshipRequestDto> requests =
                friendshipService.getIncomingRequests(user.id);

        return Response.ok(requests).build();
    }

    /**
     * Lädt ausgehende Anfragen
     */
    @GET
    @Path("/requests/outgoing")
    public Response getOutgoingRequests() {
        User user = this.userService.getUser(jwt.getSubject());

        List<FriendshipRequestDto> requests =
                friendshipService.getOutgoingRequests(user.id);

        return Response.ok(requests).build();
    }

    @GET
    @Path("/friends")
    public Response getFriends() {
        User user = this.userService.getUser(jwt.getSubject());

        List<FriendDto> requests =
                friendshipService.getFriends(user.id);

        return Response.ok(requests).build();
    }

    @DELETE
    @Path("/friends/{friendId}")
    public Response endFriendship(@PathParam("friendId") long friendId) {
        User user = this.userService.getUser(jwt.getSubject());

        this.friendshipService.removeFriendship(user.id, friendId);

        return Response.noContent().build();
    }
}