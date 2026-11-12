package de.chronos_live.chronos_date_api.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link MessageService#movedText}; no container required.
 */
class MessageServiceMovedTextTest {

    private static final Instant START = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant END = Instant.parse("2024-06-01T12:00:00Z");

    private static Optional<String> text(long startMinutes, long endMinutes) {
        return MessageService.movedText("Max", START, START.plusSeconds(startMinutes * 60),
                END, END.plusSeconds(endMinutes * 60));
    }

    @Test
    void should_sayOneHourLater_when_startPlus60() {
        assertThat(text(60, 0)).contains("Max hat diesen Termin um 1 Stunde nach hinten verschoben");
    }

    @Test
    void should_sayOneHourEarlier_when_startMinus60() {
        assertThat(text(-60, 0)).contains("Max hat diesen Termin um 1 Stunde vorverlegt");
    }

    @Test
    void should_sayMinutes_when_startPlus30() {
        assertThat(text(30, 0)).contains("Max hat diesen Termin um 30 Minuten nach hinten verschoben");
    }

    @Test
    void should_sayMinutes_when_startMinus30() {
        assertThat(text(-30, 0)).contains("Max hat diesen Termin um 30 Minuten vorverlegt");
    }

    @Test
    void should_sayHoursAndMinutes_when_startPlus90() {
        assertThat(text(90, 0)).contains("Max hat diesen Termin um 1 Stunde 30 Minuten nach hinten verschoben");
    }

    @Test
    void should_sayMinutes_when_startPlus59() {
        assertThat(text(59, 0)).contains("Max hat diesen Termin um 59 Minuten nach hinten verschoben");
    }

    @Test
    void should_useSingularMinute_when_startPlus1() {
        assertThat(text(1, 0)).contains("Max hat diesen Termin um 1 Minute nach hinten verschoben");
    }

    @Test
    void should_usePluralHours_when_startMinus121() {
        assertThat(text(-121, 0)).contains("Max hat diesen Termin um 2 Stunden 1 Minute vorverlegt");
    }

    @Test
    void should_sayEndLater_when_onlyEndPlus2h() {
        assertThat(text(0, 120)).contains("Max hat das Ende dieses Termins um 2 Stunden nach hinten verschoben");
    }

    @Test
    void should_sayEndEarlier_when_onlyEndMinus2h() {
        assertThat(text(0, -120)).contains("Max hat das Ende dieses Termins um 2 Stunden vorverlegt");
    }

    @Test
    void should_sayEndMinutes_when_onlyEndMinus30() {
        assertThat(text(0, -30)).contains("Max hat das Ende dieses Termins um 30 Minuten vorverlegt");
    }

    @Test
    void should_returnEmpty_when_nothingChanged() {
        assertThat(text(0, 0)).isEmpty();
    }

    @Test
    void should_letStartDecide_when_startAndEndMoved() {
        assertThat(text(-30, 120)).contains("Max hat diesen Termin um 30 Minuten vorverlegt");
    }
}
