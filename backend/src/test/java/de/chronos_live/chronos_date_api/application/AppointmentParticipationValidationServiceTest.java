package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentEditedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationInvalidEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusChangedEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
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
 * Unit tests for {@link AppointmentParticipationValidationService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock}. {@link PanacheMock}
 * intercepts static Panache calls inside the observer methods.
 *
 * <p><b>Coverage plan – sendNonMinimalAttendeesAlerts:</b>
 * <ul>
 *   <li>B1  {@code isLeader()} = false → early return</li>
 *   <li>B2  long-appt list: duration {@literal <} 24h → continue (skip)</li>
 *   <li>B3  long-appt list: duration {@literal >=} 24h → checkForEnoughAttendees</li>
 *   <li>B4  weekday-appt list: not weekday → continue (skip)</li>
 *   <li>B5  weekday-appt list: is weekday → checkForEnoughAttendees</li>
 *   <li>B6  weekend-appt list: is weekday → continue (skip)</li>
 *   <li>B7  weekend-appt list: is weekend → checkForEnoughAttendees</li>
 * </ul>
 *
 * <p><b>Coverage plan – checkForEnoughAttendees:</b>
 * <ul>
 *   <li>B1  status = CANCELLED → early return</li>
 *   <li>B2  approvedCount {@literal >=} minimalAttendees → early return</li>
 *   <li>B3  status = NOT_ENOUGH_ATTENDEES → early return</li>
 *   <li>B4  status = PLANNED, approved {@literal <} minimal → set status + fire</li>
 * </ul>
 *
 * <p><b>Coverage plan – onAppointmentParticipationStatusChanged:</b>
 * <ul>
 *   <li>B1  startTime is in the past → early return</li>
 *   <li>B2  status = CANCELLED → early return</li>
 *   <li>B3  status = DELETED → early return</li>
 *   <li>B4  newStatus = REJECTED and status = NOT_ENOUGH_ATTENDEES → early return</li>
 *   <li>B5  status = PLANNED, participant-rejected count {@literal >=} minimal → early return</li>
 *   <li>B6  status = PLANNED, not enough → set NOT_ENOUGH_ATTENDEES + fire</li>
 *   <li>B7  status = NOT_ENOUGH_ATTENDEES, not enough → early return</li>
 *   <li>B8  status = NOT_ENOUGH_ATTENDEES, enough → set PLANNED</li>
 * </ul>
 *
 * <p><b>Coverage plan – onAppointmentEdited:</b>
 * <ul>
 *   <li>B1  minimalAttendees = null → early return</li>
 *   <li>B2  startTime in the past → early return</li>
 *   <li>B3  status = CANCELLED → early return</li>
 *   <li>B4  status = DELETED → early return</li>
 *   <li>B5  duration {@literal >=} 24h → feedbackDeadline = 8 weeks</li>
 *   <li>B6  duration {@literal <} 24h AND weekday → feedbackDeadline = 1 week</li>
 *   <li>B7  duration {@literal <} 24h AND weekend → feedbackDeadline = 2 weeks</li>
 *   <li>B8  deadlineWeeks {@literal >} weeksUntilAppt (past deadline): approved {@literal >=} minimal → return</li>
 *   <li>B9  deadlineWeeks {@literal >} weeksUntilAppt (past deadline): approved {@literal <} minimal → fire</li>
 *   <li>B10 deadlineWeeks {@literal <=} weeksUntilAppt (future): participants-rejected {@literal >=} minimal → return</li>
 *   <li>B11 deadlineWeeks {@literal <=} weeksUntilAppt (future): participants-rejected {@literal <} minimal → fire</li>
 * </ul>
 *
 * Total branches: 34 | Tests: 22
 */
@QuarkusTest
class AppointmentParticipationValidationServiceTest {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final Long APPT_ID     = 42L;
    private static final Long ACTING_USER = 1L;

    // Instants used by the scheduler tests:
    // future = well beyond "now" so isBefore(Instant.now()) = false
    private static final Instant FUTURE_START      = Instant.now().plus(120, ChronoUnit.DAYS);
    private static final Instant FUTURE_START_LONG = FUTURE_START.plus(25, ChronoUnit.HOURS);  // >= 24h span
    private static final Instant FUTURE_END_SHORT  = FUTURE_START.plus(2, ChronoUnit.HOURS);   // < 24h span

