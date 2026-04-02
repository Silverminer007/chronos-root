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
 * mock is the primary assertion point — tests verify which user IDs and
 * payload fields reach the port.
 *
 * <p>This test class demonstrates the testability improvement introduced by
 * the hexagonal refactoring: before the refactoring a {@code @QuarkusTest}
 * with static {@code PanacheMock} and working VAPID keys were required to
 * exercise any business logic in this service.
 */
@ExtendWith(MockitoExtension.class)
class WebPushServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long ACTING_USER_ID  = 1L;
    private static final Long TARGET_USER_ID  = 2L;
    private static final Long APPOINTMENT_ID  = 10L;
    private static final Long GROUP_ID        = 3L;
    private static final Long REQUEST_ID      = 99L;
    private static final Long MESSAGE_ID      = 50L;

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
    private static User user(long id, String firstName, String lastName) {
        User u = new User();
        u.id = id;
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return u;
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

    private static AppointmentParticipation participation(User user) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUser(user);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static GroupMember groupMember(User user) {
        GroupMember gm = new GroupMember();
        gm.setUser(user);
        return gm;
    }

    /** Captures the single payload sent to the given userId and returns it. */
    private String capturePayload(Long userId) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).send(eq(userId), captor.capture());
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
            webPushService.sendNotification(42L, "{\"custom\":true}");

            verify(notificationPort).send(42L, "{\"custom\":true}");
        }
    }

    @Nested
    class SendToUser {

        @Test
        void builds_title_body_payload_and_delegates() {
            webPushService.sendToUser(5L, "Hello", "World");

            String payload = capturePayload(5L);
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
            var event = new FriendshipRequestSentEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID, "Alice Test");

            webPushService.onFriendshipRequestSent(event);

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"NEW_FRIENDSHIP_REQUEST\"")
                    .contains("\"requester_id\":" + ACTING_USER_ID)
                    .contains("\"requester_name\":\"Alice Test\"")
                    .contains("\"request_id\":" + REQUEST_ID);
            verifyNoInteractions(userQueryService);
        }
    }

    @Nested
    class OnFriendshipRemoved {

        @Test
        void sends_FRIENDSHIP_REMOVED_to_friend() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(user(ACTING_USER_ID, "Alice", "Test"));
            var event = new FriendshipRemovedEvent(ACTING_USER_ID, TARGET_USER_ID);

            webPushService.onFriendshipRemoved(event);

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_REMOVED\"")
                    .contains("\"acting_user_id\":" + ACTING_USER_ID)
                    .contains("\"acting_user_name\":\"Alice Test\"");
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(null);

            webPushService.onFriendshipRemoved(new FriendshipRemovedEvent(ACTING_USER_ID, TARGET_USER_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnFriendshipAccepted {

        @Test
        void sends_FRIENDSHIP_ACCEPTED_to_addressee() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "Bob", "Test"));
            var event = new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID);

            webPushService.onFriendshipAccepted(event);

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_ACCEPTED\"")
                    .contains("\"addressee_id\":" + TARGET_USER_ID)
                    .contains("\"addressee_name\":\"Bob Test\"");
        }

        @Test
        void does_nothing_when_addressee_not_found() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(null);

            webPushService.onFriendshipAccepted(new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnFriendshipDeclined {

        @Test
        void sends_FRIENDSHIP_DECLINED_to_addressee() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "Bob", "Test"));
            var event = new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID);

            webPushService.onFriendshipDeclined(event);

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"FRIENDSHIP_DECLINED\"")
                    .contains("\"addressee_name\":\"Bob Test\"");
        }

        @Test
        void does_nothing_when_addressee_not_found() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(null);

            webPushService.onFriendshipDeclined(new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

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
        void sends_APPOINTMENT_MOVED_to_participants_allowed_by_settings() throws Exception {
            User actor       = user(ACTING_USER_ID, "Alice", "Test");
            User participant = user(TARGET_USER_ID, "Bob", "Participant");
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(actor);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentMovedNotification(any())).thenReturn(true);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_ID));

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"APPOINTMENT_MOVED\"")
                    .contains("\"acting_user_name\":\"Alice Test\"");
        }

        @Test
        void skips_participant_when_settings_disallow() {
            User actor       = user(ACTING_USER_ID, "Alice", "Test");
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(actor);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentMovedNotification(any())).thenReturn(false);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(null);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_appointment_not_found() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(user(ACTING_USER_ID, "A", "B"));
            when(appointmentQueryService.findById(APPOINTMENT_ID)).thenReturn(null);

            webPushService.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, Instant.now(), Instant.now(), ACTING_USER_ID));

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
            User actor       = user(ACTING_USER_ID, "Alice", "Test");
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(actor);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentCancelledNotification(any())).thenReturn(true);

            webPushService.onAppointmentCancelled(new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_ID));

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"APPOINTMENT_CANCELLED\"")
                    .contains("\"who_cancelled\":\"Alice Test\"");
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(null);

            webPushService.onAppointmentCancelled(new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class OnAppointmentReminder {

        @Test
        void sends_PARTICIPATION_REMINDER_to_all_participants_allowed_by_settings() throws Exception {
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Team Event"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentReminder(new AppointmentReminderEvent(APPOINTMENT_ID));

            assertThat(capturePayload(TARGET_USER_ID)).contains("\"type\":\"PARTICIPATION_REMINDER\"");
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
            User sender    = user(ACTING_USER_ID, "Alice", "Sender");
            User recipient = user(TARGET_USER_ID,  "Bob",  "Recipient");
            Appointment appt = appointment(APPOINTMENT_ID, "Team Meeting");
            Message message = new Message();
            message.setSender(sender);
            message.setAppointment(appt);
            message.setBody("Hello everyone!");

            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(message);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(recipient)));
            when(settingsService.sendAppointmentMessageSentNotification(any())).thenReturn(true);

            webPushService.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            String payload = capturePayload(TARGET_USER_ID);
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
            User newMember      = user(TARGET_USER_ID,  "Bob",   "New");
            User existingMember = user(ACTING_USER_ID, "Alice", "Existing");
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(newMember);
            when(groupQueryService.getGroupMembers(GROUP_ID)).thenReturn(List.of(groupMember(existingMember)));
            when(settingsService.sendGroupMemberAddedNotification(any())).thenReturn(true);

            webPushService.onGroupMemberAdded(new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            ArgumentCaptor<String> groupPayload = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).send(eq(ACTING_USER_ID), groupPayload.capture());
            assertThat(groupPayload.getValue()).contains("\"type\":\"NEW_GROUP_MEMBER\"");

            ArgumentCaptor<String> personalPayload = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).send(eq(TARGET_USER_ID), personalPayload.capture());
            assertThat(personalPayload.getValue()).contains("\"type\":\"ADDED_TO_GROUP\"");
        }

        @Test
        void does_nothing_when_new_member_not_found() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(null);

            webPushService.onGroupMemberAdded(new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_group_not_found() {
            when(userQueryService.findById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "Bob", "New"));
            when(groupQueryService.findById(GROUP_ID)).thenReturn(null);

            webPushService.onGroupMemberAdded(new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

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
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            assertThat(capturePayload(TARGET_USER_ID)).contains("\"type\":\"PARTICIPATION_STATUS_PENDING\"");
        }

        @Test
        void does_not_notify_participant_with_APPROVED_status() {
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            AppointmentParticipation ap = participation(participant);
            ap.setStatus(ParticipationStatus.APPROVED);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(ap));
            lenient().when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_not_notify_participant_with_REJECTED_status() {
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            AppointmentParticipation ap = participation(participant);
            ap.setStatus(ParticipationStatus.REJECTED);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(ap));
            lenient().when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_not_notify_pending_participant_when_settings_disallow() {
            User participant = user(TARGET_USER_ID, "Bob", "Test");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(participant)));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(false);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void only_notifies_pending_participants_when_mixed_statuses() {
            Long approvedUserId = 20L;
            Long rejectedUserId = 21L;
            User pendingUser  = user(TARGET_USER_ID, "Bob",     "Pending");
            User approvedUser = user(approvedUserId,  "Charlie", "Approved");
            User rejectedUser = user(rejectedUserId,  "Diana",   "Rejected");

            AppointmentParticipation pendingAp  = participation(pendingUser);
            AppointmentParticipation approvedAp = participation(approvedUser);
            approvedAp.setStatus(ParticipationStatus.APPROVED);
            AppointmentParticipation rejectedAp = participation(rejectedUser);
            rejectedAp.setStatus(ParticipationStatus.REJECTED);

            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(pendingAp, approvedAp, rejectedAp));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verify(notificationPort, times(1)).send(eq(TARGET_USER_ID), any());
            verify(notificationPort, never()).send(eq(approvedUserId), any());
            verify(notificationPort, never()).send(eq(rejectedUserId), any());
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
            User actor = user(ACTING_USER_ID, "Alice", "Test");
            User other = user(TARGET_USER_ID,  "Bob",   "Test");
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(actor);
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Test"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(other)));
            when(settingsService.sendAppointmentParticipationStatusChangedNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            String payload = capturePayload(TARGET_USER_ID);
            assertThat(payload)
                    .contains("\"type\":\"PARTICIPATION_STATUS_CHANGED\"")
                    .contains("\"new_participation_status\":\"APPROVED\"")
                    .contains("\"user_name\":\"Alice Test\"");
        }

        @Test
        void does_not_notify_acting_user_themselves() throws Exception {
            User actor = user(ACTING_USER_ID, "Alice", "Test");
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(actor);
            when(appointmentQueryService.findById(APPOINTMENT_ID))
                    .thenReturn(appointment(APPOINTMENT_ID, "Test"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":10}");
            // Actor is the only participant — the filter excludes them
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation(actor)));
            lenient().when(settingsService.sendAppointmentParticipationStatusChangedNotification(any())).thenReturn(true);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(notificationPort);
        }

        @Test
        void does_nothing_when_acting_user_not_found() {
            when(userQueryService.findById(ACTING_USER_ID)).thenReturn(null);

            webPushService.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID, ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(notificationPort);
        }
    }
}
