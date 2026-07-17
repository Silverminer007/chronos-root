package de.chronos_live.chronos_date_api.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.NotificationPort;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.PushAppointmentMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link WebPushService}.
 *
 * <p>No {@code @QuarkusTest}, no database, no VAPID keys. All infrastructure
 * dependencies are replaced with Mockito mocks. The {@link NotificationPort}
 * mock is the primary assertion point — tests verify which oidcIds and
 * payload fields reach the port.
 */
@ExtendWith(MockitoExtension.class)
class WebPushServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String ACTING_USER_OIDC = "oidc-acting-1";
    private static final String TARGET_USER_OIDC = "oidc-target-2";
    private static final Long   APPOINTMENT_ID   = 10L;
    private static final Long   GROUP_ID         = 3L;
    private static final Long   REQUEST_ID       = 99L;
    private static final Long   MESSAGE_ID       = 50L;

    // ── Mocks ──────────────────────────────────────────────────────────────────
    @Mock NotificationPort                          notificationPort;
    @Mock UserQueryService                          userQueryService;
    @Mock AppointmentQueryService                   appointmentQueryService;
    @Mock GroupQueryService                         groupQueryService;
    @Mock SettingsService                           settingsService;
    @Mock AppointmentParticipationQueryService      appointmentParticipationQueryService;
    @Mock MessageQueryService                       messageQueryService;
    @Mock PushAppointmentMapper                     appointmentMapper;
    @Mock GroupMapper                               groupMapper;
    @Mock ObjectMapper                              objectMapper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) MeterRegistry meterRegistry;

    @InjectMocks
    WebPushService webPushService;

    // ── Domain-object helpers ──────────────────────────────────────────────────
    private static UserIdentity identity(String oidcId, String firstName, String lastName) {
        return new UserIdentity(oidcId, firstName, lastName, firstName.toLowerCase() + "@example.com", null);
    }

    private static Appointment appointment(long id, String name) {
        Appointment a = new Appointment();
        a.id = id;
        a.setName(name);
        return a;
    }

    private static Group group(long id, String groupName) {
        Group g = new Group();
        g.id = id;
        g.setGroupName(groupName);
        return g;
    }

    private static AppointmentParticipation participation(String userOidcId) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUserOidcId(userOidcId);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static GroupMember groupMember(String userOidcId) {
        GroupMember gm = new GroupMember();
        gm.setUserOidcId(userOidcId);
        return gm;
    }

    /** Captures the single payload sent to the given oidcId and returns it. */
    private String capturePayload(String oidcId) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).send(eq(oidcId), captor.capture());
        return captor.getValue();
    }

    // ── Simple delegation ──────────────────────────────────────────────────────

    @Nested
    class GetPublicKey {

        @Test
        void delegates_to_notification_port() {
            when(notificationPort.getVapidPublicKey()).thenReturn("my-vapid-key");

            assertThat(webPushService.getPublicKey()).isEqualTo("my-vapid-key");
        }
    }

    @Nested
    class SendNotification {

        @Test
        void forwards_payload_unchanged_to_port() {
            webPushService.sendNotification("oidc-42", "{\"custom\":true}");

            verify(notificationPort).send("oidc-42", "{\"custom\":true}");
        }
    }

    @Nested
    class SendToUser {

        @Test
        void builds_title_body_payload_and_delegates() {
            webPushService.sendToUser("oidc-5", "Hello", "World");

            String payload = capturePayload("oidc-5");
            assertThat(payload)
                    .contains("\"title\":\"Hello\"")
                    .contains("\"body\":\"World\"");
        }
    }

    // ── Friendship events ──────────────────────────────────────────────────────

    @Nested
    class OnFriendshipRequestSent {

        @Test
        void sends_to_addressee_with_correct_fields_and_no_db_lookup() {
            var event = new FriendshipRequestSentEvent(REQUEST_ID, ACTING_USER_OIDC, TARGET_USER_OIDC, "Alice Test");

            webPushService.onFriendshipRequestSent(event);

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"NEW_FRIENDSHIP_REQUEST\"")
                    .contains("\"requester_id\":\"" + ACTING_USER_OIDC + "\"")
                    .contains("\"requester_name\":\"Alice Test\"")
                    .contains("\"request_id\":" + REQUEST_ID);
            verifyNoInteractions(userQueryService);
        }
    }

    @Nested
    class OnFriendshipRemoved {

        @Test
        void sends_FRIENDSHIP_REMOVED_to_friend() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            var event = new FriendshipRemovedEvent(ACTING_USER_OIDC, TARGET_USER_OIDC);

            webPushService.onFriendshipRemoved(event);

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_REMOVED\"")
                    .contains("\"acting_user_id\":\"" + ACTING_USER_OIDC + "\"")
                    .contains("\"acting_user_name\":\"Alice Test\"");
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC)).thenReturn(null);

            webPushService.onFriendshipRemoved(new FriendshipRemovedEvent(ACTING_USER_OIDC, TARGET_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnFriendshipAccepted {

        @Test
        void sends_FRIENDSHIP_ACCEPTED_to_requester() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC))
                    .thenReturn(identity(TARGET_USER_OIDC, "Bob", "Test"));
            var event = new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_OIDC, TARGET_USER_OIDC);

            webPushService.onFriendshipAccepted(event);

            // service sends to requesterOidcId = ACTING_USER_OIDC
            String payload = capturePayload(ACTING_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_ACCEPTED\"")
                    .contains("\"addressee_id\":\"" + TARGET_USER_OIDC + "\"")
                    .contains("\"addressee_name\":\"Bob Test\"");
        }

        @Test
        void does_nothing_when_addressee_not_found() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC)).thenReturn(null);

            webPushService.onFriendshipAccepted(
                    new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_OIDC, TARGET_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnFriendshipDeclined {

        @Test
        void sends_FRIENDSHIP_DECLINED_to_requester() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC))
                    .thenReturn(identity(TARGET_USER_OIDC, "Bob", "Test"));
            var event = new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_OIDC, TARGET_USER_OIDC);

            webPushService.onFriendshipDeclined(event);

            // service sends to requesterOidcId = ACTING_USER_OIDC
            String payload = capturePayload(ACTING_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_DECLINED\"")
                    .contains("\"addressee_name\":\"Bob Test\"");
        }

        @Test
        void does_nothing_when_addressee_not_found() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC)).thenReturn(null);

            webPushService.onFriendshipDeclined(
                    new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_OIDC, TARGET_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    // ── Appointment events ─────────────────────────────────────────────────────

    @Nested
    class OnAppointmentMoved {

        @BeforeEach
        void stubAppointmentLookup() throws Exception {
            lenient().when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Team Event"));
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
        }

        @Test
        void sends_APPOINTMENT_MOVED_to_participants_allowed_by_settings() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentMovedNotification(any())).thenReturn(true);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_OIDC));

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"APPOINTMENT_MOVED\"")
                    .contains("\"acting_user_name\":\"Alice Test\"");
        }

        @Test
        void skips_participant_when_settings_disallow() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentMovedNotification(any())).thenReturn(false);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC)).thenReturn(null);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_appointment_not_found() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "A", "B"));
            when(appointmentQueryService.findById(APPOINTMENT_ID)).thenReturn(null);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentCancelled {

        @BeforeEach
        void stubAppointmentLookup() throws Exception {
            lenient().when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Team Event"));
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
        }

        @Test
        void sends_APPOINTMENT_CANCELLED_with_canceller_name() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentCancelledNotification(any())).thenReturn(true);

            webPushService.onAppointmentCancelled(
                    new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_OIDC));

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"APPOINTMENT_CANCELLED\"")
                    .contains("\"who_cancelled\":\"Alice Test\"");
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC)).thenReturn(null);

            webPushService.onAppointmentCancelled(
                    new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentReminder {

        @Test
        void sends_PARTICIPATION_REMINDER_to_all_participants_allowed_by_settings() throws Exception {
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Team Event"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentReminder(new AppointmentReminderEvent(APPOINTMENT_ID));

            assertThat(capturePayload(TARGET_USER_OIDC)).contains("\"type\":\"PARTICIPATION_REMINDER\"");
        }

        @Test
        void does_nothing_when_appointment_not_found() {
            when(appointmentQueryService.findById(APPOINTMENT_ID)).thenReturn(null);

            webPushService.onAppointmentReminder(new AppointmentReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentMessageSent {

        @Test
        void sends_title_and_body_to_other_participants() throws Exception {
            Appointment appt = appointment(APPOINTMENT_ID, "Team Meeting");
            Message message = new Message();
            message.setSenderOidcId(ACTING_USER_OIDC);
            message.setAppointment(appt);
            message.setBody("Hello everyone!");

            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(message);
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Sender"));
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentMessageSentNotification(any())).thenReturn(true);

            webPushService.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"title\":\"Alice Sender schreibt zu Team Meeting\"")
                    .contains("\"body\":\"Hello everyone!\"");
        }

        @Test
        void does_nothing_when_message_not_found() {
            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(null);

            webPushService.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnGroupMemberAdded {

        @BeforeEach
        void stubGroupLookup() throws Exception {
            lenient().when(groupQueryService.findById(GROUP_ID)).thenReturn(group(GROUP_ID, "Dev Team"));
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":3}");
        }

        @Test
        void sends_group_notification_to_members_and_personal_notification_to_new_member() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC))
                    .thenReturn(identity(TARGET_USER_OIDC, "Bob", "New"));
            when(groupQueryService.getGroupMembers(GROUP_ID))
                    .thenReturn(List.of(groupMember(ACTING_USER_OIDC)));
            when(settingsService.sendGroupMemberAddedNotification(any())).thenReturn(true);

            webPushService.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));

            ArgumentCaptor<String> groupPayload = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).send(eq(ACTING_USER_OIDC), groupPayload.capture());
            assertThat(groupPayload.getValue()).contains("\"type\":\"NEW_GROUP_MEMBER\"");

            ArgumentCaptor<String> personalPayload = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).send(eq(TARGET_USER_OIDC), personalPayload.capture());
            assertThat(personalPayload.getValue()).contains("\"type\":\"ADDED_TO_GROUP\"");
        }

        @Test
        void does_nothing_when_new_member_not_found() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC)).thenReturn(null);

            webPushService.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_group_not_found() {
            when(userQueryService.findByOidcId(TARGET_USER_OIDC))
                    .thenReturn(identity(TARGET_USER_OIDC, "Bob", "New"));
            when(groupQueryService.findById(GROUP_ID)).thenReturn(null);

            webPushService.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentParticipationStatusPendingReminder {

        @BeforeEach
        void stubAppointmentLookup() throws Exception {
            lenient().when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Team Event"));
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
        }

        @Test
        void sends_PARTICIPATION_STATUS_PENDING_to_pending_participants_allowed_by_settings() {
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            assertThat(capturePayload(TARGET_USER_OIDC)).contains("\"type\":\"PARTICIPATION_STATUS_PENDING\"");
        }

        @Test
        void does_not_notify_participant_with_APPROVED_status() {
            AppointmentParticipation ap = participation(TARGET_USER_OIDC);
            ap.setStatus(ParticipationStatus.APPROVED);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(ap));
            lenient().when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any()))
                    .thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_not_notify_participant_with_REJECTED_status() {
            AppointmentParticipation ap = participation(TARGET_USER_OIDC);
            ap.setStatus(ParticipationStatus.REJECTED);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(ap));
            lenient().when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any()))
                    .thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_not_notify_pending_participant_when_settings_disallow() {
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(false);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void only_notifies_pending_participants_when_mixed_statuses() {
            String approvedUserOidcId = "oidc-approved-20";
            String rejectedUserOidcId = "oidc-rejected-21";

            AppointmentParticipation pendingAp  = participation(TARGET_USER_OIDC);
            AppointmentParticipation approvedAp = participation(approvedUserOidcId);
            approvedAp.setStatus(ParticipationStatus.APPROVED);
            AppointmentParticipation rejectedAp = participation(rejectedUserOidcId);
            rejectedAp.setStatus(ParticipationStatus.REJECTED);

            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(pendingAp, approvedAp, rejectedAp));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verify(notificationPort, times(1)).send(eq(TARGET_USER_OIDC), any());
            verify(notificationPort, never()).send(eq(approvedUserOidcId), any());
            verify(notificationPort, never()).send(eq(rejectedUserOidcId), any());
        }

        @Test
        void does_nothing_when_appointment_not_found() {
            when(appointmentQueryService.findById(APPOINTMENT_ID)).thenReturn(null);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentParticipationStatusChanged {

        @Test
        void sends_PARTICIPATION_STATUS_CHANGED_to_other_participants() throws Exception {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Test"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(TARGET_USER_OIDC)));
            when(settingsService.sendAppointmentParticipationStatusChangedNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_OIDC, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            String payload = capturePayload(TARGET_USER_OIDC);
            assertThat(payload)
                    .contains("\"type\":\"PARTICIPATION_STATUS_CHANGED\"")
                    .contains("\"new_participation_status\":\"APPROVED\"")
                    .contains("\"user_name\":\"Alice Test\"");
        }

        @Test
        void does_not_notify_acting_user_themselves() throws Exception {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC))
                    .thenReturn(identity(ACTING_USER_OIDC, "Alice", "Test"));
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Test"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            // Actor is the only participant — the filter excludes them
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(ACTING_USER_OIDC)));
            lenient().when(settingsService.sendAppointmentParticipationStatusChangedNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_OIDC, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findByOidcId(ACTING_USER_OIDC)).thenReturn(null);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_OIDC, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(notificationPort);
        }
    }
}
