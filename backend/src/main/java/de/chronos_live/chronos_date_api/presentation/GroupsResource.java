package de.chronos_live.chronos_date_api.presentation;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/api/v2/groups")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GroupsResource {

    @GET
    @Path("/")
    public String getGroups() {
        return "";
    }

    @POST
    @Path("/")
    public String postGroup(@RequestBody String group) {
        return "";
    }

    @GET
    @Path("/{group}")
    public String getGroup(@PathParam("group") String group) {
        return "";
    }

    @DELETE
    @Path("/{group}")
    public String deleteGroup(@PathParam("group") String group) {
        return "";
    }

    @PATCH
    @Path("/{group}")
    public String patchGroup(@PathParam("group") int group_id, @RequestBody String group) {
        return "";
    }

    @POST
    @Path("/{group}/users")
    public String postUsers(@PathParam("group") int group_id, @RequestBody String user) {
        return "";
    }

    @GET
    @Path("/{group}/users")
    public String getUsers(@PathParam("group") int group_id) {
        return "";
    }

    @DELETE
    @Path("/{group}/users/{user}")
    public String deleteUsers(@PathParam("group") int group_id, @PathParam("user") String user) {
        return "";
    }
}