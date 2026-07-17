package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
@Timed("service.admin.group")
public class AdminGroupService {
    @Inject
    GroupRepository groupRepository;

    public void addGroupMember(Long groupId, String targetUserOidcId) {
        if (groupRepository.isMember(groupId, targetUserOidcId)) {
            return;
        }
        Group group = groupRepository.findById(groupId);
        if (group == null) {
            return;
        }
        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(group);
        groupMember.setUserOidcId(targetUserOidcId);
        groupRepository.persistMember(groupMember);
    }
}
