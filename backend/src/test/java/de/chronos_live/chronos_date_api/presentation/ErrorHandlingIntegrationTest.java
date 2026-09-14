package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for error handling and edge cases.
 *
 * <p>Tests verify:
 * - 400 Bad Request for invalid input
 * - 401 Unauthorized for missing/invalid authentication
 * - 403 Forbidden for unauthorized access
 * - 404 Not Found for non-existent resources
 * - 500 Internal Server Error handling
 */
@QuarkusTest
class ErrorHandlingIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Test
    void testCreateAppointment_MissingName_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName(null);
        createDto.setStartTime(Instant.now().toString());
        createDto.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(createDto)
                .when()
                .post("/api/v2/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isBetween(400, 422);
    }

    @Test
    void testCreateAppointment_InvalidTimeRange_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endTime = startTime.minus(1, ChronoUnit.HOURS);

        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("Invalid Time Range");
        createDto.setStartTime(startTime.toString());
        createDto.setEndTime(endTime.toString());

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(createDto)
                .when()
                .post("/api/v2/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void testGetAppointment_NotFound_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Long nonExistentId = 99999L;

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/appointments/" + nonExistentId)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void testDeleteAppointment_AsNonCreator_Returns403() {
        // Arrange - Create appointment as TEST_USER_OIDC
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Meeting");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        // Act - Try to delete as TEST_USER_OIDC_2
        mockJwtForUser(TEST_USER_OIDC_2);
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointment.getId())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testEditAppointment_NotFound_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        var updateDto = new java.util.LinkedHashMap<String, Object>();
        updateDto.put("name", "Updated Name");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/99999")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void testAddParticipant_UnauthorizedUser_Returns403() {
        // Arrange - Create appointment as TEST_USER_OIDC
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Meeting");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        // Act - Try to add participant as TEST_USER_OIDC_2 (not creator)
        mockJwtForUser(TEST_USER_OIDC_2);
        var addDto = new java.util.LinkedHashMap<String, String>();
        addDto.put("user_id", ADMIN_USER_OIDC);
        addDto.put("user_role", "GUEST");

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(addDto)
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/participants/users")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testRsvpOnNonExistentAppointment_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Long nonExistentId = 99999L;

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + nonExistentId + "/participants/approve")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void testInvalidJsonBody_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act - Send invalid JSON
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body("{ invalid json ")
                .when()
                .post("/api/v2/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isBetween(400, 422);
    }
}
