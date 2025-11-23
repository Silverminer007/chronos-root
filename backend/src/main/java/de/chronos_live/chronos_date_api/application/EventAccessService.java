package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventAccessService {
    private final GroupService groupService;

    public EventAccessService(GroupService groupService) {
        this.groupService = groupService;
    }

    private Set<Long> getAccessibleEvents(User user) {
        Set<Long> userEventIds = new HashSet<>(EventUserAttendees
                .find("user.id", user.id)
                .project(Long.class)
                .list());

        // 2) Gruppen laden, in denen der User Member ist
        List<Group> groups = this.groupService.getGroups(user);
        Set<Long> groupIds = groups.stream()
                .map(g -> g.id)
                .collect(Collectors.toSet());

        // 3) Alle Events, bei denen eine dieser Gruppen eingetragen ist
        Set<Long> groupEventIds = groupIds.isEmpty() ? Set.of() :
                new HashSet<>(EventGroupAttendees.find("group.id IN ?1", groupIds)
                        .project(Long.class)
                        .list());

        userEventIds.addAll(groupEventIds);
        return userEventIds;
    }

    public List<Event> filterEvents(List<Event> events, User user) {
        Set<Long> eventIds = getAccessibleEvents(user);
        return events.stream()
                .filter(e -> eventIds.contains(e.id))
                .toList();
    }

    public boolean userHasAccessToEvent(User user, Event event) {
        return userHasAccessToEvent(user, event.id);
    }

    public boolean userHasAccessToEvent(User user, Long eventId) {
        long count = Event.find(
                """
                        id = ?1 AND (
                              id IN (SELECT uer.event.id FROM UserEventRelation uer WHERE uer.user.id = ?2)
                           OR id IN (SELECT ger.event.id FROM GroupEventRelation ger
                                     WHERE ger.group.id IN
                                        (SELECT g.id FROM Group g WHERE ?3 MEMBER OF g.members))
                        )
                        """,
                eventId, user.id, user
        ).count();

        return count > 0;
    }

    // TODO
    public void assignUserToEvent(User user, Long eventId, Long attendeeId, EventAttendeeRole role) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }

        EventUserAttendees userAttendees = (EventUserAttendees) EventUserAttendees.find("event.id AND user.id", eventId, attendeeId)
                .firstResultOptional().orElseGet(() -> {
                    EventUserAttendees eventUserAttendees = new EventUserAttendees();
                    Event event = Event.findById(eventId);
                    if (event == null) {
                        throw new IllegalArgumentException("Event with id " + eventId + " does not exist");
                    }
                    eventUserAttendees.setEvent(event);
                    User attendee = User.findById(attendeeId);
                    if (attendee == null) { // Muss der User auch in den Kontakten sein?
                        throw new IllegalArgumentException("User with id " + attendeeId + " does not exist");
                    }
                    eventUserAttendees.setUser(attendee);
                    eventUserAttendees.persist();
                    return eventUserAttendees;
                });
        userAttendees.setRole(role);
    }

    public void unassignUserToEvent(User user, Long eventId, Long attendeeId) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        EventUserAttendees.delete("event.id AND user.id", eventId, attendeeId);
    }

    public void assignGroupToEvent(User user, Long eventId, Long groupId, EventAttendeeRole role) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }

        EventGroupAttendees groupAttendees = (EventGroupAttendees) EventGroupAttendees.find("event.id AND group.id", eventId, groupId)
                .firstResultOptional().orElseGet(() -> {
                    EventGroupAttendees eventGroupAttendees = new EventGroupAttendees();
                    Event event = Event.findById(eventId);
                    if (event == null) {
                        throw new IllegalArgumentException("Event with id " + eventId + " does not exist");
                    }
                    eventGroupAttendees.setEvent(event);
                    Group group = Group.findById(groupId);
                    if (group == null) { // TODO Muss der User auch in den Kontakten sein?
                        throw new IllegalArgumentException("Group with id " + groupId + " does not exist");
                    }
                    eventGroupAttendees.setGroup(group);
                    eventGroupAttendees.persist();
                    return eventGroupAttendees;
                });
        groupAttendees.setRole(role);
    }

    public void unassignGroupToEvent(User user, Long eventId, Long groupId) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        EventGroupAttendees.delete("event.id AND group.id", eventId, groupId);
    }

    public EventAttendeeRole getEventRole(User user, Event event) {
        EventAttendeeRole role = EventAttendeeRole.NONE;

        EventUserAttendees eventUserAttendees = EventUserAttendees.find("event.id = ?1 AND user.id = ?2", event.id, user.id).firstResult();
        if (eventUserAttendees != null) {
            role = eventUserAttendees.getRole();
        }

        for (Group group : this.groupService.getGroups(user)) {
            EventGroupAttendees eventGroupAttendees =
                    EventGroupAttendees.find("event.id = ?1 AND group.id = ?2", event.id, group.id).firstResult();
            if (eventGroupAttendees == null) {
                continue;
            }
            if (eventGroupAttendees.getRole().ordinal() > role.ordinal()) {
                role = eventGroupAttendees.getRole();
            }
        }
        return role;
    }

    public EventAttendeeRole getUserEventRole(User user, Event event) {
        EventUserAttendees eventUserAttendees = EventUserAttendees.find("event.id = ?1 AND user.id = ?2", event.id, user.id).firstResult();
        if (eventUserAttendees != null) {
            return eventUserAttendees.getRole();
        }
        return EventAttendeeRole.NONE;
    }

    public EventAttendeeRole getGroupEventRole(Group group, Event event) {
        EventGroupAttendees eventGroupAttendees =
                EventGroupAttendees.find("event.id = ?1 AND group.id = ?2", event.id, group.id).firstResult();
        if (eventGroupAttendees != null) {
            return eventGroupAttendees.getRole();
        }
        return EventAttendeeRole.NONE;
    }

    public Set<User> getAttendees(Event event) {
        Set<User> attendees = new HashSet<>();

        // User Attendees
        List<EventUserAttendees> userAttendees = EventUserAttendees.find("event", event).list();
        for (EventUserAttendees eventUserAttendees : userAttendees) {
            attendees.add(eventUserAttendees.getUser());
        }

        List<EventGroupAttendees> groupAttendees = EventGroupAttendees.find("event", event).list();
        for (EventGroupAttendees eventGroupAttendees : groupAttendees) {
            attendees.addAll(eventGroupAttendees.getGroup().getMembers());
        }

        return attendees;
    }

    public List<EventUserAttendees> getUserAttendees(Long eventId) {
        return EventUserAttendees.find("event.id", eventId).list();
    }

    public List<EventGroupAttendees> getGroupAttendees(Long eventId) {
        return EventGroupAttendees.find("event.id", eventId).list();
    }
}
