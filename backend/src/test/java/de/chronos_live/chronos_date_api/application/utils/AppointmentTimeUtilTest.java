package de.chronos_live.chronos_date_api.application.utils;

import de.chronos_live.chronos_date_api.domain.Appointment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppointmentTimeUtil}.
 *
 * <p>All methods are static; no mocking is required.
 *
 * <p>Weekday / weekend semantics in this codebase:
 * <ul>
 *   <li>{@code isWeekday}: Monday–Thursday (values 1–4) → {@code true}</li>
 *   <li>{@code isWeekend}: Friday–Sunday  (values 5–7) → {@code true}</li>
 *   <li>Friday is "weekend", <em>not</em> "weekday"</li>
 * </ul>
 *
 * <p><b>Untestable branches:</b><br>
 * None.
 */
class AppointmentTimeUtilTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    // 2024-01-01 = Monday   (DayOfWeek value 1)
    private static final Instant MONDAY   = Instant.parse("2024-01-01T12:00:00Z");
    // 2024-01-04 = Thursday (DayOfWeek value 4) – upper boundary of isWeekday=true
    private static final Instant THURSDAY = Instant.parse("2024-01-04T12:00:00Z");
    // 2024-01-05 = Friday   (DayOfWeek value 5) – lower boundary of isWeekend=true
    private static final Instant FRIDAY   = Instant.parse("2024-01-05T12:00:00Z");
    // 2024-01-07 = Sunday   (DayOfWeek value 7)
    private static final Instant SUNDAY   = Instant.parse("2024-01-07T12:00:00Z");

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment(Instant start, Instant end) {
        Appointment a = new Appointment();
        a.setStartTime(start);
        a.setEndTime(end);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // durationHours
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – durationHours: pure arithmetic, no branches.
     *   D1  2-hour appointment
     *   D2  exactly 24-hour appointment (threshold used by rule comparisons)
     *
     * Total branches: 0  |  Tests: 2
     */
    @Nested
    class DurationHours {

        @Test
        void should_returnTwoHours_when_endIsTwoHoursAfterStart() {
            Appointment a = buildAppointment(MONDAY, MONDAY.plus(2, ChronoUnit.HOURS));

            assertThat(AppointmentTimeUtil.durationHours(a)).isEqualTo(2L);
        }

        @Test
        void should_return24Hours_when_endIsOneDayAfterStart() {
            Appointment a = buildAppointment(MONDAY, MONDAY.plus(24, ChronoUnit.HOURS));

            assertThat(AppointmentTimeUtil.durationHours(a)).isEqualTo(24L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isWeekday
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isWeekday (d.getValue() {@literal <=} 4):
     *   B1  Monday    (1) → true
     *   B2  Thursday  (4) → true  (upper boundary of true range)
     *   B3  Friday    (5) → false (lower boundary of false range)
     *   B4  Sunday    (7) → false
     *
     * Total branches: 2  |  Tests: 4
     */
    @Nested
    class IsWeekday {

        @Test
        void should_returnTrue_when_monday() {
            assertThat(AppointmentTimeUtil.isWeekday(MONDAY)).isTrue();
        }

        @Test
        void should_returnTrue_when_thursday() {
            assertThat(AppointmentTimeUtil.isWeekday(THURSDAY)).isTrue();
        }

        @Test
        void should_returnFalse_when_friday() {
            assertThat(AppointmentTimeUtil.isWeekday(FRIDAY)).isFalse();
        }

        @Test
        void should_returnFalse_when_sunday() {
            assertThat(AppointmentTimeUtil.isWeekday(SUNDAY)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isWeekend
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isWeekend (d.getValue() {@literal >=} 5):
     *   B1  Friday    (5) → true  (lower boundary of true range)
     *   B2  Sunday    (7) → true
     *   B3  Thursday  (4) → false (upper boundary of false range)
     *   B4  Monday    (1) → false
     *
     * Total branches: 2  |  Tests: 4
     */
    @Nested
    class IsWeekend {

        @Test
        void should_returnTrue_when_friday() {
            assertThat(AppointmentTimeUtil.isWeekend(FRIDAY)).isTrue();
        }

        @Test
        void should_returnTrue_when_sunday() {
            assertThat(AppointmentTimeUtil.isWeekend(SUNDAY)).isTrue();
        }

        @Test
        void should_returnFalse_when_thursday() {
            assertThat(AppointmentTimeUtil.isWeekend(THURSDAY)).isFalse();
        }

        @Test
        void should_returnFalse_when_monday() {
            assertThat(AppointmentTimeUtil.isWeekend(MONDAY)).isFalse();
        }
    }
}