    // A past instant so "start is in the past" guard is true
    private static final Instant PAST_START = Instant.parse("2020-01-01T10:00:00Z");
    private static final Instant PAST_END   = PAST_START.plus(2, ChronoUnit.HOURS);

    // Known weekday (Thursday 2025-01-02 UTC) and weekend (Saturday 2025-01-04 UTC)
    private static final Instant WEEKDAY_START  = Instant.parse("2025-01-02T10:00:00Z");
    private static final Instant WEEKEND_START  = Instant.parse("2025-01-04T10:00:00Z");

    // ── CDI injection ────────────────────────────────────────────────────────
    @Inject
    AppointmentParticipationValidationService service;

    @InjectMock
    AppointmentQueryService appointmentQueryService;

    @InjectMock
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @InjectMock
    LeaderElectionService leaderElectionService;

    @InjectMock
    Event<AppointmentParticipationInvalidEvent> appointmentParticipationInvalidEvent;

    // MeterRegistry is injected by the service – the real Quarkus Micrometer
    // bean is available in the test CDI container, no mock needed.

    // ── Test-object builders ─────────────────────────────────────────────────
    private static Appointment buildAppointment(Long id, Instant start, Instant end,
                                                AppointmentStatus status, Integer minAttendees) {
        Appointment a = new Appointment();
        a.id = id;
        a.setName("Test");
        a.setStartTime(start);
        a.setEndTime(end);
        a.setStatus(status);
        a.setMinimalAttendees(minAttendees);
        return a;
    }

    private static AppointmentParticipationStatusChangedEvent statusChangedEvent(
            Long appointmentId, ParticipationStatus newStatus) {
        return new AppointmentParticipationStatusChangedEvent(
                appointmentId, ACTING_USER, newStatus, ParticipationStatus.PENDING);
    }

    // ══════════════════════════════════════════════════════════════════════
    // sendNonMinimalAttendeesAlerts
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class SendNonMinimalAttendeesAlerts {

        // B1: not leader → early return
        @Test
        void should_doNothing_when_notLeader() {
            when(leaderElectionService.isLeader()).thenReturn(false);

            service.sendNonMinimalAttendeesAlerts();

            verifyNoInteractions(appointmentQueryService);
        }

        // B2: long-appointment window — appointment duration < 24h → skipped
        @Test
        void should_skipLongWindowAppointment_when_durationIsLessThan24Hours() {
            Appointment shortAppt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, 2);

            when(leaderElectionService.isLeader()).thenReturn(true);
            // long-window returns this short appointment (will be skipped)
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of(shortAppt))  // first call → long window
                    .thenReturn(List.of())            // second call → weekday window
                    .thenReturn(List.of());           // third call → weekend window

