package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.events.AppointmentCancelledEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentCreatedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentDeletedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentEditedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentMovedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
class TestEventObserver {
    private final List<AppointmentCreatedEvent> createdEvents = new ArrayList<>();
    private final List<AppointmentEditedEvent> editedEvents = new ArrayList<>();
    private final List<AppointmentMovedEvent> movedEvents = new ArrayList<>();
    private final List<AppointmentDeletedEvent> deletedEvents = new ArrayList<>();
    private final List<AppointmentCancelledEvent> cancelledEvents = new ArrayList<>();

    void onAppointmentCreated(@Observes AppointmentCreatedEvent event) {
        createdEvents.add(event);
    }

    void onAppointmentEdited(@Observes AppointmentEditedEvent event) {
        editedEvents.add(event);
    }

    void onAppointmentMoved(@Observes AppointmentMovedEvent event) {
        movedEvents.add(event);
    }

    void onAppointmentDeleted(@Observes AppointmentDeletedEvent event) {
        deletedEvents.add(event);
    }

    void onAppointmentCancelled(@Observes AppointmentCancelledEvent event) {
        cancelledEvents.add(event);
    }

    List<AppointmentCreatedEvent> getCreatedEvents() {
        return createdEvents;
    }

    List<AppointmentEditedEvent> getEditedEvents() {
        return editedEvents;
    }

    List<AppointmentMovedEvent> getMovedEvents() {
        return movedEvents;
    }

    List<AppointmentDeletedEvent> getDeletedEvents() {
        return deletedEvents;
    }

    List<AppointmentCancelledEvent> getCancelledEvents() {
        return cancelledEvents;
    }

    void reset() {
        createdEvents.clear();
        editedEvents.clear();
        movedEvents.clear();
        deletedEvents.clear();
        cancelledEvents.clear();
    }
}
