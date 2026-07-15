package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.GroupCreatedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupDeletedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupMemberAddedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupMemberRemovedEvent;
import de.chronos_live.chronos_date_api.application.events.GroupNameChangedEvent;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
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
 * {@link PanacheMock} intercepts static calls on {@link Group} and {@link GroupMember}.
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
    private static final String ACTING_USER_OIDC_ID  = "oidc-acting-1";
    private static final String TARGET_USER_OIDC_ID  = "oidc-target-2";
    private static final Long   GROUP_ID             = 10L;
    private static final String GROUP_NAME           = "Test Group";
    private static final String NEW_GROUP_NAME       = "Renamed Group";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    GroupService service;

    @Inject
    AgroalDataSource dataSource;

    @InjectMock
    AuthorizationService authorizationService;

    @InjectMock
    IdentityPort identityPort;

    @BeforeEach
    void insertGroupFixtures() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO groups (id, groupname, owner_oidcid) VALUES (10, 'Test Group', 'oidc-acting-1') ON CONFLICT DO NOTHING");
        }
    }

    @AfterEach
    void cleanupGroupTestData() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM group_member WHERE group_id = 10 AND user_oidcid = 'oidc-target-2'");
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
    private static UserIdentity buildUserIdentity(String oidcId) {
        return new UserIdentity(oidcId, "Test", "User", "test@example.com", null);
    }

    private static Group buildGroup(Long id, String name) {
        Group g = new Group();
        g.id = id;
        g.setGroupName(name);
        g.setOwnerOidcId(ACTING_USER_OIDC_ID);
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
        }

        // B1
        @Test
        void should_throwValidationException_when_userAlreadyMember() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addGroupMember(ACTING_USER_OIDC_ID, GROUP_ID, TARGET_USER_OIDC_ID))
                    .isInstanceOf(ValidationException.class);

            verify(authorizationService).requireAddGroupMember(GROUP_ID, ACTING_USER_OIDC_ID, TARGET_USER_OIDC_ID);
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_groupNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(0L);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.addGroupMember(ACTING_USER_OIDC_ID, GROUP_ID, TARGET_USER_OIDC_ID))
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
            ArgumentCaptor<GroupMemberAddedEvent> captor =
                    ArgumentCaptor.forClass(GroupMemberAddedEvent.class);

            service.addGroupMember(ACTING_USER_OIDC_ID, GROUP_ID, TARGET_USER_OIDC_ID);

            verify(groupMemberAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().newMemberOidcId()).isEqualTo(TARGET_USER_OIDC_ID);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
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

            assertThatThrownBy(() -> service.removeGroupMember(ACTING_USER_OIDC_ID, GROUP_ID, TARGET_USER_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not member");

            verify(authorizationService).requireRemoveGroupMember(GROUP_ID, ACTING_USER_OIDC_ID, TARGET_USER_OIDC_ID);
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

            service.removeGroupMember(ACTING_USER_OIDC_ID, GROUP_ID, TARGET_USER_OIDC_ID);

            verify(groupMemberRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().removedMemberOidcId()).isEqualTo(TARGET_USER_OIDC_ID);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
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
        void should_returnUserIdentities_when_authorized() {
            GroupMember member = new GroupMember();
            member.setUserOidcId(TARGET_USER_OIDC_ID);
            when(GroupMember.list(anyString(), any(Object[].class))).thenReturn(List.of(member));

            UserIdentity userIdentity = buildUserIdentity(TARGET_USER_OIDC_ID);
            when(identityPort.findByIds(anyList())).thenReturn(
                    java.util.Map.of(TARGET_USER_OIDC_ID, userIdentity));

            List<UserIdentity> result = service.getGroupUsers(ACTING_USER_OIDC_ID, GROUP_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).oidcId()).isEqualTo(TARGET_USER_OIDC_ID);
            verify(authorizationService).requireReadGroupMembers(GROUP_ID, ACTING_USER_OIDC_ID);
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
        }

        // B1
        @Test
        void should_throwValidationException_when_nameIsNull() {
            assertThatThrownBy(() -> service.createGroup(ACTING_USER_OIDC_ID, buildGroupDto(null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B2
        @Test
        void should_throwValidationException_when_nameIsBlank() {
            assertThatThrownBy(() -> service.createGroup(ACTING_USER_OIDC_ID, buildGroupDto("   ")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B3
        @Test
        void should_persistGroupAndFireEvent_when_nameIsValid() {
            ArgumentCaptor<GroupCreatedEvent> captor =
                    ArgumentCaptor.forClass(GroupCreatedEvent.class);

            Group result = service.createGroup(ACTING_USER_OIDC_ID, buildGroupDto(GROUP_NAME));

            assertThat(result.getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(result.getOwnerOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
            verify(groupCreatedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
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
            assertThatThrownBy(() -> service.editGroup(ACTING_USER_OIDC_ID, GROUP_ID, buildGroupDto(null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");

            verify(authorizationService).requireEditGroup(GROUP_ID, ACTING_USER_OIDC_ID);
        }

        // B2
        @Test
        void should_throwValidationException_when_nameIsBlank() {
            assertThatThrownBy(() -> service.editGroup(ACTING_USER_OIDC_ID, GROUP_ID, buildGroupDto("  ")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B3
        @Test
        void should_throwResourceNotFoundException_when_groupNotFound() {
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.editGroup(ACTING_USER_OIDC_ID, GROUP_ID, buildGroupDto(NEW_GROUP_NAME)))
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

            Group result = service.editGroup(ACTING_USER_OIDC_ID, GROUP_ID, buildGroupDto(NEW_GROUP_NAME));

            assertThat(result.getGroupName()).isEqualTo(NEW_GROUP_NAME);
            verify(groupNameChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().oldName()).isEqualTo(GROUP_NAME);
            assertThat(captor.getValue().newName()).isEqualTo(NEW_GROUP_NAME);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
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

            assertThatThrownBy(() -> service.deleteGroup(ACTING_USER_OIDC_ID, GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(GROUP_ID.toString());

            verify(authorizationService).requireDeleteGroup(GROUP_ID, ACTING_USER_OIDC_ID);
        }

        // B2
        @Test
        void should_deleteGroupAndFireEvent_when_groupFound() {
            Group group = buildGroup(GROUP_ID, GROUP_NAME);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(Group.deleteById(GROUP_ID)).thenReturn(true);
            ArgumentCaptor<GroupDeletedEvent> captor =
                    ArgumentCaptor.forClass(GroupDeletedEvent.class);

            service.deleteGroup(ACTING_USER_OIDC_ID, GROUP_ID);

            PanacheMock.verify(Group.class).deleteById(GROUP_ID);
            verify(groupDeletedEvent).fire(captor.capture());
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
        }
    }
}
