package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import io.micrometer.core.annotation.Timed;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
@Timed("service.group")
public class GroupService {
    private static final Logger LOGGER = Logger.getLogger(GroupService.class);
    @Inject
    AuthorizationService authorizationService;
    @Inject
    IdentityPort identityPort;
    @Inject
    GroupRepository groupRepository;
    @Inject
    Event<GroupMemberAddedEvent> groupMemberAddedEvent;
    @Inject
    Event<GroupMemberRemovedEvent> groupMemberRemovedEvent;
    @Inject
    Event<GroupCreatedEvent> groupCreatedEvent;
    @Inject
    Event<GroupDeletedEvent> groupDeletedEvent;
    @Inject
    Event<GroupNameChangedEvent> groupNameChangedEvent;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onGroupCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) GroupCreatedEvent event) {
        Log.info("Group was created, adding creator to it");
        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(groupRepository.findById(event.groupId()));
        groupMember.setUserOidcId(event.actingUserOidcId());
        groupRepository.persistMember(groupMember);
    }

    public void addGroupMember(String actingUserOidcId, Long groupId, String targetUserOidcId) {
        LOGGER.debugf("[Principal %s][Group %s][User %s] Adding Member", actingUserOidcId, groupId, targetUserOidcId);

        authorizationService.requireAddGroupMember(groupId, actingUserOidcId, targetUserOidcId);
        if (groupRepository.isMember(groupId, targetUserOidcId)) {
            throw new ValidationException("user", "This user is already a member of this group");
        }
        if (groupRepository.findById(groupId) == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(groupRepository.findById(groupId));
        groupMember.setUserOidcId(targetUserOidcId);
        groupRepository.persistMember(groupMember);
        groupMemberAddedEvent.fire(new GroupMemberAddedEvent(groupId, targetUserOidcId, actingUserOidcId));
    }

    public void removeGroupMember(String actingUserOidcId, Long groupId, String targetUserOidcId) {
        LOGGER.debugf("[Principal %s][Group %s][User %s] Removing Member", actingUserOidcId, groupId, targetUserOidcId);

        authorizationService.requireRemoveGroupMember(groupId, actingUserOidcId, targetUserOidcId);
        GroupMember groupMember = groupRepository.findMember(groupId, targetUserOidcId)
                .orElseThrow(() -> new ValidationException("This user is not member of this group"));
        groupRepository.deleteMember(groupMember);
        groupMemberRemovedEvent.fire(new GroupMemberRemovedEvent(groupId, targetUserOidcId, actingUserOidcId));
    }

    public List<UserIdentity> getGroupUsers(String requestingUserOidcId, Long groupId) {
        LOGGER.debugf("[Principal %s][Group %s] Reading Group Members", requestingUserOidcId, groupId);
        authorizationService.requireReadGroupMembers(groupId, requestingUserOidcId);
        List<GroupMember> members = groupRepository.listMembers(groupId);
        Map<String, UserIdentity> userMap = identityPort.findByIds(
                members.stream().map(GroupMember::getUserOidcId).toList()
        );
        return members.stream()
                .map(m -> userMap.getOrDefault(m.getUserOidcId(),
                        new UserIdentity(m.getUserOidcId(), null, null, null, null)))
                .toList();
    }

    public Group createGroup(String actingUserOidcId, GroupDto createGroupDto) {
        if (createGroupDto.getName() == null || createGroupDto.getName().isBlank()) {
            throw new ValidationException("Group name is required");
        }
        LOGGER.debugf("[Principal %s][Group %s] Creating Group", actingUserOidcId, createGroupDto.getName());

        Group group = new Group();
        group.setGroupName(createGroupDto.getName());
        group.setOwnerOidcId(actingUserOidcId);
        groupRepository.persist(group);

        groupCreatedEvent.fire(new GroupCreatedEvent(group.id, actingUserOidcId));
        return group;
    }

    public Group editGroup(String actingUserOidcId, Long groupId, GroupDto groupDto) {
        LOGGER.debugf("[Principal %s][Group %s] Edit Group", actingUserOidcId, groupId);

        authorizationService.requireEditGroup(groupId, actingUserOidcId);
        if (groupDto.getName() == null || groupDto.getName().isBlank()) {
            throw new ValidationException("Group name is required");
        }
        Group group = groupRepository.findById(groupId);
        if (group == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        groupNameChangedEvent.fire(new GroupNameChangedEvent(groupId, group.getGroupName(), groupDto.getName(), actingUserOidcId));
        group.setGroupName(groupDto.getName());
        return group;
    }

    public void deleteGroup(String actingUserOidcId, Long groupId) {
        LOGGER.debugf("[Principal %s][Group %s] Deleting Group", actingUserOidcId, groupId);

        authorizationService.requireDeleteGroup(groupId, actingUserOidcId);
        Group group = groupRepository.findById(groupId);
        if (group == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        groupRepository.deleteById(group.id);
        groupDeletedEvent.fire(new GroupDeletedEvent(groupId, actingUserOidcId));
    }
}
