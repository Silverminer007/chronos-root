package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentQueryService}.
 *
 * Strategy: @QuarkusTest with @InjectMock replaces AppointmentRepository with a
 * Mockito mock. AppointmentQueryService is a pure delegator, so tests verify
 * correct delegation and argument passing.
 */
@QuarkusTest
class AppointmentQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   APPOINTMENT_ID = 42L;
    private static final Long   UNKNOWN_ID     = 999L;
    private static final String USER_OIDC      = "oidc-user-1";

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
    // getAppointment — delegates to appointmentRepository.getAppointment()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        @Test
        void should_returnAppointment_when_allFlagsFalse() {
            Appointment expected = buildAppointment();
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, false, false, false)).thenReturn(expected);

            Appointment result = service.getAppointment(APPOINTMENT_ID, false, false, false);

            assertThat(result).isSameAs(expected);
            verify(appointmentRepository).getAppointment(APPOINTMENT_ID, false, false, false);
        }

        @Test
        void should_delegateWithMessagesFlag_when_messagesFlagIsTrue() {
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, true, false, false)).thenReturn(buildAppointment());

            service.getAppointment(APPOINTMENT_ID, true, false, false);

            verify(appointmentRepository).getAppointment(APPOINTMENT_ID, true, false, false);
        }

        @Test
        void should_delegateWithParticipantsFlag_when_participantsFlagIsTrue() {
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, false, true, false)).thenReturn(buildAppointment());

            service.getAppointment(APPOINTMENT_ID, false, true, false);

            verify(appointmentRepository).getAppointment(APPOINTMENT_ID, false, true, false);
        }

        @Test
        void should_delegateWithGroupParticipantsFlag_when_groupParticipantsFlagIsTrue() {
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, false, false, true)).thenReturn(buildAppointment());

            service.getAppointment(APPOINTMENT_ID, false, false, true);

            verify(appointmentRepository).getAppointment(APPOINTMENT_ID, false, false, true);
        }

        @Test
        void should_throwResourceNotFoundException_when_appointmentNotFound() {
            when(appointmentRepository.getAppointment(UNKNOWN_ID, false, false, false))
                    .thenThrow(new ResourceNotFoundException("appointment", UNKNOWN_ID));

            assertThatThrownBy(() -> service.getAppointment(UNKNOWN_ID, false, false, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("appointment mit ID " + UNKNOWN_ID + " wurde nicht gefunden");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // search — delegates to appointmentRepository.search()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Search {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        @Test
        void should_returnSearchResult_when_queryIsNull() {
            AppointmentRepository.SearchResult repoResult =
                    new AppointmentRepository.SearchResult(List.of(buildAppointment()), 1L);
            when(appointmentRepository.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false))
                    .thenReturn(repoResult);

            AppointmentQueryService.SearchResult result =
                    service.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false);

            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.items()).hasSize(1);
            verify(appointmentRepository).search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false);
        }

        @Test
        void should_returnSearchResult_when_queryIsNotNull() {
            List<Appointment> items = List.of(buildAppointment(), buildAppointment(), buildAppointment());
            AppointmentRepository.SearchResult repoResult = new AppointmentRepository.SearchResult(items, 3L);
            when(appointmentRepository.search(USER_OIDC, "meeting", AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false))
                    .thenReturn(repoResult);

            AppointmentQueryService.SearchResult result =
                    service.search(USER_OIDC, "meeting", AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, false);

            assertThat(result.total()).isEqualTo(3L);
            assertThat(result.items()).hasSize(3);
        }

        @Test
        void should_delegateWithMessagesFlag_when_messagesFlagIsTrue() {
            AppointmentRepository.SearchResult repoResult = new AppointmentRepository.SearchResult(List.of(), 0L);
            when(appointmentRepository.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, true, false, false))
                    .thenReturn(repoResult);

            service.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, true, false, false);

            verify(appointmentRepository).search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, true, false, false);
        }

        @Test
        void should_delegateWithParticipantsFlag_when_participantsFlagIsTrue() {
            AppointmentRepository.SearchResult repoResult = new AppointmentRepository.SearchResult(List.of(), 0L);
            when(appointmentRepository.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, true, false))
                    .thenReturn(repoResult);

            service.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, true, false);

            verify(appointmentRepository).search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, true, false);
        }

        @Test
        void should_delegateWithGroupParticipantsFlag_when_groupParticipantsFlagIsTrue() {
            AppointmentRepository.SearchResult repoResult = new AppointmentRepository.SearchResult(List.of(), 0L);
            when(appointmentRepository.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, true))
                    .thenReturn(repoResult);

            service.search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, true);

            verify(appointmentRepository).search(USER_OIDC, null, AFTER, BEFORE, PAGE, PAGE_SIZE, false, false, true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getNonCancelledAppointmentsStartingAt
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetNonCancelledAppointmentsStartingAt {

        @Test
        void should_delegateWithCorrectTimeWindow_when_calledWithInstant() {
            Instant expectedAfter  = T0.minusSeconds(30);
            Instant expectedBefore = T0.plusSeconds(30);
            List<Appointment> expected = List.of(buildAppointment());
            when(appointmentRepository.findNonCancelledBetween(expectedAfter, expectedBefore)).thenReturn(expected);

            List<Appointment> result = service.getNonCancelledAppointmentsStartingAt(T0);

            assertThat(result).hasSize(1);
            verify(appointmentRepository).findNonCancelledBetween(expectedAfter, expectedBefore);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getNonCancelledAppointmentsStartingBetween
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetNonCancelledAppointmentsStartingBetween {

        @Test
        void should_returnAppointments_when_found() {
            Appointment a = buildAppointment();
            when(appointmentRepository.findNonCancelledBetween(AFTER, BEFORE)).thenReturn(List.of(a));

            List<Appointment> result = service.getNonCancelledAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).containsExactly(a);
            verify(appointmentRepository).findNonCancelledBetween(AFTER, BEFORE);
        }

        @Test
        void should_returnEmptyList_when_noneFound() {
            when(appointmentRepository.findNonCancelledBetween(AFTER, BEFORE)).thenReturn(List.of());

            List<Appointment> result = service.getNonCancelledAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPlannedAppointmentsStartingBetween
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetPlannedAppointmentsStartingBetween {

        @Test
        void should_returnPlannedAppointments_when_found() {
            Appointment a = buildAppointment();
            when(appointmentRepository.findPlannedBetween(AFTER, BEFORE)).thenReturn(List.of(a));

            List<Appointment> result = service.getPlannedAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).containsExactly(a);
            verify(appointmentRepository).findPlannedBetween(AFTER, BEFORE);
        }

        @Test
        void should_returnEmptyList_when_noneFound() {
            when(appointmentRepository.findPlannedBetween(AFTER, BEFORE)).thenReturn(List.of());

            List<Appointment> result = service.getPlannedAppointmentsStartingBetween(AFTER, BEFORE);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findMatchingAppointments
    // ══════════════════════════════════════════════════════════════════════════

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
