package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

@ApplicationScoped
@Transactional
public class GroupService {
    @Inject
    ContactService contactService;
    @Inject
    WebPushService webPushService;

    public List<Group> getGroups(User user) {
        return Group.find("?1 MEMBER OF members", user).list();
    }

    public List<Group> searchGroups(User user, String searchQuery) {
        searchQuery = "%" + searchQuery + "%";
        return Group.find("?1 MEMBER OF members AND lower(groupName) LIKE lower(?2)", user, searchQuery).list();
    }

    public void addGroupMember(User user, Long groupId, User newMember) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group not found");
        }
        if (!group.getOwner().equals(user)) {
            throw new IllegalArgumentException("User is not the owner of the group");
        }
        if(this.contactService.getContacts(user).stream().noneMatch(c -> Objects.equals(c.id, newMember.id))) {
            throw new IllegalArgumentException("You can only add users who are in your contacts");
        }
        group.getMembers().add(newMember);
        this.webPushService.sendNewGroupMemberNotification(group, newMember, group.getMembers());
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
