import unittest

from parity.endpoints import (
    java_endpoints_from_source,
    normalize,
    rust_routes_from_source,
)

JAVA = '''
@Path("/api/v2/groups")
@PermitAll
public class GroupsResource {
    @GET
    @Path("/")
    public Response a() {}

    @PATCH
    @Path("/{group}")
    public Response b(@PathParam("group") Long g, @RequestBody GroupDto d) {}

    @GET
    public Response noPath() {}

    @POST
    @Path("/{groupId}/user/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response c() {}
}
'''

RUST = '''
        .route("/api/v2/me", get(get_user_info))
        .route(
            "/api/v2/appointments/:id",
            get(get_appointment)
                .patch(update_appointment)
                .delete(delete_appointment),
        )
        .route("/metrics", get(metrics_handler).with_state((metrics.clone(), pool.clone())))
'''


class NormalizeTest(unittest.TestCase):
    def test_placeholders_and_slashes(self):
        self.assertEqual(normalize("/api/v2/groups/"), "/api/v2/groups")
        self.assertEqual(normalize("/a/{id}/b/{userId}"), "/a/{}/b/{}")
        self.assertEqual(normalize("/a/:id/b/:user_id"), "/a/{}/b/{}")
        self.assertEqual(normalize("//a//b"), "/a/b")


class JavaParseTest(unittest.TestCase):
    def test_extracts_verb_and_joined_path(self):
        eps = java_endpoints_from_source(JAVA)
        self.assertEqual(
            eps,
            {
                ("GET", "/api/v2/groups"),
                ("PATCH", "/api/v2/groups/{}"),
                ("POST", "/api/v2/groups/{}/user/{}"),
            }
            | set(),
        )
        # the method without @Path maps to the class path (already present as GET /api/v2/groups)

    def test_full_paths_on_methods_without_class_path(self):
        src = "public class X {\n  @POST\n  @Path(\"/api/v2/a/{id}\")\n  public void a() {}\n}"
        self.assertEqual(java_endpoints_from_source(src), {("POST", "/api/v2/a/{}")})

    def test_path_before_verb(self):
        src = "@Path(\"/x\")\npublic class X {\n  @Path(\"/{i}\")\n  @DELETE\n  public void a() {}\n}"
        self.assertEqual(java_endpoints_from_source(src), {("DELETE", "/x/{}")})


class RustParseTest(unittest.TestCase):
    def test_extracts_multiline_routes_and_chains(self):
        self.assertEqual(
            rust_routes_from_source(RUST),
            {
                ("GET", "/api/v2/me"),
                ("GET", "/api/v2/appointments/{}"),
                ("PATCH", "/api/v2/appointments/{}"),
                ("DELETE", "/api/v2/appointments/{}"),
                ("GET", "/metrics"),
            },
        )


if __name__ == "__main__":
    unittest.main()
