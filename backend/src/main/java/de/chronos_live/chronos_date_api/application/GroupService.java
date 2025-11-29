package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.NotificationCategory;
import de.chronos_live.chronos_date_api.domain.User;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Transactional
public class GroupService {
    private final NotificationService notificationService;

    public GroupService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<Group> getGroups(User user) {
        return Group.find("?1 MEMBER OF members", user).list();
    }

    public void addGroupMember(User user, Long groupId, User newMember) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!group.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        group.getMembers().add(newMember);
        this.notificationService.notify(newMember,
                String.format("You were added to \"%s\"", group.getGroupName()),
                String.format("%s added you to the Group \"%s\". You can now see related events", user.getName(), group.getGroupName()),
                NotificationCategory.GROUP_MEMBERSHIP);
    }

    public void removeGroupMember(User user, Long groupId, User oldMember) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!group.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        group.getMembers().remove(oldMember);
    }

    public Set<User> getGroupUsers(User user, Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (group.getMembers() == null || !group.getMembers().stream().map(m -> m.id).toList().contains(user.id)) {
            throw new IllegalArgumentException("User is not a member of the group");
        }
        return group.getMembers();
    }

    public void createGroup(User user, Group group) {
        if (group.getGroupName() == null || group.getGroupName().isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (group.getMembers() == null) {
            group.setMembers(new HashSet<>());
        }
        group.getMembers().add(user);

        group.setOwner(user);
        group.persist();
    }

    public void editGroupName(User user, Long groupId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        Group g = Group.findById(groupId);
        if (g == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!g.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        g.setGroupName(newName);
    }

    public void deleteGroup(User user, Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!group.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        Group.deleteById(group.id);
    }
}
