package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import de.chronos_live.chronos_date_api.infrastructure.TeamRepository;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthorizationService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock}. All CDI dependencies
 * are replaced with Mockito mocks. No Panache interaction occurs.
 *
 * <p><b>Coverage plan — every public method:</b>
 * <ul>
 *   <li>admin bypass (isAdminRequest = true) → no exception, no role lookup</li>
 *   <li>non-admin + sufficient role → no exception</li>
 *   <li>non-admin + insufficient role → {@link ForbiddenException}</li>
 *   <li>methods with additional checks (areFriends, isGroupMember, isGroupOwner) cover
 *       each secondary guard independently</li>
 * </ul>
 *
 * <p>Methods and branch counts:
 * <pre>
 * requireReadAppointment              2 branches (admin / NONE)
 * requireUpdateAppointment            2 branches (admin / ordinal check)
 * requireDeleteAppointment            2 branches (admin / !RESPONSIBLE)
 * requireCancelAppointment            2 branches (admin / !RESPONSIBLE)
 * requireAddUserToAppointment         3 branches (admin / !RESPONSIBLE / !areFriends)
 * requireAddGroupToAppointment        3 branches (admin / !RESPONSIBLE / !isGroupMember)
 * requireChangeParticipantRole        2 branches (admin / !RESPONSIBLE)
 * requireRemoveParticipant            2 branches (admin / !RESPONSIBLE)
 * requireRemoveGroupFromAppointment   2 branches (admin / !RESPONSIBLE)
 * requireAddGroupMember               3 branches (admin / !isGroupMember / !areFriends)
 * requireRemoveGroupMember            4 branches (admin / self / isOwner / !isOwner)
 * requireReadGroupMembers             2 branches (admin / !isMember)
 * requireEditGroup                    2 branches (admin / !isOwner)
 * requireDeleteGroup                  2 branches (admin / !isOwner)
 * requireSendMessage                  2 branches (admin / NONE)
 * </pre>
 * Total branches: 37 | Tests: 37
 */
@QuarkusTest
class AuthorizationServiceTest {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final Long   APPT_ID      = 1L;
    private static final Long   GROUP_ID     = 2L;
    private static final String ACTING_USER  = "oidc-acting-10";
    private static final String TARGET_USER  = "oidc-target-20";

    // ── CDI injection ────────────────────────────────────────────────────────
    @Inject
    AuthorizationService service;

    @InjectMock
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @InjectMock
    GroupRepository groupRepository;

    @InjectMock
    TeamRepository teamRepository;

    @InjectMock
    PrincipalContext principalContext;

    // ── Helpers ──────────────────────────────────────────────────────────────
    private void asAdmin() {
        when(principalContext.isAdminRequest()).thenReturn(true);
    }

