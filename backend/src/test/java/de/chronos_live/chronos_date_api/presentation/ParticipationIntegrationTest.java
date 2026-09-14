package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.AddParticipantDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentParticipationRepository;
import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
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
 * Integration tests for appointment participation (RSVP) flows.
 *
 * <p>Tests verify:
 * - Adding participants to appointments
 * - Changing participation status (approve/reject)
 * - Changing participant roles
 * - Removing participants
 * - Fetching participant lists
 * - Authorization checks
 */
@QuarkusTest
class ParticipationIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Inject
    AppointmentParticipationRepository participationRepository;

    @Test
    void testAddUserParticipant_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();

        AddParticipantDto addDto = new AddParticipantDto();
        addDto.setUser_id(TEST_USER_OIDC_2);
        addDto.setUser_role("GUEST");

        // Act
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
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify in database
        Optional<AppointmentParticipation> participation = participationRepository
                .find("appointmentId = ?1 AND userOidcId = ?2",
                        appointment.getId(),
                        TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(participation).isPresent();
        assertThat(participation.get().getUserRole()).isEqualTo(UserRole.GUEST);
    }

    @Test
    void testApproveAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC_2);
        Appointment appointment = createTestAppointment();
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.INVITED);

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/participants/approve")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify status changed in database
        Optional<AppointmentParticipation> participation = participationRepository
                .find("appointmentId = ?1 AND userOidcId = ?2",
                        appointment.getId(),
                        TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(participation).isPresent();
        assertThat(participation.get().getParticipationStatus()).isEqualTo(ParticipationStatus.APPROVED);
    }

    @Test
    void testRejectAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC_2);
        Appointment appointment = createTestAppointment();
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.INVITED);

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/participants/reject")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify status changed in database
        Optional<AppointmentParticipation> participation = participationRepository
                .find("appointmentId = ?1 AND userOidcId = ?2",
                        appointment.getId(),
                        TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(participation).isPresent();
        assertThat(participation.get().getParticipationStatus()).isEqualTo(ParticipationStatus.REJECTED);
    }

    @Test
    void testChangeParticipantRole_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.INVITED);

        var roleDto = new java.util.LinkedHashMap<String, String>();
        roleDto.put("role", "ORGANIZER");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(roleDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.getId() + "/participants/users/" + TEST_USER_OIDC_2)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify role changed in database
        Optional<AppointmentParticipation> participation = participationRepository
                .find("appointmentId = ?1 AND userOidcId = ?2",
                        appointment.getId(),
                        TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(participation).isPresent();
        assertThat(participation.get().getUserRole()).isEqualTo(UserRole.ORGANIZER);
    }

    @Test
    void testRemoveParticipant_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.INVITED);

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointment.getId() + "/participants/users/" + TEST_USER_OIDC_2)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify removed from database
        long count = participationRepository
                .count("appointmentId = ?1 AND userOidcId = ?2",
                        appointment.getId(),
                        TEST_USER_OIDC_2);
        assertThat(count).isZero();
    }

    @Test
    void testGetParticipants_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment();
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.INVITED);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/appointments/" + appointment.getId() + "/participants/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(1);
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

    private void addParticipantToAppointment(Appointment appointment, String userOidcId,
                                             UserRole role, ParticipationStatus status) {
        AppointmentParticipation participation = new AppointmentParticipation();
        participation.setAppointmentId(appointment.getId());
        participation.setUserOidcId(userOidcId);
        participation.setUserRole(role);
        participation.setParticipationStatus(status);
        participationRepository.persist(participation);
    }
}
