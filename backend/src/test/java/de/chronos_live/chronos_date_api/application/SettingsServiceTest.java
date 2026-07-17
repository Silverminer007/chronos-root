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
 * dependencies. {@link PanacheMock} intercepts Panache calls on {@link Settings}.
 * No {@code users} table access — Settings uses {@code userOidcId} String.
 */
@QuarkusTest
class SettingsServiceTest {

    private static final String USER_OIDC = "oidc-user-1";

    @Inject
    SettingsService service;

    @InjectMock
    SettingsMapper settingsMapper;

    private static Settings buildSettings(String oidcId) {
        Settings s = new Settings();
        s.setUserOidcId(oidcId);
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

    private static AppointmentParticipation buildParticipation(String oidcId, UserRole role) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUserOidcId(oidcId);
        ap.setRole(role);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static GroupMember buildGroupMember(String oidcId) {
        GroupMember gm = new GroupMember();
        gm.setUserOidcId(oidcId);
        return gm;
    }

    @SuppressWarnings("unchecked")
    private static void stubSettingsFound(Settings settings) {
        PanacheQuery<Settings> q = mock(PanacheQuery.class);
        when(Settings.<Settings>find(anyString(), any(Object[].class))).thenReturn(q);
        when(q.firstResultOptional()).thenReturn(Optional.of(settings));
    }

    @SuppressWarnings("unchecked")
    private static void stubSettingsNotFound() {
        PanacheQuery<Settings> q = mock(PanacheQuery.class);
        when(Settings.<Settings>find(anyString(), any(Object[].class))).thenReturn(q);
        when(q.firstResultOptional()).thenReturn(Optional.empty());
    }

    @Nested
    class GetOrCreateSettings {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnExistingSettings_when_settingsAlreadyExist() {
            Settings existing = buildSettings(USER_OIDC);
            stubSettingsFound(existing);

            Settings result = service.getOrCreateSettings(USER_OIDC);

            assertThat(result).isSameAs(existing);
        }

        @Test
        void should_createDefaultSettingsAndPersist_when_settingsDoNotExist() {
            stubSettingsNotFound();

            Settings result = service.getOrCreateSettings(USER_OIDC);

            assertThat(result).isNotNull();
            assertThat(result.getUserOidcId()).isEqualTo(USER_OIDC);
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

    @Nested
    class UpdateSettings {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_delegateToMapper_when_called() {
            Settings existing = buildSettings(USER_OIDC);
            stubSettingsFound(existing);

            SettingsDto dto = new SettingsDto();
            dto.setAppointment_moved("DISABLED");

            service.updateSettings(USER_OIDC, dto);

            verify(settingsMapper).updateEntityFromDto(dto, existing);
        }
    }

    @Nested
    class CheckSetting {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnFalse_when_settingIsNull() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMoved(null);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMovedNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isFalse();
        }

        @Test
        void should_returnFalse_when_settingIsDisabled() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMoved(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMovedNotification(buildParticipation(USER_OIDC, UserRole.RESPONSIBLE))).isFalse();
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMovedNotification(buildParticipation(USER_OIDC, UserRole.GUEST))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMoved(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMovedNotification(buildParticipation(USER_OIDC, UserRole.NONE))).isFalse();
        }

        @Test
        void should_returnTrue_when_settingResponsibleAndRoleIsResponsible() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipantAddedEventNotification(buildParticipation(USER_OIDC, UserRole.RESPONSIBLE))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingResponsibleAndRoleIsHelper() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipantAdded(AppointmentNotificationSetting.RESPONSIBLE);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipantAddedEventNotification(buildParticipation(USER_OIDC, UserRole.HELPER))).isFalse();
        }

        @Test
        void should_returnTrue_when_settingHelperAndRoleIsHelper() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationStatusChangedNotification(buildParticipation(USER_OIDC, UserRole.HELPER))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingHelperAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationStatusChanged(AppointmentNotificationSetting.HELPER);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationStatusChangedNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isFalse();
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMessageSentNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentMessage(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentMessageSentNotification(buildParticipation(USER_OIDC, UserRole.GUEST))).isFalse();
        }
    }

    @Nested
    class SendAppointmentCancelledNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentCancelled(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentCancelledNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentCancelled(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentCancelledNotification(buildParticipation(USER_OIDC, UserRole.RESPONSIBLE))).isFalse();
        }
    }

    @Nested
    class SendAppointmentParticipationInvalidNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationInvalidNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAttendantAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationInvalid(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationInvalidNotification(buildParticipation(USER_OIDC, UserRole.GUEST))).isFalse();
        }
    }

    @Nested
    class SendAppointmentParticipationStatusPendingReminderNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAttendantAndRoleIsAttendant() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.ATTENDANT);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationStatusPendingReminderNotification(buildParticipation(USER_OIDC, UserRole.ATTENDANT))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingDisabled() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentParticipationStatusPending(AppointmentNotificationSetting.DISABLED);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentParticipationStatusPendingReminderNotification(buildParticipation(USER_OIDC, UserRole.RESPONSIBLE))).isFalse();
        }
    }

    @Nested
    class SendAppointmentReminderNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_settingAllAndRoleIsGuest() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentReminderNotification(buildParticipation(USER_OIDC, UserRole.GUEST))).isTrue();
        }

        @Test
        void should_returnFalse_when_settingAllAndRoleIsNone() {
            Settings s = buildSettings(USER_OIDC);
            s.setAppointmentReminder(AppointmentNotificationSetting.ALL);
            stubSettingsFound(s);

            assertThat(service.sendAppointmentReminderNotification(buildParticipation(USER_OIDC, UserRole.NONE))).isFalse();
        }
    }

    @Nested
    class SendGroupMemberAddedNotification {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Settings.class);
        }

        @Test
        void should_returnTrue_when_groupMemberAddedIsEnabled() {
            Settings s = buildSettings(USER_OIDC);
            s.setGroupMemberAdded(NotificationSetting.ENABLED);
            stubSettingsFound(s);

            assertThat(service.sendGroupMemberAddedNotification(buildGroupMember(USER_OIDC))).isTrue();
        }

        @Test
        void should_returnFalse_when_groupMemberAddedIsDisabled() {
            Settings s = buildSettings(USER_OIDC);
            s.setGroupMemberAdded(NotificationSetting.DISABLED);
            stubSettingsFound(s);

            assertThat(service.sendGroupMemberAddedNotification(buildGroupMember(USER_OIDC))).isFalse();
        }
    }

    @Nested
    class SendAppointmentParticipationStatusRecheckRequestedNotification {

        @Test
        void should_alwaysReturnTrue() {
            AppointmentParticipation ap = buildParticipation(USER_OIDC, UserRole.NONE);
            assertThat(service.sendAppointmentParticipationStatusRecheckRequestedNotification(ap)).isTrue();
        }
    }
}
