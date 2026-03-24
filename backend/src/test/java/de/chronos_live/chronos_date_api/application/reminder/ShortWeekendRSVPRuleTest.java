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
 * Unit tests for {@link ShortWeekendRSVPRule}.
 *
 * <p>Note on the "weekend" definition in this codebase:
 * {@code isWeekend} returns {@code true} for Friday–Sunday (values 5–7).
 *
 * <p><b>Untestable branches:</b><br>
 * None. The {@code while} loop in {@code computeTriggerTimes} always enters
 * at least once (cursor begins 7 days before start) and always exits (cursor
 * eventually reaches start). Both edges are covered by every normal invocation.
 */
@ExtendWith(MockitoExtension.class)
class ShortWeekendRSVPRuleTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    // 2024-01-05 = Friday (DayOfWeek value 5 → "weekend" per this rule)
    private static final Instant FRIDAY           = Instant.parse("2024-01-05T12:00:00Z");
    // 2024-01-01 = Monday (DayOfWeek value 1 → "weekday", not weekend)
    private static final Instant MONDAY           = Instant.parse("2024-01-01T12:00:00Z");
    private static final long    APPOINTMENT_ID   = 8L;
    // 3 fixed triggers + 7 daily (start−7d … start−1d inclusive) = 10
    private static final int     EXPECTED_TRIGGER_COUNT = 10;

    private final ShortWeekendRSVPRule rule = new ShortWeekendRSVPRule();

    @Mock
    private ReminderEventPort eventPort;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildShortWeekendAppointment() {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(FRIDAY);
        a.setEndTime(FRIDAY.plus(2, ChronoUnit.HOURS));  // 2 h < 24 h
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // appliesTo / computeTriggerTimes / execute
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – appliesTo (durationHours {@literal <} 24 {@literal &&} isWeekend):
     *   B1  duration {@literal <} 24 h AND weekend (Friday) → true
     *   B2  duration {@literal >=} 24 h               → false (short-circuit)
     *   B3  duration {@literal <} 24 h AND !weekend (Monday) → false
     *
     * Coverage plan – computeTriggerTimes (while loop):
     *   BW1  cursor.isBefore(start) = true  → enter loop body (7 iterations)
     *   BW2  cursor.isBefore(start) = false → exit loop
     *   Both edges covered in a single test.
     *
     * Coverage plan – execute: calls {@code sendRSVPReminder(id)} (no branches).
     *
     * Total branches: 3 + 2  |  Tests: 6
     */
    @Nested
    class AppliesTo {

        // B1: short + weekend → true
        @Test
        void should_returnTrue_when_shortAppointmentOnWeekend() {
            assertThat(rule.appliesTo(buildShortWeekendAppointment())).isTrue();
        }

        // B2: long → false (short-circuit; isWeekend not evaluated)
        @Test
        void should_returnFalse_when_longAppointment() {
            Appointment a = new Appointment();
            a.setStartTime(FRIDAY);
            a.setEndTime(FRIDAY.plus(25, ChronoUnit.HOURS));

            assertThat(rule.appliesTo(a)).isFalse();
        }

        // B3: short + weekday (Monday) → false
        @Test
        void should_returnFalse_when_shortAppointmentOnWeekday() {
            Appointment a = new Appointment();
            a.setStartTime(MONDAY);
            a.setEndTime(MONDAY.plus(2, ChronoUnit.HOURS));

            assertThat(rule.appliesTo(a)).isFalse();
        }
    }

    @Nested
    class ComputeTriggerTimes {

        // BW1 (loop enters 7 times) + BW2 (loop exits)
        @Test
        void should_returnThreeFixedPlusSevenDailyTriggers() {
            List<Instant> triggers = rule.computeTriggerTimes(buildShortWeekendAppointment());

            assertThat(triggers).hasSize(EXPECTED_TRIGGER_COUNT);
            // Three fixed triggers
            assertThat(triggers.get(0)).isEqualTo(FRIDAY.minus(4 * 7, ChronoUnit.DAYS));
            assertThat(triggers.get(1)).isEqualTo(FRIDAY.minus(2 * 7, ChronoUnit.DAYS));
            assertThat(triggers.get(2)).isEqualTo(FRIDAY.minus(7, ChronoUnit.DAYS));
            // First daily trigger coincides with fixed trigger 3 (start − 7 days)
            assertThat(triggers.get(3)).isEqualTo(FRIDAY.minus(7, ChronoUnit.DAYS));
            // Last daily trigger is the day before start
            assertThat(triggers.get(EXPECTED_TRIGGER_COUNT - 1))
                    .isEqualTo(FRIDAY.minus(1, ChronoUnit.DAYS));
        }

        @Test
        void should_notIncludeStartInstantOrLater_when_computing() {
            List<Instant> triggers = rule.computeTriggerTimes(buildShortWeekendAppointment());

            triggers.forEach(trigger -> assertThat(trigger).isBefore(FRIDAY));
        }
    }

    @Nested
    class Execute {

        @Test
        void should_callSendRSVPReminderWithAppointmentId() {
            rule.execute(buildShortWeekendAppointment(), eventPort);

            verify(eventPort).sendRSVPReminder(APPOINTMENT_ID);
        }
    }
}
