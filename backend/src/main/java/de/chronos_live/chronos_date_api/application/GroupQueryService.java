package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class GroupQueryService {

    public List<Group> searchGroups(User user, String searchQuery, boolean members) {
        String sqlQuery = "SELECT g FROM groups WHERE ?1 MEMBER OF members";
        if (searchQuery != null && !searchQuery.isEmpty()) {
            sqlQuery += " AND lower(groupName) LIKE lower(?2)";
        }
        if (members) {
            sqlQuery += " JOIN FETCH g.members";
        }
        return Group.find(sqlQuery, user, "%" + searchQuery + "%").list();
    }

    public boolean isGroupMember(Long groupId, Long userId) {
        return GroupMember.find("group.id = ?1 AND user.id = ?2", groupId, userId).count() > 0;
    }

    public boolean isGroupOwner(Long groupId, Long userId) {
        return Group
                .findByIdOptional(groupId)
                .map(g ->
                        Objects.equals(((Group) g).getOwner().id, userId))
                .orElse(false);
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return GroupMember.find("group.id = ?1", groupId).list();
    }
}
