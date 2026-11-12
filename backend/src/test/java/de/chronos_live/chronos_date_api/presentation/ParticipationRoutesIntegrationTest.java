package de.chronos_live.chronos_date_api.presentation;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression protection for the eight participation routes of {@link AppointmentParticipationResource}
 * (see #162): every route has to be reachable under {@code /api/v2/appointments/{id}/...}
 * and answer with its defined status.
 *
 * <p>A route that is not mapped would answer 404 and an unhandled error 500, so each assertion
 * names the exact 200/400/403 that the service layer produces for the prepared appointment.
 */
@QuarkusTest
class ParticipationRoutesIntegrationTest extends BaseIntegrationTest {

    private static final long UNKNOWN_GROUP_ID = 987_654_321L;

    private long createAppointmentAsCreator() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Map<String, Object> body = Map.of(
                "name", "Routen-Test",
                "start", start.toString(),
                "end", start.plus(1, ChronoUnit.HOURS).toString());
        Response response = createAppointmentWithAuth(body, TEST_USER_OIDC);
        assertThat(response.statusCode()).isEqualTo(201);
        return response.jsonPath().getLong("id");
    }

    private Response request(String method, String path, Object body) {
        var spec = RestAssured.given().contentType(ContentType.JSON);
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.when().request(method, "/api/v2/appointments/" + path).then().extract().response();
    }

    @Test
    void getParticipants_listsTheCreatorAsResponsible() {
        long id = createAppointmentAsCreator();

        Response response = request("GET", id + "/participants", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("user_id")).containsExactly(TEST_USER_OIDC);
        assertThat(response.jsonPath().getString("[0].role")).isEqualTo("RESPONSIBLE");
    }

    @Test
    void getParticipationStatus_returnsPendingForTheCreator() {
        long id = createAppointmentAsCreator();

        Response response = request("GET", id + "/participation/status", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("status")).isEqualTo("PENDING");
    }

    @Test
    void rsvp_changesTheOwnStatus() {
        long id = createAppointmentAsCreator();

        Response rsvp = request("POST", id + "/participation", Map.of("status", "APPROVED"));
        Response status = request("GET", id + "/participation/status", null);

        assertThat(rsvp.statusCode()).isEqualTo(200);
        assertThat(status.jsonPath().getString("status")).isEqualTo("APPROVED");
    }

    @Test
    void rsvp_backToPendingIsRejectedWith400() {
        long id = createAppointmentAsCreator();

        Response response = request("POST", id + "/participation", Map.of("status", "PENDING"));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void changeRole_ofNonParticipantIsRejectedWith400() {
        long id = createAppointmentAsCreator();

        Response response = request("PUT", id + "/participation/" + TEST_USER_OIDC_2 + "/role",
                Map.of("role", "HELPER"));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void changeRole_byNonParticipantIsForbidden() {
        long id = createAppointmentAsCreator();
        mockJwtForUser(TEST_USER_OIDC_2);

        Response response = request("PUT", id + "/participation/" + TEST_USER_OIDC + "/role",
                Map.of("role", "HELPER"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void addParticipant_whoIsNoFriendIsForbidden() {
        long id = createAppointmentAsCreator();

        Response response = request("POST", id + "/participants/" + TEST_USER_OIDC_2,
                Map.of("user_id", TEST_USER_OIDC_2, "user_role", "ATTENDANT"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void removeParticipant_whoIsNoParticipantIsRejectedWith400() {
        long id = createAppointmentAsCreator();

        Response response = request("DELETE", id + "/participants/" + TEST_USER_OIDC_2, null);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void addGroup_withoutMembershipIsForbidden() {
        long id = createAppointmentAsCreator();

        Response response = request("POST", id + "/groups/" + UNKNOWN_GROUP_ID,
                Map.of("group_id", UNKNOWN_GROUP_ID, "user_role", "ATTENDANT"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void removeGroup_notAssignedIsRejectedWith400() {
        long id = createAppointmentAsCreator();

        Response response = request("DELETE", id + "/groups/" + UNKNOWN_GROUP_ID, null);

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
