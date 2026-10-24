package de.chronos_live.chronos_date_api.presentation;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route reachability of {@link AppointmentParticipationResource} (#162): all participation routes must be served
 * under {@code /api/v2/appointments}. The resource lost its class-level {@code @Path} once, so every route
 * answered 404 although the resource-level unit tests (which call the methods directly) stayed green.
 * Only the routing is asserted here (any answer but 404/405, so no login is needed); business behaviour is covered by {@code AppointmentParticipationResourceTest}.
 */
@QuarkusTest
class ParticipationRoutesIntegrationTest {

    private static final long UNKNOWN_APPOINTMENT_ID = 999_999L;

    @ParameterizedTest(name = "{0} {1} is routed")
    @CsvSource({
            "POST,   /participation",
            "PUT,    /participation/test-user-oidc-456/role",
            "POST,   /participants/test-user-oidc-456",
            "DELETE, /participants/test-user-oidc-456",
            "GET,    /participants",
            "GET,    /participation/status",
            "POST,   /groups/1",
            "DELETE, /groups/1"
    })
    void routeIsReachable(Method method, String suffix) {
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .request(method, "/api/v2/appointments/" + UNKNOWN_APPOINTMENT_ID + suffix)
                .then()
                .extract()
                .response();

        // Business errors (e.g. unknown appointment) are fine; "no matching resource method" is the defect.
        assertThat(response.body().asString()).doesNotContain("Unable to find matching target resource method");
        assertThat(response.statusCode()).isNotEqualTo(404).isNotEqualTo(405);
    }
}
