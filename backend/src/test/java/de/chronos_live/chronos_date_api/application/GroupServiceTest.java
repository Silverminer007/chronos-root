package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.GroupCreatedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupDeletedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupMemberAddedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupMemberRemovedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupNameChangedEvent;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GroupService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} for CDI dependencies.
 * {@link PanacheMock} intercepts static calls on {@link Group}, {@link GroupMember},
 * and {@link User}.
 *
 * <p><b>Untestable branch – onGroupCreated:</b><br>
 * The {@code onGroupCreated} method is a CDI observer annotated with
 * {@code @Observes(during = TransactionPhase.AFTER_SUCCESS)}. It fires only
 * after a successful JTA transaction commits, which does not happen in unit
 * tests that never open a real transaction against a live database. This
 * observer is therefore not unit-tested here; it would need an integration test
 * with a real datasource to verify.
 */
@QuarkusTest
class GroupServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long ACTING_USER_ID  = 1L;
    private static final Long TARGET_USER_ID  = 2L;
    private static final Long GROUP_ID        = 10L;
    private static final String GROUP_NAME    = "Test Group";
    private static final String NEW_GROUP_NAME = "Renamed Group";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    GroupService service;

    @Inject
    AgroalDataSource dataSource;

    @InjectMock
    AuthorizationService authorizationService;

    @BeforeEach
    void insertGroupFixtures() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, firstname, lastname) VALUES (2, 'Target', 'User') ON CONFLICT DO NOTHING");
            stmt.execute("INSERT INTO groups (id, groupname) VALUES (10, 'Test Group') ON CONFLICT DO NOTHING");
        }
    }

    @AfterEach
    void cleanupGroupTestData() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM group_member WHERE group_id = 10 AND user_id = 2");
        }
    }

    @InjectMock
    Event<GroupMemberAddedEvent> groupMemberAddedEvent;

    @InjectMock
    Event<GroupMemberRemovedEvent> groupMemberRemovedEvent;

    @InjectMock
    Event<GroupCreatedEvent> groupCreatedEvent;

    @InjectMock
    Event<GroupDeletedEvent> groupDeletedEvent;

    @InjectMock
    Event<GroupNameChangedEvent> groupNameChangedEvent;

    // ── Test-object builders ──────────────────────────────────────────────────
    private static User buildUser(Long id) {
        User u = new User();
        u.id = id;
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }

    private static Group buildGroup(Long id, String name) {
        Group g = new Group();
        g.id = id;
        g.setGroupName(name);
        g.setOwner(buildUser(ACTING_USER_ID));
        return g;
    }

    private static GroupDto buildGroupDto(String name) {
        GroupDto dto = new GroupDto();
        dto.setName(name);
        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addGroupMember
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – addGroupMember:
     *   B1  GroupMember count > 0 → throw ValidationException ("already a member")
     *   B2  Group.findById == null → throw ResourceNotFoundException
     *   B3  happy path → persist member + fire event
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class AddGroupMember {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(GroupMember.class);
            PanacheMock.mock(Group.class);
            PanacheMock.mock(User.class);
        }

        // B1
        @Test
        void should_throwValidationException_when_userAlreadyMember() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addGroupMember(ACTING_USER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class);

            verify(authorizationService).requireAddGroupMember(GROUP_ID, ACTING_USER_ID, TARGET_USER_ID);
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_groupNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(0L);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.addGroupMember(ACTING_USER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(GROUP_ID.toString());
        }

        // B3
        @Test
        void should_persistMemberAndFireEvent_when_valid() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(0L);
            Group group = buildGroup(GROUP_ID, GROUP_NAME);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            User target = buildUser(TARGET_USER_ID);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(target);
            ArgumentCaptor<GroupMemberAddedEvent> captor =
                    ArgumentCaptor.forClass(GroupMemberAddedEvent.class);

            service.addGroupMember(ACTING_USER_ID, GROUP_ID, TARGET_USER_ID);

            verify(groupMemberAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().newMemberId()).isEqualTo(TARGET_USER_ID);
            assertThat(captor.getValue().actingUserId()).isEqualTo(ACTING_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeGroupMember
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – removeGroupMember:
     *   B1  GroupMember not found → throw ValidationException
     *   B2  happy path → delete member + fire event
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class RemoveGroupMember {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(GroupMember.class);
        }

        // B1
        @Test
        void should_throwValidationException_when_memberNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> q = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeGroupMember(ACTING_USER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not member");

            verify(authorizationService).requireRemoveGroupMember(GROUP_ID, ACTING_USER_ID, TARGET_USER_ID);
        }

        // B2
        @Test
        void should_deleteMemberAndFireEvent_when_memberFound() {
            GroupMember member = spy(new GroupMember());
            member.id = 99L;
            doNothing().when(member).delete();
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> q = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(member));
            ArgumentCaptor<GroupMemberRemovedEvent> captor =
                    ArgumentCaptor.forClass(GroupMemberRemovedEvent.class);

            service.removeGroupMember(ACTING_USER_ID, GROUP_ID, TARGET_USER_ID);

            verify(groupMemberRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().removedMemberId()).isEqualTo(TARGET_USER_ID);
            assertThat(captor.getValue().actingUserId()).isEqualTo(ACTING_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getGroupUsers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getGroupUsers:
     *   No conditional branches. Authorization + Panache delegation.
     * <p>
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetGroupUsers {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(GroupMember.class);
        }

        @Test
        void should_returnUsers_when_authorized() {
            User user = buildUser(TARGET_USER_ID);
            when(GroupMember.list(anyString(), any(Object[].class))).thenReturn(List.of(user));

            List<User> result = service.getGroupUsers(ACTING_USER_ID, GROUP_ID);

            assertThat(result).containsExactly(user);
            verify(authorizationService).requireReadGroupMembers(GROUP_ID, ACTING_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createGroup
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – createGroup:
     *   B1  name == null → throw ValidationException
     *   B2  name.isBlank() → throw ValidationException
     *   B3  happy path → persist group + fire event
     * <p>
     * Total branches: 3  |  Tests: 3
     * <p>
     * Note: B1 and B2 are distinct conditions joined by {@code ||} in the source.
     */
    @Nested
    class CreateGroup {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Group.class);
            PanacheMock.mock(User.class);
        }

        // B1
        @Test
        void should_throwValidationException_when_nameIsNull() {
            assertThatThrownBy(() -> service.createGroup(ACTING_USER_ID, buildGroupDto(null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B2
        @Test
        void should_throwValidationException_when_nameIsBlank() {
            assertThatThrownBy(() -> service.createGroup(ACTING_USER_ID, buildGroupDto("   ")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B3
        @Test
        void should_persistGroupAndFireEvent_when_nameIsValid() {
            User owner = buildUser(ACTING_USER_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(owner);
            ArgumentCaptor<GroupCreatedEvent> captor =
                    ArgumentCaptor.forClass(GroupCreatedEvent.class);

            Group result = service.createGroup(ACTING_USER_ID, buildGroupDto(GROUP_NAME));

            assertThat(result.getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(result.getOwner()).isEqualTo(owner);
            verify(groupCreatedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserId()).isEqualTo(ACTING_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // editGroup
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – editGroup:
     *   B1  name == null → throw ValidationException
     *   B2  name.isBlank() → throw ValidationException
     *   B3  group not found (null) → throw ResourceNotFoundException
     *   B4  happy path → fire name-changed event + set new name
     * <p>
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class EditGroup {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Group.class);
        }

        // B1
        @Test
        void should_throwValidationException_when_nameIsNull() {
            assertThatThrownBy(() -> service.editGroup(ACTING_USER_ID, GROUP_ID, buildGroupDto(null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");

            verify(authorizationService).requireEditGroup(GROUP_ID, ACTING_USER_ID);
        }

        // B2
        @Test
        void should_throwValidationException_when_nameIsBlank() {
            assertThatThrownBy(() -> service.editGroup(ACTING_USER_ID, GROUP_ID, buildGroupDto("  ")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B3
        @Test
        void should_throwResourceNotFoundException_when_groupNotFound() {
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.editGroup(ACTING_USER_ID, GROUP_ID, buildGroupDto(NEW_GROUP_NAME)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(GROUP_ID.toString());
        }

        // B4
        @Test
        void should_fireNameChangedEventAndUpdateName_when_valid() {
            Group group = buildGroup(GROUP_ID, GROUP_NAME);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            ArgumentCaptor<GroupNameChangedEvent> captor =
                    ArgumentCaptor.forClass(GroupNameChangedEvent.class);

            Group result = service.editGroup(ACTING_USER_ID, GROUP_ID, buildGroupDto(NEW_GROUP_NAME));

            assertThat(result.getGroupName()).isEqualTo(NEW_GROUP_NAME);
            verify(groupNameChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().oldName()).isEqualTo(GROUP_NAME);
            assertThat(captor.getValue().newName()).isEqualTo(NEW_GROUP_NAME);
            assertThat(captor.getValue().actingUserId()).isEqualTo(ACTING_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteGroup
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – deleteGroup:
     *   B1  group not found (null) → throw ResourceNotFoundException
     *   B2  happy path → deleteById + fire event
     * <p>
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class DeleteGroup {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Group.class);
        }

        // B1
        @Test
        void should_throwResourceNotFoundException_when_groupNotFound() {
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.deleteGroup(ACTING_USER_ID, GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(GROUP_ID.toString());

            verify(authorizationService).requireDeleteGroup(GROUP_ID, ACTING_USER_ID);
        }

        // B2
        @Test
        void should_deleteGroupAndFireEvent_when_groupFound() {
            Group group = buildGroup(GROUP_ID, GROUP_NAME);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(Group.deleteById(GROUP_ID)).thenReturn(true);
            ArgumentCaptor<GroupDeletedEvent> captor =
                    ArgumentCaptor.forClass(GroupDeletedEvent.class);

            service.deleteGroup(ACTING_USER_ID, GROUP_ID);

            PanacheMock.verify(Group.class).deleteById(GROUP_ID);
            verify(groupDeletedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().actingUserId()).isEqualTo(ACTING_USER_ID);
        }
    }
}
