package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.User;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GroupService {
    private final NotificationService notificationService;

    public GroupService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<Group> getGroups(User user) {
        return Group.find("?1 MEMBER OF members", user).list();
    }

    public void addGroupMember(User user, Group group, User newMember) {
        Group g = Group.findById(group.id);
        if (g == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!g.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        g.getMembers().add(newMember);
        this.notificationService.notify(newMember,
                String.format("You were added to \"%s\"", group.getGroupName()),
                String.format("%s added you to the Group \"%s\". You can now see related events", user.getName(), group.getGroupName()));
    }

    public void removeGroupMember(User user, Group group, User newMember) {
        Group g = Group.findById(group.id);
        if (g == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!g.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        g.getMembers().remove(newMember);
    }

    public void createGroup(User user, Group group) {
        if (group.getGroupName() == null || group.getGroupName().isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().add(user);

        group.setOwner(user);
        group.persist();
    }

    public void editGroupName(User user, Group group, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        Group g = Group.findById(group.id);
        if (g == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!g.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        g.setGroupName(newName);
    }

    public void deleteGroup(User user, Group group) {
        if (!group.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        Group.deleteById(group.id);
    }
}
