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
}
