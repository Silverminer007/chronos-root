package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route reachability of {@link AppointmentParticipationResource} (#162): all participation routes must be served
 * under {@code /api/v2/appointments}. The resource lost its class-level {@code @Path} once, so every route
 * answered 404 although the resource-level unit tests (which call the methods directly) stayed green.
 * The calls are authenticated and target a real fixture appointment. Only the routing is asserted (RESTEasy's
 * "no matching resource method" body, and 405); a business 404 from the not-found mapper is a valid answer of a
 * healthy route. Business behaviour is covered by {@code AppointmentParticipationResourceTest}.
 */
@QuarkusTest
class ParticipationRoutesIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

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
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .request(method, "/api/v2/appointments/" + appointment.getId() + suffix)
                .then()
                .extract()
                .response();

        // Business errors (incl. a mapped 404) are fine; "no matching resource method" is the defect.
        assertThat(response.body().asString()).doesNotContain("Unable to find matching target resource method");
        assertThat(response.statusCode()).isNotEqualTo(405);
    }

    private Appointment createTestAppointment() {
        Appointment appointment = new Appointment();
        appointment.setName("Route Test");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        return appointment;
    }
}
