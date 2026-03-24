package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentQueryService}.
 *
 * Strategy: @QuarkusTest with PanacheMock intercepts every static Panache call
 * (Appointment.find / Appointment.count) so no real database is ever touched.
 * The injected AppointmentRepository is replaced by a Mockito mock via @InjectMock.
 *
 * Infrastructure requirements:
 *   - Docker must be available so that Quarkus DevServices can start a PostgreSQL
 *     container (needed to satisfy the datasource extension at startup).
 *   - Flyway and Hibernate schema validation are disabled in
 *     src/test/resources/application.properties, so the empty container is fine.
 */
@QuarkusTest
class AppointmentQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long APPOINTMENT_ID = 42L;
    private static final Long UNKNOWN_ID     = 999L;
    private static final Long USER_ID        = 1L;

    /** A fixed reference instant used as the "now" anchor in timing tests. */
    private static final Instant T0    = Instant.parse("2024-06-01T12:00:00Z");
    private static final Instant AFTER  = Instant.parse("2024-06-01T08:00:00Z");
    private static final Instant BEFORE = Instant.parse("2024-06-01T18:00:00Z");

    private static final int PAGE      = 0;
    private static final int PAGE_SIZE = 10;
    private static final int BASE_MIN  = 60;
    private static final int MAX_DAYS  = 30;
    private static final int MIN_LEN   = 2;
    private static final int OFFSET_W  = 1;

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentQueryService service;

    @InjectMock
    AppointmentRepository appointmentRepository;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.setName("Test Termin");
        a.setStartTime(AFTER);
        a.setEndTime(BEFORE);
        a.setStatus(AppointmentStatus.PLANNED);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getAppointment:
     *   B1  if (messages)          → true / false
     *   B2  if (participants)      → true / false
     *   B3  if (groupParticipants) → true / false
     *   B4  Optional.isPresent()   → true (appointment returned) / false (exception)
     *
     * Total branches: 8  |  Tests: 5
     */
    @Nested
    class GetAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=false, B2=false, B3=false, B4=true
        @Test
        void should_returnAppointment_when_allFlagsFalse() {
            Appointment expected = buildAppointment();
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(expected));

            Appointment result = service.getAppointment(APPOINTMENT_ID, false, false, false);

            assertThat(result).isSameAs(expected);
            assertThat(sqlCaptor.getValue())
                    .doesNotContain("LEFT JOIN FETCH a.messages")
                    .doesNotContain("LEFT JOIN FETCH a.participants")
                    .doesNotContain("LEFT JOIN FETCH a.groupParticipants")
                    .contains("WHERE a.id = ?1 AND a.status != ?2");
        }

        // B1=true
        @Test
        void should_appendMessagesJoin_when_messagesFlagIsTrue() {
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(buildAppointment()));

            service.getAppointment(APPOINTMENT_ID, true, false, false);

            assertThat(sqlCaptor.getValue())
                    .contains("LEFT JOIN FETCH a.messages")
                    .doesNotContain("LEFT JOIN FETCH a.participants")
                    .doesNotContain("LEFT JOIN FETCH a.groupParticipants");
        }

        // B2=true
        @Test
        void should_appendParticipantsJoin_when_participantsFlagIsTrue() {
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(buildAppointment()));

            service.getAppointment(APPOINTMENT_ID, false, true, false);

            assertThat(sqlCaptor.getValue())
                    .contains("LEFT JOIN FETCH a.participants p LEFT JOIN FETCH p.user")
                    .doesNotContain("LEFT JOIN FETCH a.messages")
                    .doesNotContain("LEFT JOIN FETCH a.groupParticipants");
        }

        // B3=true
        @Test
        void should_appendGroupParticipantsJoin_when_groupParticipantsFlagIsTrue() {
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(buildAppointment()));

            service.getAppointment(APPOINTMENT_ID, false, false, true);

            assertThat(sqlCaptor.getValue())
                    .contains("LEFT JOIN FETCH a.groupParticipants gp LEFT JOIN FETCH gp.group g LEFT JOIN FETCH g.members")
                    .doesNotContain("LEFT JOIN FETCH a.messages")
                    .doesNotContain("LEFT JOIN FETCH a.participants p");
        }

        // B4=false  →  ResourceNotFoundException must be thrown with correct message
        @Test
        void should_throwResourceNotFoundException_when_appointmentNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAppointment(UNKNOWN_ID, false, false, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("appointment mit ID " + UNKNOWN_ID + " wurde nicht gefunden");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // search
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – search:
     *   B1  if (messages)                    → true / false
     *   B2  if (participants)                → true / false
     *   B3  if (groupParticipants)           → true / false
     *   B4  if (query != null)  [WHERE]      → true / false
     *   B5  ternary query != null  [params]  → true / false  (same condition as B4)
     *
     * Total branches: 10  |  Tests: 5
     * B4 and B5 are covered by the same two tests (null/non-null query).
     */
    @Nested
    class Search {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=false, B2=false, B3=false, B4=false, B5=false
        @Test
        void should_returnSearchResult_when_queryIsNull() {
            ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> findSqlCaptor  = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);

            when(Appointment.count(countSqlCaptor.capture(), any(Object[].class))).thenReturn(1L);
            when(Appointment.<Appointment>find(findSqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.page(anyInt(), anyInt())).thenReturn(q);
            when(q.list()).thenReturn(List.of(buildAppointment()));

            AppointmentQueryService.SearchResult result =
                    service.search(USER_ID, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false);

            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.items()).hasSize(1);
            // query=null → no ?5 placeholder and no LIKE clause
            assertThat(countSqlCaptor.getValue()).doesNotContain("?5").doesNotContain("LIKE");
            assertThat(findSqlCaptor.getValue()).doesNotContain("?5").doesNotContain("LIKE");
        }

        // B4=true, B5=true  →  LIKE clause + ?5 must appear in both SQL strings
        @Test
        void should_appendLikeFilter_when_queryIsNotNull() {
            ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> findSqlCaptor  = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);

            when(Appointment.count(countSqlCaptor.capture(), any(Object[].class))).thenReturn(3L);
            when(Appointment.<Appointment>find(findSqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.page(anyInt(), anyInt())).thenReturn(q);
            when(q.list()).thenReturn(List.of(buildAppointment(), buildAppointment(), buildAppointment()));

            AppointmentQueryService.SearchResult result =
                    service.search(USER_ID, "meeting", AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false);

            assertThat(result.total()).isEqualTo(3L);
            assertThat(countSqlCaptor.getValue()).contains("LIKE").contains("?5");
            assertThat(findSqlCaptor.getValue()).contains("LIKE").contains("?5");
        }

        // B1=true
        @Test
        void should_appendMessagesJoin_when_messagesFlagIsTrue() {
            ArgumentCaptor<String> findSqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);

            when(Appointment.count(anyString(), any(Object[].class))).thenReturn(0L);
            when(Appointment.<Appointment>find(findSqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.page(anyInt(), anyInt())).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            service.search(USER_ID, null, AFTER, BEFORE, PAGE, PAGE_SIZE, true, false, false);

            assertThat(findSqlCaptor.getValue()).contains("LEFT JOIN FETCH a.messages");
        }

        // B2=true
        @Test
        void should_appendParticipantsJoin_when_participantsFlagIsTrue() {
            ArgumentCaptor<String> findSqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);

            when(Appointment.count(anyString(), any(Object[].class))).thenReturn(0L);
            when(Appointment.<Appointment>find(findSqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.page(anyInt(), anyInt())).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            service.search(USER_ID, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, true, false);

            assertThat(findSqlCaptor.getValue())
                    .contains("LEFT JOIN FETCH a.participants part LEFT JOIN FETCH part.user");
        }

        // B3=true
        @Test
        void should_appendGroupParticipantsJoin_when_groupParticipantsFlagIsTrue() {
            ArgumentCaptor<String> findSqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);

            when(Appointment.count(anyString(), any(Object[].class))).thenReturn(0L);
            when(Appointment.<Appointment>find(findSqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.page(anyInt(), anyInt())).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            service.search(USER_ID, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, true);

            assertThat(findSqlCaptor.getValue()).contains("LEFT JOIN FETCH a.groupParticipants");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getNonCancelledAppointmentsStartingAt
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getNonCancelledAppointmentsStartingAt:
     *   No conditional branches. Single linear delegation.
     *   Verifies the ±30-second window arithmetic.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetNonCancelledAppointmentsStartingAt {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        @Test
        void should_delegateWithCorrectTimeWindow_when_calledWithInstant() {
            Instant expectedBefore = T0.plusSeconds(30);   // passed as ?1
            Instant expectedAfter  = T0.minusSeconds(30);  // passed as ?2

            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(buildAppointment()));

            List<Appointment> result = service.getNonCancelledAppointmentsStartingAt(T0);

            assertThat(result).hasSize(1);

            // Capture the varargs array (Mockito treats Object... as a single Object[] param)
            // and assert each element individually to verify the ±30 s arithmetic.
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            PanacheMock.verify(Appointment.class).<Appointment>find(anyString(), paramsCaptor.capture());
            Object[] params = paramsCaptor.getValue();
            assertThat(params[0]).isEqualTo(expectedBefore);
            assertThat(params[1]).isEqualTo(expectedAfter);
            assertThat(params[2]).isEqualTo(AppointmentStatus.DELETED);
            assertThat(params[3]).isEqualTo(AppointmentStatus.CANCELLED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getNonCancelledAppointmentsStartingBetween
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getNonCancelledAppointmentsStartingBetween:
     *   No conditional branches. Direct Panache delegation.
     *   Two result variants for line-coverage completeness.
     *
     * Total branches: 0  |  Tests: 2
     */
    @Nested
    class GetNonCancelledAppointmentsStartingBetween {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        @Test
        void should_returnAppointments_when_found() {
            Appointment a = buildAppointment();
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(a));

            List<Appointment> result = service.getNonCancelledAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).containsExactly(a);
        }

        @Test
        void should_returnEmptyList_when_noneFound() {
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            List<Appointment> result = service.getNonCancelledAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPlannedAppointmentsStartingBetween
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getPlannedAppointmentsStartingBetween:
     *   No conditional branches. Direct Panache delegation.
     *
     * Total branches: 0  |  Tests: 2
     */
    @Nested
    class GetPlannedAppointmentsStartingBetween {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        @Test
        void should_returnPlannedAppointments_when_found() {
            Appointment a = buildAppointment();
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(a));

            List<Appointment> result = service.getPlannedAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).containsExactly(a);
        }

        @Test
        void should_returnEmptyList_when_noneFound() {
            @SuppressWarnings("unchecked") PanacheQuery<Appointment> q = mock(PanacheQuery.class);
            when(Appointment.<Appointment>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            List<Appointment> result = service.getPlannedAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findMatchingAppointments
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findMatchingAppointments:
     *   No conditional branches. Pure delegation to AppointmentRepository.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindMatchingAppointments {

        @Test
        void should_delegateToRepository_when_called() {
            List<Appointment> expected = List.of(buildAppointment());
            when(appointmentRepository.findMatchingAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W))
                    .thenReturn(expected);

            List<Appointment> result = service.findMatchingAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);

            assertThat(result).isSameAs(expected);
            verify(appointmentRepository, times(1))
                    .findMatchingAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);
            verifyNoMoreInteractions(appointmentRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findMatchingWeekdayAppointments
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findMatchingWeekdayAppointments:
     *   No conditional branches. Pure delegation to AppointmentRepository.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindMatchingWeekdayAppointments {

        @Test
        void should_delegateToRepository_when_called() {
            List<Appointment> expected = List.of(buildAppointment());
            when(appointmentRepository.findMatchingWeekdayAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W))
                    .thenReturn(expected);

            List<Appointment> result = service.findMatchingWeekdayAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);

            assertThat(result).isSameAs(expected);
            verify(appointmentRepository, times(1))
                    .findMatchingWeekdayAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);
            verifyNoMoreInteractions(appointmentRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findMatchingWeekendAppointments
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findMatchingWeekendAppointments:
     *   No conditional branches. Pure delegation to AppointmentRepository.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindMatchingWeekendAppointments {

        @Test
        void should_delegateToRepository_when_called() {
            List<Appointment> expected = List.of(buildAppointment());
            when(appointmentRepository.findMatchingWeekendAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W))
                    .thenReturn(expected);

            List<Appointment> result = service.findMatchingWeekendAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);

            assertThat(result).isSameAs(expected);
            verify(appointmentRepository, times(1))
                    .findMatchingWeekendAppointments(BASE_MIN, MAX_DAYS, MIN_LEN, OFFSET_W);
            verifyNoMoreInteractions(appointmentRepository);
        }
    }
}
