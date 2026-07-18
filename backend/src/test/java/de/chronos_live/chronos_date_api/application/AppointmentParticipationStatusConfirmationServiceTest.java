package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusConfirmationEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentParticipationStatusConfirmationService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all CDI
 * dependencies. The scheduler method is invoked directly (as a package-private
 * method visible from the same package) so no actual cron trigger is needed.
 *
 * <p><b>Untestable branches:</b><br>
 * The 30-day and 7-day time windows are computed from {@code Instant.now()} inside
 * the method; they cannot be injected without refactoring. The tests therefore stub
 * {@code findNonCancelledBetween} to accept {@code any()} matchers
 * and focus on the conditional logic that follows (long-duration filter and weekday
 * filter).
 */
@QuarkusTest
class AppointmentParticipationStatusConfirmationServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long APPOINTMENT_ID_1 = 100L;
    private static final Long APPOINTMENT_ID_2 = 200L;

    // Known UTC weekend instant: 2024-06-01 is a Saturday
    private static final Instant SATURDAY_START = Instant.parse("2024-06-01T10:00:00Z");
    // Known UTC weekday instant: 2024-06-03 is a Monday
    private static final Instant MONDAY_START   = Instant.parse("2024-06-03T10:00:00Z");

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentParticipationStatusConfirmationService service;

    @InjectMock
    AppointmentRepository appointmentRepository;

    @InjectMock
    LeaderElectionService leaderElectionService;

    @InjectMock
    Event<AppointmentParticipationStatusConfirmationEvent> appointmentParticipationStatusConfirmationEvent;

    // ── Builder ───────────────────────────────────────────────────────────────
    private static Appointment buildAppointment(Long id, Instant startTime, Instant endTime) {
        Appointment a = new Appointment();
        a.id = id;
        a.setStartTime(startTime);
        a.setEndTime(endTime);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // triggerAppointmentParticipationStatusConfirmation — leader check
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – leader check:
     *   B1  isLeader() == false → true (early return, no further processing)
     *   B1  isLeader() == true  → proceed
     *
     * Total branches: 2  |  Tests: 1 (false case); remaining tests cover true case.
     */
    @Nested
    class LeaderCheck {

        @Test
        void should_doNothing_when_notLeader() {
            when(leaderElectionService.isLeader()).thenReturn(false);

            service.triggerAppointmentParticipationStatusConfirmation();

            verifyNoInteractions(appointmentRepository);
            verifyNoInteractions(appointmentParticipationStatusConfirmationEvent);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // triggerAppointmentParticipationStatusConfirmation — long appointment filter
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – long appointment filter (30-day window):
     *   B1  duration < 24 hours → true (continue, skip event)
     *   B2  duration >= 24 hours → false (fire event)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class LongAppointmentFilter {

        @Test
        void should_fireEvent_when_appointmentDurationIsAtLeast24Hours() {
            Instant start = Instant.parse("2024-07-01T10:00:00Z");
            Instant end   = start.plus(24, ChronoUnit.HOURS);   // exactly 24 h (not < 24)
            Appointment longAppt = buildAppointment(APPOINTMENT_ID_1, start, end);

            when(leaderElectionService.isLeader()).thenReturn(true);
            // first call: 30-day window → returns long appointment
            // second call: 7-day window → empty (no weekend tests here)
            when(appointmentRepository.findNonCancelledBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(longAppt))
                    .thenReturn(List.of());

            service.triggerAppointmentParticipationStatusConfirmation();

            ArgumentCaptor<AppointmentParticipationStatusConfirmationEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationStatusConfirmationEvent.class);
            verify(appointmentParticipationStatusConfirmationEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID_1);
        }

        @Test
        void should_skipEvent_when_appointmentDurationIsLessThan24Hours() {
            Instant start = Instant.parse("2024-07-01T10:00:00Z");
            Instant end   = start.plus(23, ChronoUnit.HOURS);   // 23 h → less than 24
            Appointment shortAppt = buildAppointment(APPOINTMENT_ID_1, start, end);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentRepository.findNonCancelledBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(shortAppt))
                    .thenReturn(List.of());

            service.triggerAppointmentParticipationStatusConfirmation();

            verify(appointmentParticipationStatusConfirmationEvent, never())
                    .fire(any(AppointmentParticipationStatusConfirmationEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // triggerAppointmentParticipationStatusConfirmation — weekend appointment filter
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – weekend appointment filter (7-day window):
     *   B1  appointment falls on Monday–Thursday → true (continue, skip event)
     *   B2  appointment falls on Friday–Sunday   → false (fire event)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class WeekendAppointmentFilter {

        @Test
        void should_fireEvent_when_appointmentIsOnWeekend() {
            // SATURDAY_START is Saturday — weekdays list does not contain Saturday
            Appointment weekendAppt = buildAppointment(APPOINTMENT_ID_2,
                    SATURDAY_START, SATURDAY_START.plus(1, ChronoUnit.HOURS));

            when(leaderElectionService.isLeader()).thenReturn(true);
            // first call: 30-day window → empty
            // second call: 7-day window → weekend appointment
            when(appointmentRepository.findNonCancelledBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of())
                    .thenReturn(List.of(weekendAppt));

            service.triggerAppointmentParticipationStatusConfirmation();

            ArgumentCaptor<AppointmentParticipationStatusConfirmationEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationStatusConfirmationEvent.class);
            verify(appointmentParticipationStatusConfirmationEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID_2);
        }

        @Test
        void should_skipEvent_when_appointmentIsOnWeekday() {
            // MONDAY_START is Monday — weekdays list contains Monday → continue
            Appointment weekdayAppt = buildAppointment(APPOINTMENT_ID_2,
                    MONDAY_START, MONDAY_START.plus(1, ChronoUnit.HOURS));

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentRepository.findNonCancelledBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of())
                    .thenReturn(List.of(weekdayAppt));

            service.triggerAppointmentParticipationStatusConfirmation();

            verify(appointmentParticipationStatusConfirmationEvent, never())
                    .fire(any(AppointmentParticipationStatusConfirmationEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Combined: both windows populated
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Verifies that events from both the 30-day window and the 7-day window are
     * fired in the same invocation, and that both query calls are made.
     */
    @Nested
    class BothWindowsPopulated {

        @Test
        void should_fireTwoEvents_when_bothWindowsReturnEligibleAppointments() {
            Instant longStart = Instant.parse("2024-07-01T10:00:00Z");
            Instant longEnd   = longStart.plus(48, ChronoUnit.HOURS);
            Appointment longAppt = buildAppointment(APPOINTMENT_ID_1, longStart, longEnd);

            Appointment weekendAppt = buildAppointment(APPOINTMENT_ID_2,
                    SATURDAY_START, SATURDAY_START.plus(2, ChronoUnit.HOURS));

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentRepository.findNonCancelledBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(longAppt))
                    .thenReturn(List.of(weekendAppt));

            service.triggerAppointmentParticipationStatusConfirmation();

            verify(appointmentParticipationStatusConfirmationEvent, times(2))
                    .fire(any(AppointmentParticipationStatusConfirmationEvent.class));
            // Verify findNonCancelledBetween was called exactly twice
            verify(appointmentRepository, times(2))
                    .findNonCancelledBetween(any(Instant.class), any(Instant.class));
        }
    }
}
