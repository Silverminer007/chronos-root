package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Timed("service.admin.group")
public class AdminGroupService {
    public void addGroupMember(Long groupId, String targetUserOidcId) {
        if (GroupMember.find("group.id = ?1 AND userOidcId = ?2", groupId, targetUserOidcId).count() > 0) {
            return;
        }
        if (Group.findById(groupId) == null) {
            return;
        }
        GroupMember groupMember = new GroupMember();
        Group group = Group.findById(groupId);
        groupMember.setGroup(group);
        groupMember.setUserOidcId(targetUserOidcId);
        groupMember.persist();
    }
}
