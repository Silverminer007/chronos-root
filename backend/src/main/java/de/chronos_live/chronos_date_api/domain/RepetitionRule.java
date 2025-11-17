package de.chronos_live.chronos_date_api.domain;

import java.time.LocalDate;

public interface RepetitionRule {
    Event getNextEvent(Event event);

    class Day implements RepetitionRule {
        private final int days;
        private final LocalDate end;

        public Day(int days, LocalDate end) {
            this.days = days;
            this.end = end;
        }

        @Override
        public Event getNextEvent(Event event) {
            Event nextEvent = new Event(event);
            nextEvent.setStart(nextEvent.getStart().plusDays(days));
            nextEvent.setEnd(nextEvent.getEnd().plusDays(days));
            if(nextEvent.getEnd().toLocalDate().isAfter(end)) {
                return null;
            }
            return nextEvent;
        }
    }
}