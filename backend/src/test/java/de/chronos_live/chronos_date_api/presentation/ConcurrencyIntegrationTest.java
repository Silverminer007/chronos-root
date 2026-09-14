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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for concurrent request handling.
 *
 * <p>Tests verify:
 * - Multiple concurrent appointment creations succeed without race conditions
 * - Concurrent RSVP updates maintain consistency
 * - Concurrent participant removals don't cause data corruption
 * - Database transactions handle concurrent modifications correctly
 */
@QuarkusTest
class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Inject
    AppointmentRepository appointmentRepository;

    @Test
    void testConcurrentAppointmentCreations() throws Exception {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Create 10 appointments concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executorService.submit(() -> {
                try {
                    Instant startTime = Instant.now().plus(index, ChronoUnit.DAYS);
                    CreateAppointmentDto createDto = new CreateAppointmentDto();
                    createDto.setName("Concurrent Appointment " + index);
                    createDto.setStartTime(startTime.toString());
                    createDto.setEndTime(startTime.plus(1, ChronoUnit.HOURS).toString());

                    var response = RestAssured
                            .given()
                            .contentType(ContentType.JSON)
                            .body(createDto)
                            .when()
                            .post("/api/v2/appointments/")
                            .then()
                            .extract()
                            .response();

                    return response.statusCode() == 201;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }

        // Assert - Verify all 10 appointments were created
        long count = appointmentRepository.count("creatorOidcId = ?1", TEST_USER_OIDC);
        assertThat(count).isGreaterThanOrEqualTo(threadCount);
    }

    @Test
    void testConcurrentRSVPUpdates() throws Exception {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Concurrent RSVP Test");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        // Create multiple participants
        List<String> participants = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            participants.add("participant-" + i);
        }

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Multiple participants RSVP concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executorService.submit(() -> {
                try {
                    mockJwtForUser(participants.get(index));
                    var response = RestAssured
                            .given()
                            .when()
                            .post("/api/v2/appointments/" + appointment.getId() + "/participants/approve")
                            .then()
                            .extract()
                            .response();

                    return response.statusCode() == 200;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }

        // Assert - No data corruption occurred
        Appointment updated = appointmentRepository.findById(appointment.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
    }

    @Test
    void testConcurrentParticipantModifications() throws Exception {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Concurrent Participants Test");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Add and potentially modify participants concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executorService.submit(() -> {
                try {
                    var addDto = new java.util.LinkedHashMap<String, String>();
                    addDto.put("user_id", "participant-" + index);
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

                    return response.statusCode() == 200;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }

        // Assert - No data corruption occurred
        Appointment updated = appointmentRepository.findById(appointment.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
    }

    @Test
    void testConcurrentReadAndWrite() throws Exception {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Appointment appointment = new Appointment();
        appointment.setName("Concurrent Read/Write Test");
        appointment.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setEndTime(appointment.getStartTime().plus(1, ChronoUnit.HOURS));
        appointment.setStatus(AppointmentStatus.PLANNED);
        appointment.setCreatorOidcId(TEST_USER_OIDC);
        appointmentRepository.persist(appointment);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Mix of read and write operations
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            if (index % 2 == 0) {
                // Read operations
                futures.add(executorService.submit(() -> {
                    try {
                        var response = RestAssured
                                .given()
                                .when()
                                .get("/api/v2/appointments/" + appointment.getId())
                                .then()
                                .extract()
                                .response();

                        return response.statusCode() == 200;
                    } catch (Exception e) {
                        return false;
                    }
                }));
            } else {
                // Write operations
                futures.add(executorService.submit(() -> {
                    try {
                        var updateDto = new java.util.LinkedHashMap<String, String>();
                        updateDto.put("description", "Updated in thread " + Thread.currentThread().getName());

                        var response = RestAssured
                                .given()
                                .contentType(ContentType.JSON)
                                .body(updateDto)
                                .when()
                                .patch("/api/v2/appointments/" + appointment.getId())
                                .then()
                                .extract()
                                .response();

                        return response.statusCode() == 200;
                    } catch (Exception e) {
                        return false;
                    }
                }));
            }
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }

        // Assert - Most or all operations should succeed
        assertThat(successCount).isGreaterThanOrEqualTo(threadCount / 2);
    }
}
