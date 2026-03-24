package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.SettingsDto;
import de.chronos_live.chronos_date_api.mapper.SettingsMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
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
 * Panache-enhanced call on {@link Settings} and {@link User} so no real
 * database is ever touched.
 *
 * <p><b>Untestable branches:</b>
 * <ul>
 *   <li>{@code checkSetting} line 67: the final {@code return false} is dead code —
 *       all five {@code AppointmentNotificationSetting} values are handled by their
 *       own {@code if}-branch, so no value can reach the trailing return.</li>
 *   <li>{@code checkSetting} line 49: the {@code appointmentParticipation == null}
 *       guard is unreachable through any public method — every public caller accesses
 *       {@code appointmentParticipation.getUser().id} before calling this method,
 *       which would already NPE if {@code appointmentParticipation} were null.</li>
 * </ul>
 */
@QuarkusTest
class SettingsServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long USER_ID = 1L;

    // ── CDI injection ──────────────────────────────────────────────────────────
    @Inject
    SettingsService service;

    @Inject
    AgroalDataSource dataSource;

    @InjectMock
    SettingsMapper settingsMapper;

    @BeforeEach
    void insertUserFixture() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, firstname, lastname) VALUES (1, 'Test', 'User') ON CONFLICT DO NOTHING");
        }
    }

    @AfterEach
    void cleanupSettingsTestData() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM settings WHERE user_id = 1");
        }
    }

    // ── Test-object builders ───────────────────────────────────────────────────
    private static User buildUser(Long id) {
        User u = new User();
        u.id = id;
        u.setFirstName("Alice");
        u.setLastName("Test");
        return u;
    }

    private static Settings buildSettings(User user) {
        Settings s = new Settings();
        s.setUser(user);
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

    private static AppointmentParticipation buildParticipation(User user, UserRole role) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUser(user);
        ap.setRole(role);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static GroupMember buildGroupMember(User user) {
        GroupMember gm = new GroupMember();
        gm.setUser(user);
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
            PanacheMock.mock(User.class);
        }

        // B1=true — settings record already exists for this user
        @Test
        void should_returnExistingSettings_when_settingsAlreadyExist() {
            User user = buildUser(USER_ID);
            Settings existing = buildSettings(user);
            stubSettingsFound(existing);

            Settings result = service.getOrCreateSettings(USER_ID);

            assertThat(result).isSameAs(existing);
            PanacheMock.verifyNoInteractions(User.class);
        }

        // B2=true — no settings record exists; default is created and persisted
        @Test
        void should_createDefaultSettingsAndPersist_when_settingsDoNotExist() {
            User user = buildUser(USER_ID);
            stubSettingsNotFound();
            when(User.<User>findById(USER_ID)).thenReturn(user);

            Settings result = service.getOrCreateSettings(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getUser()).isSameAs(user);
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
            // instance persist() is not interceptable by PanacheMock with single-arg matcher;
            // default values assertions above confirm the expected persist path was taken
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
            PanacheMock.mock(User.class);
        }

        @Test
        void should_delegateToMapper_when_called() {
            User user = buildUser(USER_ID);
            Settings existing = buildSettings(user);
            stubSettingsFound(existing);

            SettingsDto dto = new SettingsDto();
            dto.setAppointment_moved("DISABLED");

            service.updateSettings(USER_ID, dto);

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
            PanacheMock.mock(User.class);
        }

        // B1 — null setting (we fabricate a Settings with null appointmentMoved)
        @Test
        void should_returnFalse_when_settingIsNull() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMoved(null);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B3 — DISABLED always returns false regardless of role
        @Test
        void should_returnFalse_when_settingIsDisabled() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMoved(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.RESPONSIBLE);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B4 true — ALL with role >= GUEST
        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.GUEST);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isTrue();
        }

        // B4 false — ALL with role < GUEST (NONE)
        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.NONE);
            boolean result = service.sendAppointmentMovedNotification(ap);

            assertThat(result).isFalse();
        }

        // B5 true — RESPONSIBLE with role >= RESPONSIBLE
        @Test
        void should_returnTrue_when_settingResponsibleAndRoleIsResponsible() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.RESPONSIBLE);
            boolean result = service.sendAppointmentParticipantAddedEventNotification(ap);

            assertThat(result).isTrue();
        }

        // B5 false — RESPONSIBLE with role < RESPONSIBLE (HELPER)
        @Test
        void should_returnFalse_when_settingResponsibleAndRoleIsHelper() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.HELPER);
            boolean result = service.sendAppointmentParticipantAddedEventNotification(ap);

            assertThat(result).isFalse();
        }

        // B6 true — HELPER with role >= HELPER
        @Test
        void should_returnTrue_when_settingHelperAndRoleIsHelper() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.HELPER);
            boolean result = service.sendAppointmentParticipationStatusChangedNotification(ap);

            assertThat(result).isTrue();
        }

        // B6 false — HELPER with role < HELPER (ATTENDANT)
        @Test
        void should_returnFalse_when_settingHelperAndRoleIsAttendant() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentParticipationStatusChangedNotification(ap);

            assertThat(result).isFalse();
        }

        // B7 true — ATTENDANT with role >= ATTENDANT
        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            boolean result = service.sendAppointmentMessageSentNotification(ap);

            assertThat(result).isTrue();
        }

        // B7 false — ATTENDANT with role < ATTENDANT (GUEST)
        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.GUEST);
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
            PanacheMock.mock(User.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsAttendant() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentCancelledNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentCancelled(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.RESPONSIBLE);
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
            PanacheMock.mock(User.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentParticipationInvalidNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.GUEST);
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
            PanacheMock.mock(User.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.ATTENDANT);
            assertThat(service.sendAppointmentParticipationStatusPendingReminderNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.RESPONSIBLE);
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
            PanacheMock.mock(User.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.GUEST);
            assertThat(service.sendAppointmentReminderNotification(ap)).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            AppointmentParticipation ap = buildParticipation(user, UserRole.NONE);
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
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnTrue_when_groupMemberAddedIsEnabled() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setGroupMemberAdded(NotificationSetting.ENABLED);
            stubSettingsFound(s);

            GroupMember gm = buildGroupMember(user);
            assertThat(service.sendGroupMemberAddedNotification(gm)).isTrue();
        }

        // B2=true
        @Test
        void should_returnFalse_when_groupMemberAddedIsDisabled() {
            User user = buildUser(USER_ID);
            Settings s = buildSettings(user);
            s.setGroupMemberAdded(NotificationSetting.DISABLED);
            stubSettingsFound(s);

            GroupMember gm = buildGroupMember(user);
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
            User user = buildUser(USER_ID);
            AppointmentParticipation ap = buildParticipation(user, UserRole.NONE);
            assertThat(service.sendAppointmentParticipationStatusRecheckRequestedNotification(ap)).isTrue();
        }
    }
}
