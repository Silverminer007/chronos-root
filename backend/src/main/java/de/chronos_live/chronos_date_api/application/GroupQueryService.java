package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@Timed("service.groupQuery")
public class GroupQueryService {

    public List<Group> searchGroups(User user, String searchQuery) {
        String sqlQuery = "SELECT g FROM GroupMember gm JOIN gm.group g";
        sqlQuery += " LEFT JOIN FETCH g.members";
        sqlQuery += " WHERE gm.user.id = ?1";
        if (searchQuery != null && !searchQuery.isEmpty()) {
            sqlQuery += " AND lower(g.groupName) LIKE lower(?2)";
            return Group.find(sqlQuery, user.id, "%" + searchQuery + "%").list();
        }
        return Group.find(sqlQuery, user.id).list();
    }

    public boolean isGroupMember(Long groupId, Long userId) {
        return GroupMember.find("group.id = ?1 AND user.id = ?2", groupId, userId).count() > 0;
    }

    public boolean isGroupOwner(Long groupId, Long userId) {
        return Group.count("id = ?1 AND owner.id = ?2", groupId, userId) > 0;
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return GroupMember.find("group.id = ?1", groupId).list();
    }
}