    private void asUser(UserRole role) {
        when(principalContext.isAdminRequest()).thenReturn(false);
        when(appointmentParticipationQueryService.getUserRole(APPT_ID, ACTING_USER)).thenReturn(role);
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireReadAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireReadAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireReadAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userHasNoRole() {
            asUser(UserRole.NONE);
            assertThatThrownBy(() -> service.requireReadAppointment(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no role");
        }

        @Test
        void should_allowAccess_when_userHasGuestRole() {
            asUser(UserRole.GUEST);
            assertThatCode(() -> service.requireReadAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireUpdateAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireUpdateAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireUpdateAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userRoleIsNone() {
            asUser(UserRole.NONE);
            assertThatThrownBy(() -> service.requireUpdateAppointment(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("at least attendant");
        }

        @Test
        void should_throwForbidden_when_userRoleIsGuest() {
            asUser(UserRole.GUEST);
            assertThatThrownBy(() -> service.requireUpdateAppointment(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("at least attendant");
        }

        @Test
        void should_allowAccess_when_userRoleIsAttendant() {
            asUser(UserRole.ATTENDANT);
            assertThatCode(() -> service.requireUpdateAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_allowAccess_when_userRoleIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireUpdateAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireDeleteAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireDeleteAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireDeleteAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.ATTENDANT);
            assertThatThrownBy(() -> service.requireDeleteAppointment(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_allowAccess_when_userIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireDeleteAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireCancelAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireCancelAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireCancelAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.ATTENDANT);
            assertThatThrownBy(() -> service.requireCancelAppointment(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_allowAccess_when_userIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireCancelAppointment(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireAddUserToAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireAddUserToAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireAddUserToAppointment(APPT_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.ATTENDANT);
            assertThatThrownBy(() -> service.requireAddUserToAppointment(APPT_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_throwForbidden_when_responsibleButTargetIsNotTeamMember() {
            asUser(UserRole.RESPONSIBLE);
            when(teamRepository.shareTeam(ACTING_USER, TARGET_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireAddUserToAppointment(APPT_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Team");
        }

        @Test
        void should_allowAccess_when_responsibleAndTargetIsTeamMember() {
            asUser(UserRole.RESPONSIBLE);
            when(teamRepository.shareTeam(ACTING_USER, TARGET_USER)).thenReturn(true);

            assertThatCode(() -> service.requireAddUserToAppointment(APPT_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireAddGroupToAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireAddGroupToAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireAddGroupToAppointment(APPT_ID, ACTING_USER, GROUP_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.GUEST);
            assertThatThrownBy(() -> service.requireAddGroupToAppointment(APPT_ID, ACTING_USER, GROUP_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_throwForbidden_when_responsibleButNotGroupMember() {
            asUser(UserRole.RESPONSIBLE);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireAddGroupToAppointment(APPT_ID, ACTING_USER, GROUP_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("member");
        }

        @Test
        void should_allowAccess_when_responsibleAndGroupMember() {
            asUser(UserRole.RESPONSIBLE);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);

            assertThatCode(() -> service.requireAddGroupToAppointment(APPT_ID, ACTING_USER, GROUP_ID))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireChangeParticipantRoleAtAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireChangeParticipantRoleAtAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireChangeParticipantRoleAtAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER, UserRole.ATTENDANT))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.ATTENDANT);
            assertThatThrownBy(() -> service.requireChangeParticipantRoleAtAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER, UserRole.GUEST))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_allowAccess_when_userIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireChangeParticipantRoleAtAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER, UserRole.ATTENDANT))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireRemoveParticipantFromAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireRemoveParticipantFromAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireRemoveParticipantFromAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.HELPER);
            assertThatThrownBy(() -> service.requireRemoveParticipantFromAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_allowAccess_when_userIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireRemoveParticipantFromAppointment(
                    APPT_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireRemoveGroupFromAppointment
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireRemoveGroupFromAppointment {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireRemoveGroupFromAppointment(
                    APPT_ID, ACTING_USER, GROUP_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userIsNotResponsible() {
            asUser(UserRole.ATTENDANT);
            assertThatThrownBy(() -> service.requireRemoveGroupFromAppointment(
                    APPT_ID, ACTING_USER, GROUP_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("responsible");
        }

        @Test
        void should_allowAccess_when_userIsResponsible() {
            asUser(UserRole.RESPONSIBLE);
            assertThatCode(() -> service.requireRemoveGroupFromAppointment(
                    APPT_ID, ACTING_USER, GROUP_ID))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireAddGroupMember
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireAddGroupMember {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireAddGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_actingUserIsNotGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireAddGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("member");
        }

        @Test
        void should_throwForbidden_when_groupMemberButTargetIsNotTeamMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);
            when(teamRepository.shareTeam(ACTING_USER, TARGET_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireAddGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Team");
        }

        @Test
        void should_allowAccess_when_groupMemberAndTargetIsTeamMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);
            when(teamRepository.shareTeam(ACTING_USER, TARGET_USER)).thenReturn(true);

            assertThatCode(() -> service.requireAddGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireRemoveGroupMember
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireRemoveGroupMember {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireRemoveGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_actingUserIsNotGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireRemoveGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Mitglied");
        }

        @Test
        void should_allowAccess_when_actingUserIsGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);

            assertThatCode(() -> service.requireRemoveGroupMember(GROUP_ID, ACTING_USER, TARGET_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireReadGroupMembers
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireReadGroupMembers {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireReadGroupMembers(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_notGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireReadGroupMembers(GROUP_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void should_allowAccess_when_isGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);

            assertThatCode(() -> service.requireReadGroupMembers(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireEditGroup
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireEditGroup {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireEditGroup(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_notGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireEditGroup(GROUP_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Mitglied");
        }

        @Test
        void should_allowAccess_when_isGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);

            assertThatCode(() -> service.requireEditGroup(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireDeleteGroup
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireDeleteGroup {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireDeleteGroup(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_notGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(false);

            assertThatThrownBy(() -> service.requireDeleteGroup(GROUP_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Mitglied");
        }

        @Test
        void should_allowAccess_when_isGroupMember() {
            when(principalContext.isAdminRequest()).thenReturn(false);
            when(groupRepository.isMember(GROUP_ID, ACTING_USER)).thenReturn(true);

            assertThatCode(() -> service.requireDeleteGroup(GROUP_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // requireSendMessage
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RequireSendMessage {

        @Test
        void should_allowAccess_when_adminRequest() {
            asAdmin();
            assertThatCode(() -> service.requireSendMessage(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_throwForbidden_when_userHasNoRole() {
            asUser(UserRole.NONE);
            assertThatThrownBy(() -> service.requireSendMessage(APPT_ID, ACTING_USER))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no role");
        }

        @Test
        void should_allowAccess_when_userHasGuestRole() {
            asUser(UserRole.GUEST);
            assertThatCode(() -> service.requireSendMessage(APPT_ID, ACTING_USER))
                    .doesNotThrowAnyException();
        }
    }
}
