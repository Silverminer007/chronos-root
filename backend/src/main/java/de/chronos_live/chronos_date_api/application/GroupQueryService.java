package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@Timed("service.groupQuery")
public class GroupQueryService {

    public List<Group> searchGroups(UserIdentity user, String searchQuery) {
        String sqlQuery = "SELECT g FROM GroupMember gm JOIN gm.group g";
        sqlQuery += " LEFT JOIN FETCH g.members";
        sqlQuery += " WHERE gm.userOidcId = ?1";
        if (searchQuery != null && !searchQuery.isEmpty()) {
            sqlQuery += " AND lower(g.groupName) LIKE lower(?2)";
            return Group.find(sqlQuery, user.oidcId(), "%" + searchQuery + "%").list();
        }
        return Group.find(sqlQuery, user.oidcId()).list();
    }

    public boolean isGroupMember(Long groupId, String userOidcId) {
        return GroupMember.find("group.id = ?1 AND userOidcId = ?2", groupId, userOidcId).count() > 0;
    }

    public boolean isGroupOwner(Long groupId, String userOidcId) {
        return Group.count("id = ?1 AND ownerOidcId = ?2", groupId, userOidcId) > 0;
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return GroupMember.find("group.id = ?1", groupId).list();
    }

    public Group findById(Long id) {
        return Group.findById(id);
    }
}
