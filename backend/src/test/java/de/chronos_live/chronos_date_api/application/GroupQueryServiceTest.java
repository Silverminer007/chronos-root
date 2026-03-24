package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.User;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GroupQueryService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@link PanacheMock} intercepts every
 * static Panache call on {@link Group} and {@link GroupMember}. No CDI
 * dependencies are injected by the service itself so no {@code @InjectMock}
 * declarations are needed.
 *
 * <p>All branches are testable and covered below.
 */
@QuarkusTest
class GroupQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long GROUP_ID  = 10L;
    private static final Long USER_ID   = 1L;
    private static final String SEARCH  = "alpha";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    GroupQueryService service;

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
        g.setOwner(buildUser(USER_ID));
        return g;
    }

    private static GroupMember buildGroupMember(Group group, User user) {
        GroupMember gm = new GroupMember();
        gm.setGroup(group);
        gm.setUser(user);
        return gm;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // searchGroups
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – searchGroups:
     *   B1  searchQuery == null → call Group.find with only user.id (no LIKE clause)
     *   B2  searchQuery.isEmpty() → call Group.find with only user.id
     *   B3  searchQuery non-empty → call Group.find with user.id AND the LIKE clause
     *
     * Total branches: 3  |  Tests: 3
     *
     * Note: the compound condition {@code searchQuery != null && !searchQuery.isEmpty()}
     * covers B1 (null) and B2 (empty) as the false branch.
     */
    @Nested
    class SearchGroups {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Group.class);
        }

        // B1 – null query
        @Test
        void should_findWithoutLikeClause_when_searchQueryIsNull() {
            Group group = buildGroup(GROUP_ID, "Alpha Team");
            User user = buildUser(USER_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Group> q = mock(PanacheQuery.class);
            when(Group.<Group>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(group));

            List<Group> result = service.searchGroups(user, null);

            assertThat(result).containsExactly(group);
            assertThat(sqlCaptor.getValue()).doesNotContain("LIKE");
        }

        // B2 – empty query
        @Test
        void should_findWithoutLikeClause_when_searchQueryIsEmpty() {
            Group group = buildGroup(GROUP_ID, "Beta Group");
            User user = buildUser(USER_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked") PanacheQuery<Group> q = mock(PanacheQuery.class);
            when(Group.<Group>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(group));

            List<Group> result = service.searchGroups(user, "");

            assertThat(result).containsExactly(group);
            assertThat(sqlCaptor.getValue()).doesNotContain("LIKE");
        }

        // B3 – non-empty query
        @Test
        void should_appendLikeClause_when_searchQueryIsNonEmpty() {
            Group group = buildGroup(GROUP_ID, "Alpha Team");
            User user = buildUser(USER_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            @SuppressWarnings("unchecked") PanacheQuery<Group> q = mock(PanacheQuery.class);
            when(Group.<Group>find(sqlCaptor.capture(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(group));

            List<Group> result = service.searchGroups(user, SEARCH);

            assertThat(result).containsExactly(group);
            assertThat(sqlCaptor.getValue()).contains("LIKE lower(?2)");

            PanacheMock.verify(Group.class).<Group>find(anyString(), paramsCaptor.capture());
            Object[] params = paramsCaptor.getValue();
            assertThat(params[0]).isEqualTo(USER_ID);
            assertThat(params[1].toString()).isEqualTo("%" + SEARCH + "%");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isGroupMember
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isGroupMember:
     *   B1  count > 0 → return true
     *   B2  count == 0 → return false
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class IsGroupMember {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(GroupMember.class);
        }

        // B1
        @Test
        void should_returnTrue_when_memberExists() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(1L);

            boolean result = service.isGroupMember(GROUP_ID, USER_ID);

            assertThat(result).isTrue();
        }

        // B2
        @Test
        void should_returnFalse_when_memberDoesNotExist() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> gmQuery = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(gmQuery);
            when(gmQuery.count()).thenReturn(0L);

            boolean result = service.isGroupMember(GROUP_ID, USER_ID);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isGroupOwner
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isGroupOwner:
     *   B1  count > 0 → return true
     *   B2  count == 0 → return false
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class IsGroupOwner {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Group.class);
        }

        // B1
        @Test
        void should_returnTrue_when_userIsOwner() {
            when(Group.count(anyString(), any(Object[].class))).thenReturn(1L);

            boolean result = service.isGroupOwner(GROUP_ID, USER_ID);

            assertThat(result).isTrue();
        }

        // B2
        @Test
        void should_returnFalse_when_userIsNotOwner() {
            when(Group.count(anyString(), any(Object[].class))).thenReturn(0L);

            boolean result = service.isGroupOwner(GROUP_ID, USER_ID);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getGroupMembers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getGroupMembers:
     *   No conditional branches. Direct Panache delegation.
     *
     * Total branches: 0  |  Tests: 2
     */
    @Nested
    class GetGroupMembers {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(GroupMember.class);
        }

        @Test
        void should_returnMembers_when_membersExist() {
            Group group = buildGroup(GROUP_ID, "Team");
            User user = buildUser(USER_ID);
            GroupMember member = buildGroupMember(group, user);

            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> q = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(member));

            List<GroupMember> result = service.getGroupMembers(GROUP_ID);

            assertThat(result).containsExactly(member);
        }

        @Test
        void should_returnEmptyList_when_noMembersFound() {
            @SuppressWarnings("unchecked") PanacheQuery<GroupMember> q = mock(PanacheQuery.class);
            when(GroupMember.<GroupMember>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            List<GroupMember> result = service.getGroupMembers(GROUP_ID);

            assertThat(result).isEmpty();
        }
    }
}
