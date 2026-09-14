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
 * Integration tests for appointment lifecycle covering create, read, update, delete operations.
 *
 * <p>Tests verify:
 * - REST endpoint contract (status codes, response format)
 * - Database persistence
 * - CDI event firing (mocked separately in service tests)
 * - Authorization checks
 */
@QuarkusTest
class AppointmentIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

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
                .get("/api/v2/appointments/" + appointment.getId())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Test Termin");
        assertThat(response.body().jsonPath().getLong("id")).isEqualTo(appointment.getId());
    }

    @Test
    void testCreateAppointment_BlankName_Returns400() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        CreateAppointmentDto createDto = new CreateAppointmentDto();
        createDto.setName("");
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
                .patch("/api/v2/appointments/" + appointment.getId())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Updated Name");

        // Verify in database
        Appointment updated = appointmentRepository.findById(appointment.getId());
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
        Long appointmentId = appointment.getId();

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

        // Verify deleted from database
        Appointment deleted = appointmentRepository.findById(appointmentId);
        assertThat(deleted).isNull();
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

        // Act
        var response = RestAssured
                .given()
                .when()
                .post("/api/v2/appointments/" + appointment.getId() + "/cancel")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify status changed in database
        Appointment cancelled = appointmentRepository.findById(appointment.getId());
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
}
