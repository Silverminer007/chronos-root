package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminGroupService {
    public void addGroupMember(Long groupId, Long targetUserId) {
        if(GroupMember.find("group.id = ?1 AND user.id = ?2", groupId, targetUserId).count() > 0) {
            return;
        }
        if(Group.findById(groupId) == null) {
            return;
        }
        GroupMember groupMember = new GroupMember();
        User user = User.findById(targetUserId);
        Group group = Group.findById(groupId);
        groupMember.setGroup(group);
        groupMember.setUser(user);
        groupMember.persist();
    }
}
