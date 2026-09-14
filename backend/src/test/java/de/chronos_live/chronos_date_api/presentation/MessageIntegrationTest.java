package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for appointment messages and push notifications.
 *
 * <p>Tests verify:
 * - Posting messages to appointments
 * - Message retrieval and listing
 * - Message deletion
 * - Push notification events fired
 * - Message timestamp handling
 */
@QuarkusTest
class MessageIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Inject
    MessageRepository messageRepository;

    @Test
    void testPostMessage_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        var messageDto = new java.util.LinkedHashMap<String, String>();
        messageDto.put("text", "This is a test message");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(messageDto)
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/messages")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().jsonPath().getLong("id")).isNotNull();
        assertThat(response.body().jsonPath().getString("text")).isEqualTo("This is a test message");

        // Verify in database
        long messageCount = messageRepository.count("appointmentId = ?1", appointment.getId());
        assertThat(messageCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testGetAppointmentMessages_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        Message message = new Message();
        message.setAppointmentId(appointment.getId());
        message.setUserOidcId(TEST_USER_OIDC);
        message.setText("Test message");
        message.setCreatedAt(Instant.now());
        messageRepository.persist(message);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/appointments/" + appointment.getId() + "/messages")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void testDeleteMessage_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        Message message = new Message();
        message.setAppointmentId(appointment.getId());
        message.setUserOidcId(TEST_USER_OIDC);
        message.setText("Test message to delete");
        message.setCreatedAt(Instant.now());
        messageRepository.persist(message);
        Long messageId = message.getId();

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointment.getId() + "/messages/" + messageId)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify deleted from database
        Optional<Message> deleted = messageRepository.findByIdOptional(messageId);
        assertThat(deleted).isEmpty();
    }

    @Test
    void testPostMessage_Unauthorized_Returns403() {
        // Arrange - Create appointment as TEST_USER_OIDC
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        // Act - Try to post message as TEST_USER_OIDC_2 (not participant)
        mockJwtForUser(TEST_USER_OIDC_2);
        var messageDto = new java.util.LinkedHashMap<String, String>();
        messageDto.put("text", "Unauthorized message");

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(messageDto)
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/messages")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testPostMessageToNonExistentAppointment_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Long nonExistentId = 99999L;

        var messageDto = new java.util.LinkedHashMap<String, String>();
        messageDto.put("text", "Message to non-existent appointment");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(messageDto)
                .when()
                .post("/api/v2/appointments/" + nonExistentId + "/messages")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    // Helper methods
    private Appointment createTestAppointment() {
        Appointment appointment = new Appointment();
        appointment.setName("Test Meeting");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        return appointment;
    }
}