            service.sendNonMinimalAttendeesAlerts();

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B3: long-appointment window — duration >= 24h → checkForEnoughAttendees called
        // Here we make sure the inner check fires an event.
        @Test
        void should_checkAttendees_when_longWindowAppointmentHasSufficientDuration() {
            Appointment longAppt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_START_LONG,
                    AppointmentStatus.PLANNED, 5);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of(longAppt))
                    .thenReturn(List.of())
                    .thenReturn(List.of());
            // 0 approved < 5 minimum → will fire
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(3, 0, 0));

            service.sendNonMinimalAttendeesAlerts();

            ArgumentCaptor<AppointmentParticipationInvalidEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationInvalidEvent.class);
            verify(appointmentParticipationInvalidEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPT_ID);
            assertThat(longAppt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
        }

        // B4: weekday window — appointment is weekend → skipped
        @Test
        void should_skipWeekdayWindowAppointment_when_startTimeIsWeekend() {
            // Saturday
            Appointment weAppt = buildAppointment(APPT_ID, WEEKEND_START, WEEKEND_START.plus(2, ChronoUnit.HOURS),
                    AppointmentStatus.PLANNED, 2);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of())      // long window empty
                    .thenReturn(List.of(weAppt)) // weekday window — but it's a weekend appointment
                    .thenReturn(List.of());

            service.sendNonMinimalAttendeesAlerts();

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B5: weekday window — appointment is weekday → checkForEnoughAttendees called
        @Test
        void should_checkAttendees_when_weekdayWindowAppointmentIsWeekday() {
            Appointment wdAppt = buildAppointment(APPT_ID, WEEKDAY_START, WEEKDAY_START.plus(2, ChronoUnit.HOURS),
                    AppointmentStatus.PLANNED, 3);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of())
                    .thenReturn(List.of(wdAppt))
                    .thenReturn(List.of());
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(1, 0, 0));

            service.sendNonMinimalAttendeesAlerts();

            verify(appointmentParticipationInvalidEvent, times(1)).fire(any());
        }

        // B6: weekend window — appointment is weekday → skipped
        @Test
        void should_skipWeekendWindowAppointment_when_startTimeIsWeekday() {
            // Thursday = weekday → should be skipped in weekend window
            Appointment wdAppt = buildAppointment(APPT_ID, WEEKDAY_START, WEEKDAY_START.plus(2, ChronoUnit.HOURS),
                    AppointmentStatus.PLANNED, 2);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of())
                    .thenReturn(List.of())
                    .thenReturn(List.of(wdAppt)); // weekend window, but weekday appointment

            service.sendNonMinimalAttendeesAlerts();

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B7: weekend window — appointment is weekend → checkForEnoughAttendees called
        @Test
        void should_checkAttendees_when_weekendWindowAppointmentIsWeekend() {
            Appointment weAppt = buildAppointment(APPT_ID, WEEKEND_START, WEEKEND_START.plus(3, ChronoUnit.HOURS),
                    AppointmentStatus.PLANNED, 4);

            when(leaderElectionService.isLeader()).thenReturn(true);
            when(appointmentQueryService.getPlannedAppointmentsStartingBetween(any(), any()))
                    .thenReturn(List.of())
                    .thenReturn(List.of())
                    .thenReturn(List.of(weAppt));
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(2, 1, 0));

            service.sendNonMinimalAttendeesAlerts();

            verify(appointmentParticipationInvalidEvent, times(1)).fire(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // checkForEnoughAttendees
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class CheckForEnoughAttendees {

        // B1: status = CANCELLED → immediate return, no query
        @Test
        void should_returnImmediately_when_appointmentIsCancelled() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.CANCELLED, 2);

            service.checkForEnoughAttendees(appt);

            verifyNoInteractions(appointmentParticipationQueryService);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B2: approvedCount >= minimalAttendees → early return, no event
        @Test
        void should_returnImmediately_when_approvedCountMeetsMinimum() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, 2);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(5, 3, 0)); // 3 approved >= 2 min

            service.checkForEnoughAttendees(appt);

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
        }

        // B3: approved < min AND status already NOT_ENOUGH_ATTENDEES → no re-fire
        @Test
        void should_notFireEvent_when_statusAlreadyNotEnoughAttendees() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.NOT_ENOUGH_ATTENDEES, 3);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(2, 1, 0)); // 1 approved < 3 min

            service.checkForEnoughAttendees(appt);

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B4: PLANNED, approved < min → set NOT_ENOUGH_ATTENDEES + fire event
        @Test
        void should_setStatusAndFireEvent_when_notEnoughApprovedAttendees() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, 5);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(2, 1, 0)); // 1 approved < 5 min

            service.checkForEnoughAttendees(appt);

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
            ArgumentCaptor<AppointmentParticipationInvalidEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationInvalidEvent.class);
            verify(appointmentParticipationInvalidEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationStatusChanged
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class OnAppointmentParticipationStatusChanged {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1: start in the past → early return
        @Test
        void should_returnImmediately_when_appointmentAlreadyStarted() {
            Appointment appt = buildAppointment(APPT_ID, PAST_START, PAST_END,
                    AppointmentStatus.PLANNED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.APPROVED));

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B2: status = CANCELLED → early return
        @Test
        void should_returnImmediately_when_appointmentIsCancelled() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.CANCELLED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.APPROVED));

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B3: status = DELETED → early return
        @Test
        void should_returnImmediately_when_appointmentIsDeleted() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.DELETED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.REJECTED));

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B4: newStatus = REJECTED AND status = NOT_ENOUGH_ATTENDEES → early return
        @Test
        void should_returnImmediately_when_rejectedAndAlreadyNotEnough() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.NOT_ENOUGH_ATTENDEES, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPT_ID, ACTING_USER,
                            ParticipationStatus.REJECTED, ParticipationStatus.PENDING));

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B5: PLANNED, participantCount - rejectedCount >= minimal → no event
        @Test
        void should_notFireEvent_when_plannedAndEnoughParticipantsRemaining() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 5 total, 1 rejected → 5-1=4 >= 2 minimum
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(5, 2, 1));

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.REJECTED));

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
        }

        // B6: PLANNED, participantCount - rejectedCount < minimal → set NOT_ENOUGH + fire
        @Test
        void should_setNotEnoughAndFire_when_plannedAndNotEnoughParticipantsRemaining() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, 5);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 3 total, 1 rejected → 3-1=2 < 5 minimum
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(3, 1, 1));

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.REJECTED));

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
            ArgumentCaptor<AppointmentParticipationInvalidEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationInvalidEvent.class);
            verify(appointmentParticipationInvalidEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPT_ID);
        }

        // B7: NOT_ENOUGH_ATTENDEES, participantCount - rejectedCount < minimal → no change, no event
        @Test
        void should_notChangeStatus_when_notEnoughAndStillNotEnoughParticipants() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.NOT_ENOUGH_ATTENDEES, 5);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 3 total, 1 rejected → 2 < 5 minimum
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(3, 1, 1));

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.APPROVED));

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B8: NOT_ENOUGH_ATTENDEES, participantCount - rejectedCount >= minimal → set PLANNED
        @Test
        void should_setPlanned_when_notEnoughButNowEnoughParticipants() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.NOT_ENOUGH_ATTENDEES, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 5 total, 0 rejected → 5 >= 2 minimum
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(5, 3, 0));

            service.onAppointmentParticipationStatusChanged(
                    statusChangedEvent(APPT_ID, ParticipationStatus.APPROVED));

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // onAppointmentEdited
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class OnAppointmentEdited {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        private AppointmentEditedEvent editedEvent() {
            return new AppointmentEditedEvent(APPT_ID);
        }

        // B1: minimalAttendees = null → early return
        @Test
        void should_returnImmediately_when_minimalAttendeesIsNull() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.PLANNED, null);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentEdited(editedEvent());

            verifyNoInteractions(appointmentParticipationQueryService);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B2: start in the past → early return
        @Test
        void should_returnImmediately_when_appointmentAlreadyStarted() {
            Appointment appt = buildAppointment(APPT_ID, PAST_START, PAST_END,
                    AppointmentStatus.PLANNED, 3);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentEdited(editedEvent());

            verifyNoInteractions(appointmentParticipationQueryService);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B3: status = CANCELLED → early return
        @Test
        void should_returnImmediately_when_appointmentIsCancelled() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.CANCELLED, 3);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentEdited(editedEvent());

            verifyNoInteractions(appointmentParticipationQueryService);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B4: status = DELETED → early return
        @Test
        void should_returnImmediately_when_appointmentIsDeleted() {
            Appointment appt = buildAppointment(APPT_ID, FUTURE_START, FUTURE_END_SHORT,
                    AppointmentStatus.DELETED, 3);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);

            service.onAppointmentEdited(editedEvent());

            verifyNoInteractions(appointmentParticipationQueryService);
            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B5 + B8: duration >= 24h → feedbackDeadline = 8 weeks
        // Appointment 5 weeks in future → weeksUntil(35/7=5) < deadline(8) → use approved check
        // approved(3) >= min(3) → return without firing
        @Test
        void should_returnImmediately_when_longApptPastDeadlineWithEnoughApproved() {
            Instant start = Instant.now().plus(35, ChronoUnit.DAYS);
            Instant end   = start.plus(25, ChronoUnit.HOURS); // >= 24h
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 3);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(5, 3, 0)); // 3 approved >= 3 min

            service.onAppointmentEdited(editedEvent());

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B9: long appt 5 weeks away (past 8-week deadline), approved < min → fire
        @Test
        void should_setNotEnoughAndFire_when_longApptPastDeadlineWithInsufficientApproved() {
            Instant start = Instant.now().plus(35, ChronoUnit.DAYS);
            Instant end   = start.plus(25, ChronoUnit.HOURS); // >= 24h
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 5);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(3, 2, 0)); // 2 approved < 5 min

            service.onAppointmentEdited(editedEvent());

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
            ArgumentCaptor<AppointmentParticipationInvalidEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationInvalidEvent.class);
            verify(appointmentParticipationInvalidEvent, times(1)).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPT_ID);
        }

        // B6 + B10: duration < 24h AND weekday → feedbackDeadline = 1 week
        // Appointment on a weekday 63 days (= 9 weeks) from now → weeksUntil(9) >= deadline(1)
        // → participant check: participants-rejected(4) >= min(2) → return without firing
        @Test
        void should_returnImmediately_when_shortWeekdayApptBeforeDeadlineWithEnoughParticipants() {
            // Start with now + 63 days and snap to the next Mon-Thu
            Instant start = nextWeekdayFromNow(63);
            Instant end   = start.plus(2, ChronoUnit.HOURS); // < 24h
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 4 total, 0 rejected → 4-0=4 >= 2 min
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(4, 2, 0));

            service.onAppointmentEdited(editedEvent());

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B11: short weekday appt 9 weeks away (before 1-week deadline), participants-rejected < min → fire
        @Test
        void should_fireEvent_when_shortWeekdayApptBeforeDeadlineWithInsufficientParticipants() {
            Instant start = nextWeekdayFromNow(63); // 9 weeks from now, weekday
            Instant end   = start.plus(2, ChronoUnit.HOURS);
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 5);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            // 3 total, 1 rejected → 3-1=2 < 5 min
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(3, 1, 1));

            service.onAppointmentEdited(editedEvent());

            assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
            verify(appointmentParticipationInvalidEvent, times(1)).fire(any());
        }

        // B7 + B10: duration < 24h AND weekend → feedbackDeadline = 2 weeks
        // Appointment on a weekend 70 days (= 10 weeks) from now → weeksUntil(10) >= deadline(2)
        // → participant check: participants-rejected(4) >= min(2) → return without firing
        @Test
        void should_returnImmediately_when_shortWeekendApptBeforeDeadlineWithEnoughParticipants() {
            Instant start = nextWeekendFromNow(70); // 10 weeks from now, weekend
            Instant end   = start.plus(3, ChronoUnit.HOURS); // < 24h
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(4, 2, 0));

            service.onAppointmentEdited(editedEvent());

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        // B8 variant: short weekday appt 0 weeks away (past 1-week deadline), approved >= min → return
        @Test
        void should_returnImmediately_when_shortWeekdayApptPastDeadlineWithEnoughApproved() {
            // now + 2h is 0 full days = 0 weeks away → weeksUntil(0) < deadlineWeeks(1) → approved check
            Instant start = nextWeekdayFromNow(0); // weekday within today
            Instant end   = start.plus(2, ChronoUnit.HOURS);
            Appointment appt = buildAppointment(APPT_ID, start, end, AppointmentStatus.PLANNED, 2);
            when(Appointment.<Appointment>findById(APPT_ID)).thenReturn(appt);
            when(appointmentParticipationQueryService.getParticipationStatistik(APPT_ID))
                    .thenReturn(new ParticipationStatistik(5, 3, 0)); // 3 approved >= 2 min

            service.onAppointmentEdited(editedEvent());

            verify(appointmentParticipationInvalidEvent, never()).fire(any());
        }

        /**
         * Returns the first weekday (Mon-Thu UTC) found starting at
         * {@code now + baseDays} days (walking up to 7 additional days if needed).
         * Always at least 1 hour in the future so isBefore(now) stays false.
         */
        private Instant nextWeekdayFromNow(long baseDays) {
            Instant candidate = Instant.now().plus(baseDays, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
            for (int i = 0; i < 7; i++) {
                java.time.DayOfWeek dow = candidate.atZone(java.time.ZoneOffset.UTC).getDayOfWeek();
                if (dow == java.time.DayOfWeek.MONDAY || dow == java.time.DayOfWeek.TUESDAY
                        || dow == java.time.DayOfWeek.WEDNESDAY || dow == java.time.DayOfWeek.THURSDAY) {
                    return candidate;
                }
                candidate = candidate.plus(1, ChronoUnit.DAYS);
            }
            throw new IllegalStateException("Could not find weekday");
        }

        /**
         * Returns the first weekend day (Fri-Sun UTC) found starting at
         * {@code now + baseDays} days (walking up to 7 additional days if needed).
         * Always at least 1 hour in the future so isBefore(now) stays false.
         */
        private Instant nextWeekendFromNow(long baseDays) {
            Instant candidate = Instant.now().plus(baseDays, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
            for (int i = 0; i < 7; i++) {
                java.time.DayOfWeek dow = candidate.atZone(java.time.ZoneOffset.UTC).getDayOfWeek();
                if (dow == java.time.DayOfWeek.FRIDAY || dow == java.time.DayOfWeek.SATURDAY
                        || dow == java.time.DayOfWeek.SUNDAY) {
                    return candidate;
                }
                candidate = candidate.plus(1, ChronoUnit.DAYS);
            }
            throw new IllegalStateException("Could not find weekend day");
        }
    }
}
