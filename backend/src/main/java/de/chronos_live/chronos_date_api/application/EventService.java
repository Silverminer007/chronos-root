package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.EventSeries;
import de.chronos_live.chronos_date_api.domain.EventStatus;
import de.chronos_live.chronos_date_api.domain.RepetitionRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
@Transactional
public class EventService {
    public void createEvent(Event event) {
        if (event.getName().isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        if (event.getEndTime() == null || event.getStartTime() == null) {
            throw new IllegalArgumentException("End or start cannot be null");
        }
        if (event.getEndTime().isBefore(event.getStartTime())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (event.getEventStatus() == null) {
            event.setEventStatus(EventStatus.PLANNED);
        }
        event.setCreatedAt(Instant.now());
        event.setLastUpdate(Instant.now());
        event.persist();
    }

    public void createRecurringEvent(Event event, RepetitionRule rrule) {
        this.createEvent(event);
        Event nextEvent = rrule.getNextEvent(event);
        while (nextEvent != null) {
            nextEvent.persist();
            nextEvent.setCreatedAt(Instant.now());
            nextEvent.setLastUpdate(Instant.now());

            EventSeries eventSeries = new EventSeries();
            eventSeries.setSeriesId(event.id);
            eventSeries.setEventId(nextEvent.id);
            eventSeries.persist();

            nextEvent = rrule.getNextEvent(nextEvent);
        }
    }

    public Event getEvent(long id) {
        Event event = Event.findById(id);
        if (event != null && event.getEventStatus() != EventStatus.DELETED) {
            return event;
        }
        return null;
    }

    public List<Event> getEventSeries(long id) {
        List<EventSeries> eventSeriesSeriesId = EventSeries.find("eventdId = ?", id).list();
        if (eventSeriesSeriesId.isEmpty()) {
            return List.of(getEvent(id));
        }

        long eventSeriesId = eventSeriesSeriesId.getFirst().getSeriesId();
        List<EventSeries> eventSeriesEvents = EventSeries.find("seriesId = ?", eventSeriesId).list();
        List<Long> eventIds = eventSeriesEvents.stream().map(EventSeries::getEventId).toList();

        List<Event> eventList = Event.find("id in [?]", eventIds).list();
        return eventList.stream()
                .filter(event -> event.getEventStatus() != EventStatus.DELETED)
                .toList();
    }

    public Event updateEvent(Event updatedEvent) {
        Event oldEvent = Event.findById(updatedEvent.id);

        if (updatedEvent.getName() != null) {
            if (updatedEvent.getName().isBlank()) {
                throw new IllegalArgumentException("Name cannot be blank");
            }
            oldEvent.setName(updatedEvent.getName());
        }
        if (updatedEvent.getDescription() != null) {
            oldEvent.setDescription(updatedEvent.getDescription());
        }
        if (updatedEvent.getVenue() != null) {
            oldEvent.setVenue(updatedEvent.getVenue());
        }
        // Es könnte sein, dass nur Start-Datum oder nur End-Datum aktualisiert werden.
        // Deshalb müssen auch beide einzeln validiert werden
        if (updatedEvent.getStartTime() != null && updatedEvent.getEndTime() != null) {
            if (updatedEvent.getEndTime().isBefore(updatedEvent.getStartTime())) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            oldEvent.setStartTime(updatedEvent.getStartTime());
            oldEvent.setEndTime(updatedEvent.getEndTime());
        }
        if (updatedEvent.getStartTime() != null && updatedEvent.getEndTime() == null) {
            if (oldEvent.getEndTime().isBefore(updatedEvent.getStartTime())) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            oldEvent.setStartTime(updatedEvent.getStartTime());
        }
        if (updatedEvent.getStartTime() == null && updatedEvent.getEndTime() != null) {
            if (updatedEvent.getEndTime().isBefore(oldEvent.getStartTime())) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            oldEvent.setEndTime(updatedEvent.getEndTime());
        }
        if (updatedEvent.getEventStatus() != null) {
            oldEvent.setEventStatus(updatedEvent.getEventStatus());
        }
        if (updatedEvent.getMinimalAttendees() != null) {
            oldEvent.setMinimalAttendees(updatedEvent.getMinimalAttendees());
        }
        updatedEvent.setLastUpdate(Instant.now());

        return oldEvent;
    }

    public void deleteEvent(Event event) {
        event.setEventStatus(EventStatus.DELETED);
        this.updateEvent(event);
    }

    public void cancelEvent(Long eventId) {
        Event event = Event.findById(eventId);
        if (event == null) {
            return;
        }
        event.setEventStatus(EventStatus.CANCELLED);
        this.updateEvent(event);
    }

    public List<Event> getEventsUpdatedAfter(LocalDateTime after) {
        List<Event> events = Event.find("lastUpdated AFTER ?", after).list();
        return events.stream()
                .filter(e -> e.getEventStatus() != EventStatus.DELETED)
                .toList();
    }

    public List<Long> getDeletedEvents(LocalDateTime deletedAfter) {
        List<Event> events = Event.find("lastUpdated AFTER ?", deletedAfter).list();
        return events.stream()
                .filter(e -> e.getEventStatus() == EventStatus.DELETED)
                .map(e -> e.id)
                .toList();
    }

    public List<Event> searchEvent(String query, LocalDateTime after, LocalDateTime before) {
        List<Event> events;
        if (query != null) {
            query = "%" + query + "%";

            events = Event.find(
                    "(lower(name) LIKE lower(?1) OR lower(description) LIKE lower(?1) OR lower(venue) LIKE lower(?1))" +
                            " AND endTime > ?2 AND startTime < ?3" +
                            "ORDER BY startTime",
                    query, after, before).list();
        } else {
            events = Event.find(
                    "endTime > ?1 AND startTime < ?2" +
                            "ORDER BY startTime", after, before).list();
        }

        return events.stream()
                .filter(e -> e.getEventStatus() != EventStatus.DELETED)
                .toList();
    }

    public List<Event> findEventsStartingAt(Instant at) {
        return this.findEventsStartingBetween(at.minusSeconds(30), at.plusSeconds(30));
    }

    public List<Event> findEventsStartingBetween(Instant after, Instant before) {
        List<Event> events = Event.find(
                "startTime < ?1 AND startTime > ?2", before, after).list();

        return events.stream()
                .filter(e -> e.getEventStatus() != EventStatus.DELETED)
                .toList();
    }

    public List<Event> searchEvent(String query, Instant after, Instant before, int page, int pageSize) {
        List<Event> events;
        if (query != null) {
            query = "%" + query + "%";
            query = query.toLowerCase();

            events = Event.find(
                            "(lower(name) LIKE lower(?1) OR lower(description) LIKE lower(?1) OR lower(venue) LIKE lower(?1))" +
                                    " AND endTime > ?2 AND startTime < ?3" +
                                    "ORDER BY startTime",
                            query, after, before)
                    .page(page, pageSize).list();
        } else {
            events = Event.find(
                            "endTime > ?1 AND startTime < ?2" +
                                    "ORDER BY startTime", after, before)
                    .page(page, pageSize).list();
        }

        return events.stream()
                .filter(e -> e.getEventStatus() != EventStatus.DELETED)
                .toList();
    }
}
