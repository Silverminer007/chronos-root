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
        Set<Long> userEventIds = new HashSet<>(UserEventRelation
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
                new HashSet<>(GroupEventRelation.find("group.id IN ?1", groupIds)
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
        long count = Event.find(
                """
                        id = ?1 AND (
                              id IN (SELECT uer.event.id FROM UserEventRelation uer WHERE uer.user.id = ?2)
                           OR id IN (SELECT ger.event.id FROM GroupEventRelation ger
                                     WHERE ger.group.id IN
                                        (SELECT g.id FROM Group g WHERE ?3 MEMBER OF g.members))
                        )
                        """,
                event.id, user.id, user
        ).count();

        return count > 0;
    }

    // TODO
    public void assignUserToEvent(User user, Event event, User participant) {

    }

    public void unassignUserToEvent(User user, Event event, User participant) {

    }

    public void assignGroupToEvent(User user, Event event, Group participant) {

    }

    public void unassignGroupToEvent(User user, Event event, Group participant) {

    }

    public UserEventRelationRole getEventRole(User user, Event event) {
        UserEventRelationRole role = UserEventRelationRole.NONE;

        UserEventRelation userEventRelation = UserEventRelation.find("event.id = ?1 AND user.id = ?2", event.id, user.id).firstResult();
        if (userEventRelation != null) {
            role = userEventRelation.getRole();
        }

        for (Group group : this.groupService.getGroups(user)) {
            GroupEventRelation groupEventRelation =
                    GroupEventRelation.find("event.id = ?1 AND group.id = ?2", event.id, group.id).firstResult();
            if (groupEventRelation == null) {
                continue;
            }
            if (groupEventRelation.getRole().ordinal() > role.ordinal()) {
                role = groupEventRelation.getRole();
            }
        }
        return role;
    }

    public UserEventRelationRole getUserEventRole(User user, Event event) {
        UserEventRelation userEventRelation = UserEventRelation.find("event.id = ?1 AND user.id = ?2", event.id, user.id).firstResult();
        if (userEventRelation != null) {
            return userEventRelation.getRole();
        }
        return UserEventRelationRole.NONE;
    }

    public UserEventRelationRole getGroupEventRole(Group group, Event event) {
        GroupEventRelation groupEventRelation =
                    GroupEventRelation.find("event.id = ?1 AND group.id = ?2", event.id, group.id).firstResult();
        if (groupEventRelation != null) {
            return groupEventRelation.getRole();
        }
        return UserEventRelationRole.NONE;
    }

    public Set<User> getAttendees(Event event) {
        Set<User> attendees = new HashSet<>();

        // User Attendees
        List<UserEventRelation> userAttendees = UserEventRelation.find("event", event).list();
        for (UserEventRelation userEventRelation : userAttendees) {
            attendees.add(userEventRelation.getUser());
        }

        List<GroupEventRelation> groupAttendees = GroupEventRelation.find("event", event).list();
        for (GroupEventRelation groupEventRelation : groupAttendees) {
            attendees.addAll(groupEventRelation.getGroup().getMembers());
        }

        return attendees;
    }
}
