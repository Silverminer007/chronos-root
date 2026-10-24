package de.chronos_live.chronos_date_api.presentation;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the routes of {@code AppointmentParticipationResource} (ticket #162).
 *
 * <p>The resource has no class-level {@code @Path}; if its method-level paths are not registered,
 * every route answers 404 from the router. Each of the 8 routes is therefore called against a
 * non-existent appointment, which makes the business layer answer with a defined status.
 */
@QuarkusTest
class ParticipationRoutesIntegrationTest extends BaseIntegrationTest {

    private static final long MISSING_APPOINTMENT_ID = 999_999L;
    private static final String BASE = "/api/v2/appointments/" + MISSING_APPOINTMENT_ID;

    private int statusOf(String method, String path, String body) {
        mockJwtForUser(TEST_USER_OIDC);
        var request = RestAssured.given().contentType(ContentType.JSON);
        if (body != null) {
            request = request.body(body);
        }
        return request.when().request(method, path).then().extract().statusCode();
    }

    @Test
    void rsvpRoute() {
        assertThat(statusOf("POST", BASE + "/participation", "{\"status\":\"APPROVED\"}")).isEqualTo(RSVP_STATUS);
    }

    @Test
    void changeRoleRoute() {
        assertThat(statusOf("PUT", BASE + "/participation/" + TEST_USER_OIDC_2 + "/role", "{\"role\":\"HELPER\"}"))
                .isEqualTo(RSVP_STATUS);
    }

    @Test
    void addParticipantRoute() {
        assertThat(statusOf("POST", BASE + "/participants/" + TEST_USER_OIDC_2, "{\"user_role\":\"GUEST\"}"))
                .isEqualTo(RSVP_STATUS);
    }

    @Test
    void removeParticipantRoute() {
        assertThat(statusOf("DELETE", BASE + "/participants/" + TEST_USER_OIDC_2, null)).isEqualTo(RSVP_STATUS);
    }

    @Test
    void addGroupRoute() {
        assertThat(statusOf("POST", BASE + "/groups/1", "{\"user_role\":\"GUEST\"}")).isEqualTo(RSVP_STATUS);
    }

    @Test
    void removeGroupRoute() {
        assertThat(statusOf("DELETE", BASE + "/groups/1", null)).isEqualTo(RSVP_STATUS);
    }

    @Test
    void getParticipantsRoute() {
        assertThat(statusOf("GET", BASE + "/participants", null)).isEqualTo(RSVP_STATUS);
    }

    @Test
    void getParticipationStatusRoute() {
        assertThat(statusOf("GET", BASE + "/participation/status", null)).isEqualTo(RSVP_STATUS);
    }

    private static final int RSVP_STATUS = -1;
}
