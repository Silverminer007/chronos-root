package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReminderRuleEngine}.
 *
 * <p>Strategy: {@code @ExtendWith(MockitoExtension.class)}. The engine takes
 * constructor args so it is instantiated directly with mocked {@link ReminderRule}
 * and {@link ReminderEventPort}; no CDI container is needed.
 *
 * <p><b>Untestable branches:</b><br>
 * None. Every branch in {@code evaluate} and the private {@code isDue} helper
 * is reachable via the public API.
 */
@ExtendWith(MockitoExtension.class)
class ReminderRuleEngineTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Instant NOW                = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant TRIGGER_10_MIN_AGO = NOW.minus(10, ChronoUnit.MINUTES);
    private static final Instant TRIGGER_15_MIN_AGO = NOW.minus(15, ChronoUnit.MINUTES);
    private static final Instant TRIGGER_16_MIN_AGO = NOW.minus(16, ChronoUnit.MINUTES);
    private static final Instant TRIGGER_7_DAYS_AGO = NOW.minus(7, ChronoUnit.DAYS);
    private static final Instant TRIGGER_10_MIN_AHEAD = NOW.plus(10, ChronoUnit.MINUTES);
    private static final Instant TRIGGER_1_HOUR_AHEAD = NOW.plus(1, ChronoUnit.HOURS);

    // ── Mocks ─────────────────────────────────────────────────────────────────
    @Mock
    private ReminderRule rule;

    @Mock
    private ReminderEventPort eventPort;

    private ReminderRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReminderRuleEngine(List.of(rule), eventPort);
    }

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.id = 42L;
        a.setName("Test Appointment");
        a.setStartTime(NOW.plus(30, ChronoUnit.MINUTES));
        a.setEndTime(NOW.plus(2, ChronoUnit.HOURS));
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // evaluate
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – evaluate:
     *   B1  empty appointments list → for-loop never executes
     *   B2  no rule matches (rule == null) → continue
     *   B3  rule matches, trigger within past 15 min → execute + break
     *   B4  rule matches, trigger in the future → no execute
     *   B5  multiple appointments: one unmatched, one matched
     *   B6  multiple triggers: first not due (future), second due (past) → execute once
     *   B7  multiple triggers: none due → no execute
     *
     * Coverage plan – isDue (private, exercised through evaluate):
     *   IB1  trigger 0–15 min ago → true  (execute called)
     *   IB2  trigger in future → false (execute not called)
     *   IB3  trigger exactly 15 min ago → true  (boundary inclusive)
     *   IB4  trigger exactly 16 min ago → false (boundary exclusive)
     *   IB5  trigger 7 days ago → false  (regression: old triggers must not re-fire)
     *
     * Total branches: 7 + 5  |  Tests: 10
     */
    @Nested
    class Evaluate {

        // B1: empty list → loop body never runs
        @Test
        void should_doNothing_when_appointmentListIsEmpty() {
            engine.evaluate(List.of(), NOW);

            verifyNoInteractions(rule, eventPort);
        }

        // B2: appliesTo returns false for every rule → null → continue
        @Test
        void should_skipAppointment_when_noRuleApplies() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(false);

            engine.evaluate(List.of(appointment), NOW);

            verify(rule).appliesTo(appointment);
            verify(rule, never()).computeTriggerTimes(any());
            verifyNoInteractions(eventPort);
        }

        // B3 + IB1: rule matches, trigger within past 15 min → execute called, break
        @Test
        void should_executeRule_when_ruleAppliesAndTriggerIsDue() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment)).thenReturn(List.of(TRIGGER_10_MIN_AGO));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule).execute(appointment, eventPort);
        }

        // B4 + IB2: rule matches, trigger in the future → no execute
        @Test
        void should_notExecuteRule_when_ruleAppliesButTriggerIsNotDue() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment)).thenReturn(List.of(TRIGGER_1_HOUR_AHEAD));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule, never()).execute(any(), any());
        }

        // B5: two appointments; only the matching one triggers execute
        @Test
        void should_evaluateEachAppointmentIndependently_when_multipleAppointments() {
            Appointment unmatched = buildAppointment();
            Appointment matched = buildAppointment();
            matched.id = 99L;

            when(rule.appliesTo(unmatched)).thenReturn(false);
            when(rule.appliesTo(matched)).thenReturn(true);
            when(rule.computeTriggerTimes(matched)).thenReturn(List.of(TRIGGER_10_MIN_AGO));

            engine.evaluate(List.of(unmatched, matched), NOW);

            verify(rule, times(1)).execute(any(Appointment.class), eq(eventPort));
        }

        // B6: multiple triggers – first not due (future), second is due (past) → exactly 1 execute
        @Test
        void should_executeOnce_when_secondOfMultipleTriggersIsDue() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment))
                    .thenReturn(List.of(TRIGGER_1_HOUR_AHEAD, TRIGGER_10_MIN_AGO));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule, times(1)).execute(appointment, eventPort);
        }

        // B7: multiple triggers, none due
        @Test
        void should_notExecute_when_noTriggerIsDue() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment))
                    .thenReturn(List.of(TRIGGER_10_MIN_AHEAD, TRIGGER_1_HOUR_AHEAD));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule, never()).execute(any(), any());
        }

        // IB3: exactly 15 min ago → due (boundary inclusive)
        @Test
        void should_executeRule_when_triggerWasExactly15MinutesAgo() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment)).thenReturn(List.of(TRIGGER_15_MIN_AGO));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule).execute(appointment, eventPort);
        }

        // IB4: exactly 16 min ago → not due (boundary exclusive)
        @Test
        void should_notExecuteRule_when_triggerWas16MinutesAgo() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment)).thenReturn(List.of(TRIGGER_16_MIN_AGO));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule, never()).execute(any(), any());
        }

        // IB5: regression – trigger 7 days ago must not re-fire on every scheduler run
        @Test
        void should_notExecuteRule_when_triggerWas7DaysAgo() {
            Appointment appointment = buildAppointment();
            when(rule.appliesTo(appointment)).thenReturn(true);
            when(rule.computeTriggerTimes(appointment)).thenReturn(List.of(TRIGGER_7_DAYS_AGO));

            engine.evaluate(List.of(appointment), NOW);

            verify(rule, never()).execute(any(), any());
        }
    }
}
