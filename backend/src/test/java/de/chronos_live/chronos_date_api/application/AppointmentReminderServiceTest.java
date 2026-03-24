package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.reminder.ReminderRuleEngine;
import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentReminderService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all three CDI
 * dependencies ({@link AppointmentQueryService}, {@link ReminderRuleEngine}, {@link Clock})
 * with Mockito mocks. The service has a single linear method with no conditional branches;
 * the only observable behaviour is the correct delegation sequence.
 *
 * <p><b>Untestable branches:</b><br>
 * None. {@code sendPendingReminders} contains no branches.
 */
@QuarkusTest
class AppointmentReminderServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Instant NOW            = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant EXPECTED_BEFORE = NOW.plus(20 * 7, ChronoUnit.DAYS);

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentReminderService service;

    @InjectMock
    AppointmentQueryService queryService;

    @InjectMock
    ReminderRuleEngine engine;

    @InjectMock
    Clock clock;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.setName("Test Appointment");
        a.setStartTime(NOW);
        a.setEndTime(NOW.plus(1, ChronoUnit.HOURS));
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendPendingReminders
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendPendingReminders:
     *   No branches — reads clock, fetches appointments in [now, now+20w), passes to engine.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class SendPendingReminders {

        @Test
        void should_fetchAppointmentsIn20WeeksWindowAndEvaluate_when_called() {
            List<Appointment> appointments = List.of(buildAppointment());
            when(clock.instant()).thenReturn(NOW);
            when(queryService.getNonCancelledAppointmentsStartingBetween(NOW, EXPECTED_BEFORE))
                    .thenReturn(appointments);

            service.sendPendingReminders();

            verify(clock).instant();
            verify(queryService).getNonCancelledAppointmentsStartingBetween(NOW, EXPECTED_BEFORE);
            verify(engine).evaluate(appointments, NOW);
            verifyNoMoreInteractions(queryService, engine);
        }
    }
}
