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
 * Unit tests for {@link LongAppointmentRSVPRule}.
 *
 * <p><b>Untestable branches:</b><br>
 * None. The {@code while} loop in {@code computeTriggerTimes} always enters
 * at least once (cursor starts 14 days before start) and always exits when
 * cursor reaches start. Both edges are covered by every normal invocation.
 */
@ExtendWith(MockitoExtension.class)
class LongAppointmentRSVPRuleTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Instant START          = Instant.parse("2024-01-15T12:00:00Z");
    private static final long    APPOINTMENT_ID = 15L;
    // 4 fixed triggers + 14 daily (start−14d … start−1d inclusive) = 18
    private static final int     EXPECTED_TRIGGER_COUNT = 18;

    private final LongAppointmentRSVPRule rule = new LongAppointmentRSVPRule();

    @Mock
    private ReminderEventPort eventPort;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildLongAppointment() {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(START);
        a.setEndTime(START.plus(48, ChronoUnit.HOURS));  // 48 h >= 24 h
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // appliesTo / computeTriggerTimes / execute
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – appliesTo (durationHours {@literal >=} 24):
     *   B1  duration {@literal >=} 24 h → true
     *   B2  duration {@literal <}  24 h → false
     *
     * Coverage plan – computeTriggerTimes (while loop):
     *   BW1  cursor.isBefore(start) = true  → enter loop body (14 iterations)
     *   BW2  cursor.isBefore(start) = false → exit loop
     *   Both edges covered by a single invocation with a 48-hour appointment.
     *
     * Coverage plan – execute: calls {@code sendRSVPReminder(id)} (no branches).
     *
     * Total branches: 2 + 2  |  Tests: 5
     */
    @Nested
    class AppliesTo {

        // B1: 48 h >= 24 h → true
        @Test
        void should_returnTrue_when_appointmentIsAtLeast24Hours() {
            assertThat(rule.appliesTo(buildLongAppointment())).isTrue();
        }

        // B2: 2 h < 24 h → false
        @Test
        void should_returnFalse_when_appointmentIsShorterThan24Hours() {
            Appointment a = new Appointment();
            a.setStartTime(START);
            a.setEndTime(START.plus(2, ChronoUnit.HOURS));

            assertThat(rule.appliesTo(a)).isFalse();
        }
    }

    @Nested
    class ComputeTriggerTimes {

        // BW1 (loop iterates 14 times) + BW2 (loop exits)
        @Test
        void should_returnFourFixedPlusFourteenDailyTriggers() {
            List<Instant> triggers = rule.computeTriggerTimes(buildLongAppointment());

            assertThat(triggers).hasSize(EXPECTED_TRIGGER_COUNT);
            // Four fixed triggers
            assertThat(triggers.get(0)).isEqualTo(START.minus(16 * 7, ChronoUnit.DAYS));
            assertThat(triggers.get(1)).isEqualTo(START.minus(8 * 7, ChronoUnit.DAYS));
            assertThat(triggers.get(2)).isEqualTo(START.minus(4 * 7, ChronoUnit.DAYS));
            assertThat(triggers.get(3)).isEqualTo(START.minus(2 * 7, ChronoUnit.DAYS));
            // First daily trigger coincides with fixed trigger 4 (start − 14 days)
            assertThat(triggers.get(4)).isEqualTo(START.minus(2 * 7, ChronoUnit.DAYS));
            // Last daily trigger is the day before start
            assertThat(triggers.get(EXPECTED_TRIGGER_COUNT - 1))
                    .isEqualTo(START.minus(1, ChronoUnit.DAYS));
        }

        @Test
        void should_notIncludeStartInstantOrLater_when_computing() {
            List<Instant> triggers = rule.computeTriggerTimes(buildLongAppointment());

            triggers.forEach(trigger -> assertThat(trigger).isBefore(START));
        }
    }

    @Nested
    class Execute {

        @Test
        void should_callSendRSVPReminderWithAppointmentId() {
            rule.execute(buildLongAppointment(), eventPort);

            verify(eventPort).sendRSVPReminder(APPOINTMENT_ID);
        }
    }
}
