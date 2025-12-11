package de.chronos_live.chronos_date_api.domain;

import java.time.Instant;

public interface RepetitionRule {
    Event getNextEvent(Event event);

    class Day implements RepetitionRule {
        private final int days;
        private final Instant end;

        public Day(int days, Instant end) {
            this.days = days;
            this.end = end;
        }

        @Override
        public Event getNextEvent(Event event) {
            Event nextEvent = new Event(event);
            nextEvent.setStartTime(nextEvent.getStartTime().plusSeconds(60L * 60 * 24 * days));
            nextEvent.setEndTime(nextEvent.getEndTime().plusSeconds(60L * 60 * 24 * days));
            if(nextEvent.getEndTime().isAfter(end)) {
                return null;
            }
            return nextEvent;
        }
    }
}