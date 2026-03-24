package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AppointmentReminderRule}.
 *
 * <p><b>Untestable branches:</b><br>
 * None. {@code appliesTo} is an unconditional {@code true}; both other
 * methods are linear.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentReminderRuleTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final long    APPOINTMENT_ID = 42L;
    private static final Instant START_TIME     = Instant.parse("2024-06-01T10:00:00Z");

    private final AppointmentReminderRule rule = new AppointmentReminderRule();

    @Mock
    private ReminderEventPort eventPort;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(START_TIME);
        a.setEndTime(START_TIME.plus(1, ChronoUnit.HOURS));
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // appliesTo / computeTriggerTimes / execute
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – appliesTo:      no branches, always {@code true}.
     * Coverage plan – computeTriggerTimes: returns 1 trigger: start − 30 min.
     * Coverage plan – execute:         calls {@code sendReminder(id)}.
     *
     * Total branches: 0  |  Tests: 3
     */
    @Nested
    class AppliesTo {

        @Test
        void should_returnTrue_when_anyAppointment() {
            assertThat(rule.appliesTo(buildAppointment())).isTrue();
        }
    }

    @Nested
    class ComputeTriggerTimes {

        @Test
        void should_returnOneItemThirtyMinutesBeforeStart() {
            List<Instant> triggers = rule.computeTriggerTimes(buildAppointment());

            assertThat(triggers).containsExactly(START_TIME.minus(30, ChronoUnit.MINUTES));
        }
    }

    @Nested
    class Execute {

        @Test
        void should_callSendReminderWithAppointmentId() {
            rule.execute(buildAppointment(), eventPort);

            verify(eventPort).sendReminder(APPOINTMENT_ID);
        }
    }
}
