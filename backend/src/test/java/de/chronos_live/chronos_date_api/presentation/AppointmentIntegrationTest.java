package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentParticipationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for appointment lifecycle covering create, read, update, delete operations.
 *
 * <p>Tests verify:
 * - REST endpoint contract (status codes, response format)
 * - Database persistence
 * - CDI event firing (mocked separately in service tests)
 * - Authorization checks
 *
 * <p>Note: Test class does NOT use @Transactional so that data created in test methods
 * is immediately committed to the database and visible to REST request handlers running
 * in separate threads. Panache repositories auto-commit by default.
 */
@QuarkusTest
class AppointmentIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Inject
    AppointmentParticipationRepository participationRepository;

    @Test
    void testCreateAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endTime = startTime.plus(2, ChronoUnit.HOURS);

        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("Team Meeting");
        createDto.setDescription("Weekly sync");
        createDto.setVenue("Conference Room A");
        createDto.setStart(startTime.toString());
        createDto.setEnd(endTime.toString());

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

        // Assert - Response
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().jsonPath().getLong("id")).isNotNull();
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Team Meeting");
        assertThat(response.body().jsonPath().getString("status")).isEqualTo("PLANNED");

        // Assert - Database
        Long appointmentId = response.body().jsonPath().getLong("id");
        Appointment persisted = appointmentRepository.findById(appointmentId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getName()).isEqualTo("Team Meeting");
        assertThat(persisted.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
    }

    @Test
    void testGetAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Termin");
        appointment.setDescription("Test Description");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Test Termin");
        assertThat(response.body().jsonPath().getLong("id")).isEqualTo(appointment.id);
    }

    @Test
    void testCreateAppointment_BlankName_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("");
        createDto.setStart(Instant.now().toString());
        createDto.setEnd(Instant.now().plus(1, ChronoUnit.HOURS).toString());

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
    void testUpdateAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Original Name");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "Updated Name");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Updated Name");

        // Verify in database
        Appointment updated = appointmentRepository.findById(appointment.id);
        assertThat(updated.getName()).isEqualTo("Updated Name");
    }

    @Test
    void testDeleteAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Appointment");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointmentId)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify soft-deleted (status set to DELETED)
        Appointment deleted = appointmentRepository.findById(appointmentId);
        assertThat(deleted).isNotNull();
        assertThat(deleted.getStatus()).isEqualTo(AppointmentStatus.DELETED);
    }

    @Test
    void testCancelAppointment_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Test Appointment");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointmentId + "/cancel")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify soft-cancelled (status set to CANCELLED)
        Appointment cancelled = appointmentRepository.findById(appointmentId);
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void testListAppointmentsWithPagination_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        // Create multiple appointments
        for (int i = 0; i < 15; i++) {
            Appointment appointment = new Appointment();
            appointment.setName("Appointment " + i);
            appointment.setStartTime(Instant.now().plus(i, ChronoUnit.DAYS));
            appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
            appointment.setStatus(AppointmentStatus.PLANNED);
            appointment.setCreatorOidcId(TEST_USER_OIDC);
            appointmentRepository.persist(appointment);
        }

        // Act
        var response = RestAssured
                .given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v2/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("items")).hasSize(10);
        assertThat(response.body().jsonPath().getInt("meta.page")).isEqualTo(0);
        assertThat(response.body().jsonPath().getInt("meta.size")).isEqualTo(10);
        assertThat(response.body().jsonPath().getInt("meta.total")).isGreaterThanOrEqualTo(15);
    }

    @Test
    void testListAppointmentsWithSearch_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment1 = new Appointment();
        appointment1.setName("Team Meeting");
        appointment1.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment1.setEndTime(appointment1.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment1.setStatus(AppointmentStatus.PLANNED);
        appointment1.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment1);

        Appointment appointment2 = new Appointment();
        appointment2.setName("Individual Review");
        appointment2.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        appointment2.setEndTime(appointment2.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment2.setStatus(AppointmentStatus.PLANNED);
        appointment2.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment2);

        // Act
        var response = RestAssured
                .given()
                .queryParam("search", "Team")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v2/appointments/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("items")).hasSize(1);
        assertThat(response.body().jsonPath().getString("items[0].name")).isEqualTo("Team Meeting");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Authorization Tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateAppointment_AuthorizedUser_CanUpdate() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Add TEST_USER_OIDC as participant with RESPONSIBLE role
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "Updated by Creator");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Updated by Creator");
    }

    @Test
    void testUpdateAppointment_AttendantCanUpdate() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Add TEST_USER_OIDC_2 as ATTENDANT (can update)
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.ATTENDANT, ParticipationStatus.APPROVED);

        // Act - TEST_USER_OIDC_2 updates (should succeed with ATTENDANT role)
        mockJwtForUser(TEST_USER_OIDC_2);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("description", "Updated by Attendant");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void testUpdateAppointment_GuestCannotUpdate() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Add TEST_USER_OIDC_2 as GUEST (cannot update)
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.GUEST, ParticipationStatus.APPROVED);

        // Act - TEST_USER_OIDC_2 tries to update (should fail with GUEST role)
        mockJwtForUser(TEST_USER_OIDC_2);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("description", "Hacked");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert - GUEST cannot update (needs ATTENDANT or higher)
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testUpdateAppointment_UnauthorizedUser_Returns403() {
        // Arrange
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // TEST_USER_OIDC_2 has no participation at all

        // Act - Switch to unauthorized user
        mockJwtForUser(TEST_USER_OIDC_2);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "Hacked Name");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testDeleteAppointment_CreatorCanDelete() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointmentId)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        Appointment deleted = appointmentRepository.findById(appointmentId);
        assertThat(deleted.getStatus()).isEqualTo(AppointmentStatus.DELETED);
    }

    @Test
    void testDeleteAppointment_AttendantCannotDelete() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Add TEST_USER_OIDC_2 as ATTENDANT (cannot delete, needs RESPONSIBLE)
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.ATTENDANT, ParticipationStatus.APPROVED);

        // Act
        mockJwtForUser(TEST_USER_OIDC_2);
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testDeleteAppointment_UnauthorizedUser_Returns403() {
        // Arrange
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Only TEST_USER_OIDC can delete (creator/RESPONSIBLE)

        // Act
        mockJwtForUser(TEST_USER_OIDC_2);
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testCancelAppointment_OnlyResponsibleCanCancel() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointmentId + "/cancel")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        Appointment cancelled = appointmentRepository.findById(appointmentId);
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void testCancelAppointment_AttendantCannotCancel() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        // Add TEST_USER_OIDC_2 as ATTENDANT (cannot cancel, needs RESPONSIBLE)
        addParticipantToAppointment(appointment, TEST_USER_OIDC_2, UserRole.ATTENDANT, ParticipationStatus.APPROVED);

        // Act
        mockJwtForUser(TEST_USER_OIDC_2);
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointment.id + "/cancel")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(403);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Validation Tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateAppointment_BlankName_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "   ");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void testUpdateAppointment_EndBeforeStart_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("start", end.toString());
        updateDto.put("end", start.toString());
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void testCreateAppointment_InvalidDateRange_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant start = end.plus(1, ChronoUnit.HOURS);

        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("Invalid Meeting");
        createDto.setStart(start.toString());
        createDto.setEnd(end.toString());

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
    void testCreateAppointment_NegativeMinimalAttendees_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("Meeting");
        createDto.setStart(start.toString());
        createDto.setEnd(end.toString());
        createDto.setMinimal_attendees(-5);

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

    // ────────────────────────────────────────────────────────────────────────────
    // 404 Not Found Tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testGetAppointment_NotFound_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Long nonExistentId = 999999L;

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
    void testUpdateAppointment_NotFound_Returns404() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "Updated");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/999999")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void testDeleteAppointment_NotFound_ReturnsOk() {
        // Arrange - Note: delete returns 200 even if not found (idempotent)
        mockJwtForUser(TEST_USER_OIDC);

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/999999")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Delete vs Cancel Tests (Soft Delete Distinction)
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testDeleteAppointment_SetsStatusDeleted() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/appointments/" + appointmentId)
                .then()
                .extract()
                .response();

        // Assert - Response
        assertThat(response.statusCode()).isEqualTo(200);

        // Assert - Database persists row with DELETED status
        Appointment deleted = appointmentRepository.findById(appointmentId);
        assertThat(deleted).isNotNull();
        assertThat(deleted.getStatus()).isEqualTo(AppointmentStatus.DELETED);
    }

    @Test
    void testCancelAppointment_SetsStatusCancelled() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);
        Long appointmentId = appointment.id;

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointmentId + "/cancel")
                .then()
                .extract()
                .response();

        // Assert - Response
        assertThat(response.statusCode()).isEqualTo(200);

        // Assert - Database persists row with CANCELLED status
        Appointment cancelled = appointmentRepository.findById(appointmentId);
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Update Scenarios - Partial Updates
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateAppointment_OnlyNameUpdated() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Original Name");
        appointment.setDescription("Original Desc");
        Instant originalStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant originalEnd = originalStart.plus(2, ChronoUnit.HOURS);
        appointment.setStartTime(originalStart);
        appointment.setEndTime(originalEnd);
        appointment.setVenue("Hall A");
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("name", "Updated Name");
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Updated Name");
        assertThat(response.body().jsonPath().getString("description")).isEqualTo("Original Desc");
        assertThat(response.body().jsonPath().getString("venue")).isEqualTo("Hall A");
    }

    @Test
    void testUpdateAppointment_OnlyStartTimeUpdated() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Meeting");
        Instant originalStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant originalEnd = originalStart.plus(2, ChronoUnit.HOURS);
        appointment.setStartTime(originalStart);
        appointment.setEndTime(originalEnd);
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        Instant newStart = originalStart.plus(1, ChronoUnit.DAYS);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("start", newStart.toString());
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("start")).isEqualTo(newStart.toString());
        assertThat(response.body().jsonPath().getString("end")).isEqualTo(originalEnd.toString());
    }

    @Test
    void testUpdateAppointment_OnlyEndTimeUpdated() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Meeting");
        Instant originalStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant originalEnd = originalStart.plus(2, ChronoUnit.HOURS);
        appointment.setStartTime(originalStart);
        appointment.setEndTime(originalEnd);
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        Instant newEnd = originalEnd.plus(1, ChronoUnit.HOURS);
        var updateDto = new java.util.LinkedHashMap<String, String>();
        updateDto.put("end", newEnd.toString());
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("start")).isEqualTo(originalStart.toString());
        assertThat(response.body().jsonPath().getString("end")).isEqualTo(newEnd.toString());
    }

    @Test
    void testUpdateAppointment_OnlyMinimalAttendeesUpdated() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Meeting");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setMinimalAttendees(5);
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        var updateDto = new java.util.LinkedHashMap<String, Integer>();
        updateDto.put("minimal_attendees", 10);
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getInt("minimal_attendees")).isEqualTo(10);
    }

    @Test
    void testUpdateAppointment_AllFieldsUpdated() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Original");
        appointment.setDescription("Original Desc");
        appointment.setVenue("Hall A");
        Instant originalStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant originalEnd = originalStart.plus(2, ChronoUnit.HOURS);
        appointment.setStartTime(originalStart);
        appointment.setEndTime(originalEnd);
        appointment.setMinimalAttendees(5);
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        Instant newStart = originalStart.plus(7, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(3, ChronoUnit.HOURS);
        var updateDto = new java.util.LinkedHashMap<>();
        updateDto.put("name", "Updated Name");
        updateDto.put("description", "Updated Desc");
        updateDto.put("venue", "Hall B");
        updateDto.put("start", newStart.toString());
        updateDto.put("end", newEnd.toString());
        updateDto.put("minimal_attendees", 15);

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .patch("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Updated Name");
        assertThat(response.body().jsonPath().getString("description")).isEqualTo("Updated Desc");
        assertThat(response.body().jsonPath().getString("venue")).isEqualTo("Hall B");
        assertThat(response.body().jsonPath().getString("start")).isEqualTo(newStart.toString());
        assertThat(response.body().jsonPath().getString("end")).isEqualTo(newEnd.toString());
        assertThat(response.body().jsonPath().getInt("minimal_attendees")).isEqualTo(15);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Response Format Tests (matching Java spec)
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateAppointment_ResponseFormatMatchesSpec() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endTime = startTime.plus(2, ChronoUnit.HOURS);

        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("Team Meeting");
        createDto.setDescription("Weekly sync");
        createDto.setVenue("Conference Room A");
        createDto.setStart(startTime.toString());
        createDto.setEnd(endTime.toString());
        createDto.setMinimal_attendees(3);

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

        // Assert - Verify response has all required fields
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().jsonPath().getLong("id")).isNotNull();
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Team Meeting");
        assertThat(response.body().jsonPath().getString("description")).isEqualTo("Weekly sync");
        assertThat(response.body().jsonPath().getString("venue")).isEqualTo("Conference Room A");
        assertThat(response.body().jsonPath().getString("start")).isNotNull();
        assertThat(response.body().jsonPath().getString("end")).isNotNull();
        assertThat(response.body().jsonPath().getString("status")).isEqualTo("PLANNED");
        assertThat(response.body().jsonPath().getInt("minimal_attendees")).isEqualTo(3);
        assertThat(response.body().jsonPath().getList("participants")).isNotNull();
    }

    @Test
    void testGetAppointment_ResponseFormatMatchesSpec() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = createTestAppointment(TEST_USER_OIDC);
        addParticipantToAppointment(appointment, TEST_USER_OIDC, UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);

        // Act
        var response = RestAssured
                .given()
                .queryParam("participants", "true")
                .queryParam("messages", "true")
                .when()
                .get("/api/v2/appointments/" + appointment.id)
                .then()
                .extract()
                .response();

        // Assert - Verify response structure
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getLong("id")).isEqualTo(appointment.id);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Test Termin");
        assertThat(response.body().jsonPath().getString("status")).isEqualTo("PLANNED");
        assertThat(response.body().jsonPath().getList("participants")).isNotNull();
        assertThat(response.body().jsonPath().getList("messages")).isNotNull();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ────────────────────────────────────────────────────────────────────────────

    @Transactional
    private Appointment createTestAppointment(String creatorOidcId) {
        Appointment appointment = new Appointment();
        appointment.setName("Test Termin");
        appointment.setDescription("Test Description");
        appointment.setVenue("Main Hall");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setMinimalAttendees(5);
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(creatorOidcId);
        appointmentRepository.persist(appointment);
        return appointment;
    }

    @Transactional
    private void addParticipantToAppointment(Appointment appointment, String userOidcId,
                                             UserRole role, ParticipationStatus status) {
        AppointmentParticipation participation = new AppointmentParticipation();
        participation.setAppointment(appointment);
        participation.setUserOidcId(userOidcId);
        participation.setRole(role);
        participation.setStatus(status);
        participationRepository.persist(participation);
    }
}
