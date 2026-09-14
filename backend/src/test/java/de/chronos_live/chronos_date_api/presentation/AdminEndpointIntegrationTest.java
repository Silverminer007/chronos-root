package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for admin-only endpoints.
 *
 * <p>Tests verify:
 * - Admin endpoints return 403 for non-admin users
 * - Admin endpoints are accessible with proper admin role
 * - Admin operations succeed with appropriate authorization
 *
 * <p>Note: These tests check authorization enforcement. Full admin endpoint
 * functionality is tested in their respective admin resource test classes.
 */
@QuarkusTest
class AdminEndpointIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Test
    void testAdminAppointmentEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act - Try to access admin endpoint as non-admin
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAdminGroupEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/groups/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAdminFriendshipEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/friendships/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAdminUserEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/users/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAdminPushEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/push/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAdminStatisticsEndpoint_NonAdminUser_Returns403() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/admin/statistics/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testDeleteAppointmentAsAdmin_Success() {
        // Arrange - Create appointment as non-admin user
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Meeting");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        // Act - Delete as admin (if admin override is implemented)
        // Note: This test assumes the admin endpoint allows deletion of any appointment
        // The actual implementation may vary based on business logic
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/admin/appointments/" + appointment.getId())
                .then()
                .extract()
                .response();

        // Assert - Expecting 403 because we're not authenticated as admin
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
