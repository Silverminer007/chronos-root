package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Timed("service.groupQuery")
public class GroupQueryService {

    @Inject
    GroupRepository groupRepository;

    public List<Group> searchGroups(UserIdentity user, String searchQuery) {
        return groupRepository.searchGroups(user, searchQuery);
    }

    public boolean isGroupMember(Long groupId, String userOidcId) {
        return groupRepository.isMember(groupId, userOidcId);
    }

    public boolean isGroupOwner(Long groupId, String userOidcId) {
        return groupRepository.isOwner(groupId, userOidcId);
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return groupRepository.listMembers(groupId);
    }

    public Group findById(Long id) {
        return groupRepository.findById(id);
    }
}
