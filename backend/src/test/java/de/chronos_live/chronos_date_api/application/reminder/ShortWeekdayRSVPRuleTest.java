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
 * Unit tests for {@link ShortWeekdayRSVPRule}.
 *
 * <p>Note on the "weekday" definition in this codebase:
 * {@code isWeekday} returns {@code true} for Monday–Thursday (values 1–4) only.
 * Friday (value 5) is treated as "weekend" by this rule.
 *
 * <p><b>Untestable branches:</b><br>
 * None.
 */
@ExtendWith(MockitoExtension.class)
class ShortWeekdayRSVPRuleTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    // 2024-01-01 = Monday (DayOfWeek value 1)
    private static final Instant MONDAY         = Instant.parse("2024-01-01T12:00:00Z");
    // 2024-01-05 = Friday (DayOfWeek value 5 → "weekend" per this rule)
    private static final Instant FRIDAY         = Instant.parse("2024-01-05T12:00:00Z");
    private static final long    APPOINTMENT_ID = 7L;

    private final ShortWeekdayRSVPRule rule = new ShortWeekdayRSVPRule();

    @Mock
    private ReminderEventPort eventPort;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildShortWeekdayAppointment() {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(MONDAY);
        a.setEndTime(MONDAY.plus(2, ChronoUnit.HOURS));  // 2 h < 24 h
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // appliesTo / computeTriggerTimes / execute
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – appliesTo (durationHours {@literal <} 24 {@literal &&} isWeekday):
     *   B1  duration {@literal <} 24 h AND weekday (Monday) → true
     *   B2  duration {@literal >=} 24 h               → false (short-circuit; isWeekday not evaluated)
     *   B3  duration {@literal <} 24 h AND !weekday (Friday) → false
     *
     * Coverage plan – computeTriggerTimes: returns exactly 4 triggers (no branches).
     * Coverage plan – execute: calls {@code sendRSVPReminder(id)} (no branches).
     *
     * Total branches: 3  |  Tests: 5
     */
    @Nested
    class AppliesTo {

        // B1: short + weekday → true
        @Test
        void should_returnTrue_when_shortAppointmentOnWeekday() {
            assertThat(rule.appliesTo(buildShortWeekdayAppointment())).isTrue();
        }

        // B2: long (>= 24 h) → false; weekday check never reached
        @Test
        void should_returnFalse_when_longAppointment() {
            Appointment a = new Appointment();
            a.setStartTime(MONDAY);
            a.setEndTime(MONDAY.plus(25, ChronoUnit.HOURS));

            assertThat(rule.appliesTo(a)).isFalse();
        }

        // B3: short + weekend (Friday) → false
        @Test
        void should_returnFalse_when_shortAppointmentStartsOnFriday() {
            Appointment a = new Appointment();
            a.setStartTime(FRIDAY);
            a.setEndTime(FRIDAY.plus(2, ChronoUnit.HOURS));

            assertThat(rule.appliesTo(a)).isFalse();
        }
    }

    @Nested
    class ComputeTriggerTimes {

        @Test
        void should_returnFourTriggersCountingDownToOneDay() {
            List<Instant> triggers = rule.computeTriggerTimes(buildShortWeekdayAppointment());

            assertThat(triggers).containsExactly(
                    MONDAY.minus(7, ChronoUnit.DAYS),
                    MONDAY.minus(4, ChronoUnit.DAYS),
                    MONDAY.minus(2, ChronoUnit.DAYS),
                    MONDAY.minus(1, ChronoUnit.DAYS)
            );
        }
    }

    @Nested
    class Execute {

        @Test
        void should_callSendRSVPReminderWithAppointmentId() {
            rule.execute(buildShortWeekdayAppointment(), eventPort);

            verify(eventPort).sendRSVPReminder(APPOINTMENT_ID);
        }
    }
}
