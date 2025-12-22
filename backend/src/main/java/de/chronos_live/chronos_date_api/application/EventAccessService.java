package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class EventAccessService {
    private final GroupService groupService;
    private final ContactService contactService;
    private final WebPushService webPushService;

    public EventAccessService(GroupService groupService, ContactService contactService, WebPushService webPushService) {
        this.groupService = groupService;
        this.contactService = contactService;
        this.webPushService = webPushService;
    }

    private Set<Long> getAccessibleEvents(User user) {
        Set<Long> userEventIds = EventUserAttendees
                .find("user.id = ?1", user.id)
                .stream()
                .map(eventUserAttendee ->
                        ((EventUserAttendees) eventUserAttendee).getEvent().id)
                .collect(Collectors.toSet());

        // 2) Gruppen laden, in denen der User Member ist
        List<Group> groups = this.groupService.getGroups(user);
        Set<Long> groupIds = groups.stream()
                .map(g -> g.id)
                .collect(Collectors.toSet());

        // 3) Alle Events, bei denen eine dieser Gruppen eingetragen ist
        Set<Long> groupEventIds = groupIds.isEmpty()
                ? Set.of()
                : EventGroupAttendees.find("group.id IN ?1", groupIds)
                .stream().map(o ->
                        ((EventGroupAttendees) o).getEvent().id)
                .collect(Collectors.toSet());

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
                              id IN (SELECT uer.event.id FROM EventUserAttendees uer WHERE uer.user.id = ?2)
                           OR id IN (SELECT ger.event.id FROM EventGroupAttendees ger
                                     WHERE ger.group.id IN
                                        (SELECT g.id FROM Group g WHERE ?3 MEMBER OF g.members))
                        )
                        """,
                eventId, user.id, user
        ).count();

        return count > 0;
    }

    public void assignUserToEvent(User user, Long eventId, Long attendeeId, EventAttendeeRole role, boolean force) {
        if (!force && !this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!force && !EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to add attendees");
        }

        EventUserAttendees userAttendees = (EventUserAttendees) EventUserAttendees.find("event.id = ?1 AND user.id = ?2", eventId, attendeeId)
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
                    if(!force && this.contactService.getContacts(user).stream().noneMatch(c -> Objects.equals(c.id, attendeeId))) {
                        throw new IllegalArgumentException("You can only add users who are in your contacts");
                    }
                    eventUserAttendees.setUser(attendee);
                    eventUserAttendees.persist();
                    this.webPushService.sendNewEventAttendeeNotification(event, user.getName(), "user",
                            eventUserAttendees.getRole().name(), this.getAttendees(eventId));
                    return eventUserAttendees;
                });
        userAttendees.setRole(role);
    }

    public void updateUserEventRole(User user, Long eventId, Long attendeeId, EventAttendeeRole role) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to add attendees");
        }

        if (!EventAttendeeRole.RESPONSIBLE.equals(role)) {
            boolean groupWithResponsibleRole = this.getGroupAttendees(eventId).stream()
                    .anyMatch(e -> EventAttendeeRole.RESPONSIBLE.equals(e.getRole()));
            long usersWithResponsibleRole = this.getUserAttendees(eventId)
                    .stream().filter(e -> EventAttendeeRole.RESPONSIBLE.equals(e.getRole()))
                    .count();
            if (!groupWithResponsibleRole && usersWithResponsibleRole < 2) {
                throw new IllegalArgumentException("You can't remove the last responsible person from an event");
            }
        }

        EventUserAttendees userAttendees = (EventUserAttendees) EventUserAttendees.find("event.id = ?1 AND user.id = ?2", eventId, attendeeId)
                .firstResultOptional().orElseThrow();
        userAttendees.setRole(role);
    }

    public void unassignUserToEvent(User user, Long eventId, Long attendeeId) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to remove attendees");
        }
        EventUserAttendees.delete("event.id AND user.id", eventId, attendeeId);
    }

    public void assignGroupToEvent(User user, Long eventId, Long groupId, EventAttendeeRole role) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to add attendees");
        }

        EventGroupAttendees groupAttendees = (EventGroupAttendees) EventGroupAttendees.find("event.id = ?1 AND group.id = ?2", eventId, groupId)
                .firstResultOptional().orElseGet(() -> {
                    EventGroupAttendees eventGroupAttendees = new EventGroupAttendees();
                    Event event = Event.findById(eventId);
                    if (event == null) {
                        throw new IllegalArgumentException("Event with id " + eventId + " does not exist");
                    }
                    eventGroupAttendees.setEvent(event);
                    Group group = Group.findById(groupId);
                    if (group == null) {
                        throw new IllegalArgumentException("Group with id " + groupId + " does not exist");
                    }
                    if(group.getMembers().stream().noneMatch( m -> Objects.equals(user.id, m.id))) {
                        throw new IllegalArgumentException("You can only add groups you're member in");
                    }
                    eventGroupAttendees.setGroup(group);
                    eventGroupAttendees.persist();
                    this.webPushService.sendNewEventAttendeeNotification(event, group.getGroupName(), "group",
                            eventGroupAttendees.getRole().name(), this.getAttendees(eventId));
                    return eventGroupAttendees;
                });
        groupAttendees.setRole(role);
    }

    public void unassignGroupToEvent(User user, Long eventId, Long groupId) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to remove attendees");
        }
        EventGroupAttendees.delete("event.id AND group.id", eventId, groupId);
    }

    public void updateGroupEventRole(User user, Long eventId, Long groupId, EventAttendeeRole role) {
        if (!this.userHasAccessToEvent(user, eventId)) {
            throw new IllegalArgumentException("User does not have access to event");
        }
        if(!EventAttendeeRole.RESPONSIBLE.equals(this.getEventRole(user, eventId))) {
            throw new IllegalArgumentException("You must be responsible for a event to add attendees");
        }

        if (!EventAttendeeRole.RESPONSIBLE.equals(role)) {
            boolean userWithResponsibleRole = this.getUserAttendees(eventId).stream()
                    .anyMatch(e -> EventAttendeeRole.RESPONSIBLE.equals(e.getRole()));
            long groupsWithResponsibleRole = this.getGroupAttendees(eventId)
                    .stream().filter(e -> EventAttendeeRole.RESPONSIBLE.equals(e.getRole()))
                    .count();
            if (!userWithResponsibleRole && groupsWithResponsibleRole < 2) {
                throw new IllegalArgumentException("You can't remove the last responsible person from an event");
            }
        }

        EventGroupAttendees groupAttendees = (EventGroupAttendees) EventGroupAttendees.find("event.id AND group.id", eventId, groupId)
                .firstResultOptional().orElseThrow();
        groupAttendees.setRole(role);
    }

    public EventAttendeeRole getEventRole(User user, Long eventId) {
        EventAttendeeRole role = EventAttendeeRole.NONE;

        EventUserAttendees eventUserAttendees = EventUserAttendees.find("event.id = ?1 AND user.id = ?2", eventId, user.id).firstResult();
        if (eventUserAttendees != null) {
            role = eventUserAttendees.getRole();
        }

        for (Group group : this.groupService.getGroups(user)) {
            EventGroupAttendees eventGroupAttendees =
                    EventGroupAttendees.find("event.id = ?1 AND group.id = ?2", eventId, group.id).firstResult();
            if (eventGroupAttendees == null) {
                continue;
            }
            if (eventGroupAttendees.getRole().ordinal() > role.ordinal()) {
                role = eventGroupAttendees.getRole();
            }
        }
        return role;
    }

    public Set<User> getAttendees(Long eventId) {
        Set<User> attendees = new HashSet<>();

        // User Attendees
        List<EventUserAttendees> userAttendees = EventUserAttendees.find("event.id = ?1", eventId).list();
        for (EventUserAttendees eventUserAttendees : userAttendees) {
            attendees.add(eventUserAttendees.getUser());
        }

        List<EventGroupAttendees> groupAttendees = EventGroupAttendees.find("event.id = ?1", eventId).list();
        for (EventGroupAttendees eventGroupAttendees : groupAttendees) {
            attendees.addAll(eventGroupAttendees.getGroup().getMembers());
        }

        return attendees;
    }

    public List<EventUserAttendees> getUserAttendees(Long eventId) {
        return EventUserAttendees.find("event.id", eventId).list();
    }

    public List<EventGroupAttendees> getGroupAttendees(Long eventId) {
        return EventGroupAttendees.find(
                "SELECT DISTINCT a FROM EventGroupAttendees a " +
                        "LEFT JOIN FETCH a.group g " +
                        "LEFT JOIN FETCH g.members " +
                        "WHERE a.event.id = ?1",
                eventId
        ).list();
    }
}
