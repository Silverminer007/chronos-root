package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.mapper.SettingsMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SettingsService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces CDI
 * dependencies with Mockito mocks. {@link PanacheMock} intercepts every
 * Panache-enhanced call on {@link Settings} so no real database is ever touched.
 * The User entity has been removed — user identity is now represented by OIDC ID
 * strings. No DB fixture for a users table is needed.
 *
 * <p><b>Untestable branches:</b>
 * <ul>
 *   <li>{@code checkSetting} line 67: the final {@code return false} is dead code —
 *       all five {@code AppointmentNotificationSetting} values are handled by their
 *       own {@code if}-branch, so no value can reach the trailing return.</li>
 *   <li>{@code checkSetting} line 49: the {@code appointmentParticipation == null}
 *       guard is unreachable through any public method — every public caller passes
 *       a non-null participation.</li>
 * </ul>
 */
@QuarkusTest
class SettingsServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String USER_OIDC_ID = "oidc-user-1";

    // ── CDI injection ──────────────────────────────────────────────────────────
    @Inject
    SettingsService service;

    @InjectMock
    SettingsMapper settingsMapper;

    // ── Test-object builders ───────────────────────────────────────────────────
    private static Settings buildSettings(String userOidcId) {
        Settings s = new Settings();
        s.setUserOidcId(userOidcId);
        s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
        s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
        s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
        s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.RESPONSIBLE);
        s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
        s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
        s.setGroupMemberAdded(NotificationSetting.DISABLED);
        return s;
    }

    private static AppointmentParticipation buildParticipation(String userOidcId, UserRole role) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUserOidcId(userOidcId);
        ap.setRole(role);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static GroupMember buildGroupMember(String userOidcId) {
        GroupMember gm = new GroupMember();
        gm.setUserOidcId(userOidcId);
        return gm;
    }

    // ── Helper: stub Settings.find to return an existing Settings ──────────────
    @SuppressWarnings("unchecked")
    private static void stubSettingsFound(Settings settings) {
        PanacheQuery<Settings> q = mock(PanacheQuery.class);
        when(Settings.<Settings>find(anyString(), any(Object[].class))).thenReturn(q);
        when(q.firstResultOptional()).thenReturn(Optional.of(settings));
    }

    // ── Helper: stub Settings.find to return empty (create path) ──────────────
    @SuppressWarnings("unchecked")
    private static void stubSettingsNotFound() {
        PanacheQuery<Settings> q = mock(PanacheQuery.class);
        when(Settings.<Settings>find(anyString(), any(Object[].class))).thenReturn(q);
        when(q.firstResultOptional()).thenReturn(Optional.empty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getOrCreateSettings
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getOrCreateSettings:
     *   B1  settings found in DB              → return existing settings
     *   B2  settings not found in DB          → create default, persist, return
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetOrCreateSettings {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        // B1=true — settings record already exists for this user
        @Test
        void should_returnExistingSettings_when_settingsAlreadyExist() {
            Settings existing = buildSettings(USER_OIDC_ID);
            stubSettingsFound(existing);

            Settings result = service.getOrCreateSettings(USER_OIDC_ID);

            assertThat(result).isSameAs(existing);
        }

        // B2=true — no settings record exists; default is created and persisted
        @Test
        void should_createDefaultSettingsAndPersist_when_settingsDoNotExist() {
            stubSettingsNotFound();

            Settings result = service.getOrCreateSettings(USER_OIDC_ID);

            assertThat(result).isNotNull();
            assertThat(result.getUserOidcId()).isEqualTo(USER_OIDC_ID);
            // Verify default values match getDefaultSetting()
            assertThat(result.getAppointmentMoved()).isEqualTo(AppointmentNotificationSetting.ALL);
            assertThat(result.getAppointmentMessage()).isEqualTo(AppointmentNotificationSetting.ATTENDANT);
            assertThat(result.getAppointmentCancelled()).isEqualTo(AppointmentNotificationSetting.ALL);
            assertThat(result.getAppointmentParticipantAdded()).isEqualTo(AppointmentNotificationSetting.RESPONSIBLE);
            assertThat(result.getAppointmentParticipationStatusChanged()).isEqualTo(AppointmentNotificationSetting.RESPONSIBLE);
            assertThat(result.getAppointmentParticipationInvalid()).isEqualTo(AppointmentNotificationSetting.ATTENDANT);
            assertThat(result.getAppointmentParticipationStatusPending()).isEqualTo(AppointmentNotificationSetting.ATTENDANT);
            assertThat(result.getAppointmentReminder()).isEqualTo(AppointmentNotificationSetting.ALL);
            assertThat(result.getGroupMemberAdded()).isEqualTo(NotificationSetting.DISABLED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateSettings
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – updateSettings:
     *   No conditional branches in the method itself; delegates to
     *   getOrCreateSettings + settingsMapper. One test is sufficient.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class UpdateSettings {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_delegateToMapper_when_called() {
            Settings existing = buildSettings(USER_OIDC_ID);
            stubSettingsFound(existing);

            SettingsDto dto = new SettingsDto();
            dto.setAppointment_moved("DISABLED");

            service.updateSettings(USER_OIDC_ID, dto);

            verify(settingsMapper).updateEntityFromDto(dto, existing);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // checkSetting (tested through sendXxx methods)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – checkSetting (private, exercised via public send* methods):
     *   B1  setting == null                    → false
     *   B2  appointmentParticipation == null   → false
     *   B3  DISABLED                           → false
     *   B4  ALL, role >= GUEST                 → true
     *   B4  ALL, role < GUEST (NONE)           → false
     *   B5  RESPONSIBLE, role >= RESPONSIBLE   → true
     *   B5  RESPONSIBLE, role < RESPONSIBLE    → false
     *   B6  HELPER, role >= HELPER             → true
     *   B6  HELPER, role < HELPER              → false
     *   B7  ATTENDANT, role >= ATTENDANT       → true
     *   B7  ATTENDANT, role < ATTENDANT        → false
     *
     * Total branches: 12  |  Tests: 10
     */
    @Nested
    class CheckSetting {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        // B1 — null setting (we fabricate a Settings with null appointmentMoved)
        @Test
        void should_returnFalse_when_settingIsNull() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMoved(null);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B3 — DISABLED always returns false regardless of role
        @Test
        void should_returnFalse_when_settingIsDisabled() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMoved(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.RESPONSIBLE);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B4 true — ALL with role >= GUEST
        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.GUEST);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isTrue();
        }

        // B4 false — ALL with role < GUEST (NONE)
        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.NONE);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B5 true — RESPONSIBLE with role >= RESPONSIBLE
        @Test
        void should_returnTrue_when_settingResponsibleAndRoleIsResponsible() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.RESPONSIBLE);
            boolean result = service.sendAppointmentParticipantAddedEventNotification(ap);

            assertThat(result).isTrue();
        }

        // B5 false — RESPONSIBLE with role < RESPONSIBLE (HELPER)
        @Test
        void should_returnFalse_when_settingResponsibleAndRoleIsHelper() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.HELPER);
            boolean result = service.sendAppointmentParticipantAddedEventNotification(ap);

            assertThat(result).isFalse();
        }

        // B6 true — HELPER with role >= HELPER
        @Test
        void should_returnTrue_when_settingHelperAndRoleIsHelper() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.HELPER);
            boolean result = service.sendAppointmentParticipationStatusChangedNotification(ap);

            assertThat(result).isTrue();
        }

        // B6 false — HELPER with role < HELPER (ATTENDANT)
        @Test
        void should_returnFalse_when_settingHelperAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentParticipationStatusChangedNotification(ap);

            assertThat(result).isFalse();
        }

        // B7 true — ATTENDANT with role >= ATTENDANT
        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentMessageSentNotification(ap);

            assertThat(result).isTrue();
        }

        // B7 false — ATTENDANT with role < ATTENDANT (GUEST)
        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.GUEST);
            boolean result = service.sendAppointmentMessageSentNotification(ap);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendAppointmentCancelledNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendAppointmentCancelledNotification:
     *   Delegates to checkSetting using appointmentCancelled field.
     *   Tests true and false branches.
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class SendAppointmentCancelledNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentCancelledNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentCancelled(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.RESPONSIBLE);
            assertThat(service.sendAppointmentCancelledNotification(ap)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendAppointmentParticipationInvalidNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendAppointmentParticipationInvalidNotification:
     *   Delegates to checkSetting using appointmentParticipationInvalid field.
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class SendAppointmentParticipationInvalidNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentParticipationInvalidNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.GUEST);
            assertThat(service.sendAppointmentParticipationInvalidNotification(ap)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendAppointmentParticipationStatusPendingReminderNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendAppointmentParticipationStatusPendingReminderNotification:
     *   Delegates to checkSetting using appointmentParticipationStatusPending field.
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class SendAppointmentParticipationStatusPendingReminderNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentParticipationStatusPendingReminderNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.RESPONSIBLE);
            assertThat(service.sendAppointmentParticipationStatusPendingReminderNotification(ap)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendAppointmentReminderNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendAppointmentReminderNotification:
     *   Delegates to checkSetting using appointmentReminder field.
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class SendAppointmentReminderNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.GUEST);
            assertThat(service.sendAppointmentReminderNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.NONE);
            assertThat(service.sendAppointmentReminderNotification(ap)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendGroupMemberAddedNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendGroupMemberAddedNotification:
     *   B1  groupMemberAdded == ENABLED  → true
     *   B2  groupMemberAdded == DISABLED → false
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class SendGroupMemberAddedNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        // B1=true
        @Test
        void should_returnTrue_when_groupMemberAddedIsEnabled() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setGroupMemberAdded(NotificationSetting.ENABLED);
            stubSettingsFound(s);

            GroupMember gm = buildGroupMember(USER_OIDC_ID);
            assertThat(service.sendGroupMemberAddedNotification(gm)).isTrue();
        }

        // B2=true
        @Test
        void should_returnFalse_when_groupMemberAddedIsDisabled() {
            Settings s = buildSettings(USER_OIDC_ID);
            s.setGroupMemberAdded(NotificationSetting.DISABLED);
            stubSettingsFound(s);

            GroupMember gm = buildGroupMember(USER_OIDC_ID);
            assertThat(service.sendGroupMemberAddedNotification(gm)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendAppointmentParticipationStatusRecheckRequestedNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendAppointmentParticipationStatusRecheckRequestedNotification:
     *   No conditional branches — always returns {@code true}.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class SendAppointmentParticipationStatusRecheckRequestedNotification {

        @Test
        void should_alwaysReturnTrue() {
            AppointmentParticipation ap = buildParticipation(USER_OIDC_ID, UserRole.NONE);
            assertThat(service.sendAppointmentParticipationStatusRecheckRequestedNotification(ap)).isTrue();
        }
    }
}
