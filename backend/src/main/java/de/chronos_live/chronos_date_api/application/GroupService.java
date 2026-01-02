package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

@ApplicationScoped
@Transactional
public class GroupService {
    @Inject
    AuthorizationService authorizationService;
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

    public void onGroupCreated(@ObservesAsync GroupCreatedEvent groupCreatedEvent) {
        Log.info("Group was created, adding creator to it");
        GroupMember groupMember = new GroupMember();
        User user = new User();
        user.id = groupCreatedEvent.actingUserId();
        Group group = new Group();
        group.id = groupCreatedEvent.groupId();
        groupMember.setGroup(group);
        groupMember.setUser(user);
        groupMember.persist();
    }

    public void addGroupMember(Long actingUserId, Long groupId, Long targetUserId) {
        this.authorizationService.requireAddGroupMember(groupId, actingUserId, targetUserId);
        if(GroupMember.find("group.id = ?1 AND user.id = ?2", groupId, targetUserId).count() > 0) {
            throw new ValidationException("user", "This user is already a member of this group");
        }
        if(Group.findById(groupId) == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        GroupMember groupMember = new GroupMember();
        User user = new User();
        user.id = targetUserId;
        Group group = new Group();
        group.id = groupId;
        groupMember.setGroup(group);
        groupMember.setUser(user);
        groupMember.persist();
        this.groupMemberAddedEvent.fireAsync(new GroupMemberAddedEvent(actingUserId, targetUserId, actingUserId));
    }

    public void removeGroupMember(Long actingUserId, Long groupId, Long targetUserId) {
        this.authorizationService.requireRemoveGroupMember(groupId, actingUserId, targetUserId);
        GroupMember groupMember = (GroupMember) GroupMember.find("group.id = ?1 AND user.id = ?2", groupId, targetUserId)
                .firstResultOptional().orElseThrow(() -> new ValidationException("This user is not member of this group"));
        groupMember.delete();
        this.groupMemberRemovedEvent.fireAsync(new GroupMemberRemovedEvent(groupId, targetUserId, actingUserId));
    }

    public List<User> getGroupUsers(Long requestingUserId, Long groupId) {
        this.authorizationService.requireReadGroupMembers(groupId, requestingUserId);
        return GroupMember.list("SELECT gm.user FROM GroupMember gm WHERE gm.group.id = ?1", groupId);
    }

    public Group createGroup(Long actingUserId, GroupDto createGroupDto) {
        Group group = new Group();
        if (createGroupDto.getName() == null || createGroupDto.getName().isBlank()) {
            throw new ValidationException("Group name is required");
        }
        group.setGroupName(createGroupDto.getName());

        User user = new User();
        user.id = actingUserId;
        group.setOwner(user);
        group.persist();

        this.groupCreatedEvent.fireAsync(new GroupCreatedEvent(group.id, user.id));
        return group;
    }

    public Group editGroup(Long actingUserId, Long groupId, GroupDto groupDto) {
        this.authorizationService.requireEditGroup(groupId, actingUserId);
        if (groupDto.getName() == null || groupDto.getName().isBlank()) {
            throw new ValidationException("Group name is required");
        }
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        this.groupNameChangedEvent.fireAsync(new GroupNameChangedEvent(groupId, group.getGroupName(), groupDto.getName(), actingUserId));
        group.setGroupName(groupDto.getName());
        return group;
    }

    public void deleteGroup(Long actingUserId, Long groupId) {
        this.authorizationService.requireDeleteGroup(groupId, actingUserId);
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new ResourceNotFoundException("group", groupId);
        }
        Group.deleteById(group.id);
        this.groupDeletedEvent.fireAsync(new GroupDeletedEvent(groupId, actingUserId));
    }
}
