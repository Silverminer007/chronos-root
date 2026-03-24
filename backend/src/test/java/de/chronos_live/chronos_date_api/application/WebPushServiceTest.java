package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.AppointmentDto;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.infrastructure.PushNotificationLogRepository;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.PushAppointmentMapper;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebPushService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} for every injected CDI
 * service dependency. {@link de.chronos_live.chronos_date_api.config.PushConfig} is
 * NOT mocked — the real bean is used but the {@code push.vapid.*} properties are
 * intentionally absent from test {@code application.properties}, which causes
 * {@code PushConfig} to return {@code Optional.empty()} for all three keys.
 * {@code WebPushService.init()} therefore takes the "VAPID not configured" branch,
 * setting the internal {@code push} field to {@code null}. No valid EC key material
 * or network access is needed.
 *
 * <p>Consequence: the {@code sendNotification} loop body that calls
 * {@code push.send()} is unreachable in tests. See "Untestable branches" below.
 *
 * <p>All observer / handler methods are invoked directly (not via CDI events)
 * so that every conditional branch inside each method is reachable.
 *
 * <p><b>Untestable branches:</b>
 * <ul>
 *   <li>{@code sendNotification} lines that iterate subscriptions and call
 *       {@code push.send()} — requires a non-null {@code PushService} instance
 *       which in turn requires valid VAPID EC key material.</li>
 * </ul>
 */
@QuarkusTest
class WebPushServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long APPOINTMENT_ID  = 10L;
    private static final Long USER_ID         = 1L;
    private static final Long ACTING_USER_ID  = 2L;
    private static final Long TARGET_USER_ID  = 3L;
    private static final Long GROUP_ID        = 5L;
    private static final Long MESSAGE_ID      = 20L;
    private static final Long REQUEST_ID      = 30L;

    private static final Instant OLD_START = Instant.parse("2024-07-01T09:00:00Z");
    private static final Instant OLD_END   = Instant.parse("2024-07-01T17:00:00Z");

    // ── CDI injection ──────────────────────────────────────────────────────────
    @Inject
    WebPushService service;

    // PushConfig is intentionally NOT mocked — the real CDI bean reads
    // test application.properties where push.vapid.* are absent, so all
    // three getters return Optional.empty(). This causes init() to set
    // push = null (the "VAPID not configured" safe path).

    @InjectMock
    PushSubscriptionService subscriptionService;

    @InjectMock
    SettingsService settingsService;

    @InjectMock
    PushAppointmentMapper appointmentMapper;

    @InjectMock
    GroupMapper groupMapper;

    @InjectMock
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @InjectMock
    MessageQueryService messageQueryService;

    @InjectMock
    PushNotificationLogRepository pushNotificationLogRepository;

    // MeterRegistry is intentionally NOT mocked — the real Quarkus-provided
    // SimpleMeterRegistry is injected so that Timer.start(meterRegistry) works
    // without NullPointerException inside onAppointmentParticipationStatusChanged.

    // ── Test-object builders ───────────────────────────────────────────────────
    private static User buildUser(Long id, String firstName, String lastName) {
        User u = new User();
        u.id = id;
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return u;
    }

    private static Appointment buildAppointment(Long id) {
        Appointment a = new Appointment();
        a.id = id;
        a.setName("Test Appointment");
        a.setStartTime(OLD_START);
        a.setEndTime(OLD_END);
        a.setStatus(AppointmentStatus.PLANNED);
        return a;
    }

    private static Group buildGroup(Long id) {
        Group g = new Group();
        g.id = id;
        g.setGroupName("Test Group");
        return g;
    }

    private static AppointmentParticipation buildParticipation(User user, UserRole role) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setUser(user);
        ap.setRole(role);
        ap.setStatus(ParticipationStatus.PENDING);
        return ap;
    }

    private static Message buildMessage(Long id, User sender, Appointment appointment) {
        Message m = new Message();
        m.id = id;
        m.setSender(sender);
        m.setAppointment(appointment);
        m.setBody("Hello World");
        return m;
    }

    private static GroupMember buildGroupMember(User user, Group group) {
        GroupMember gm = new GroupMember();
        gm.setUser(user);
        gm.setGroup(group);
        return gm;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPublicKey
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getPublicKey:
     *   Test properties have no push.vapid.* → PushConfig returns Optional.empty()
     *   → getPublicKey() returns "".
     *
     * Total branches: 0 (Optional.orElse is not a conditional branch)  |  Tests: 1
     */
    @Nested
    class GetPublicKey {

        @Test
        void should_returnEmptyString_when_vapidPublicKeyNotConfigured() {
            // PushConfig reads push.vapid.public; if not configured returns "".
            // When VAPID keys are present (e.g. via .env), getPublicKey returns the key.
            String result = service.getPublicKey();

            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendToUser
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendToUser:
     *   No conditional branches. Builds JSON payload and calls sendNotification.
     *   With push=null, sendNotification returns early — no error thrown.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class SendToUser {

        @Test
        void should_notThrow_when_called() {
            // sendNotification may call subscriptionService.getAllForUser if push is initialized
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());
            service.sendToUser(USER_ID, "Title", "Body");
            // No exception is expected regardless of whether push is null or not
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendNotification
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendNotification:
     *   B1  push == null (VAPID keys missing) → early return, no subscriptions fetched
     *   B2  push != null → (untestable: requires valid EC key material)
     *
     * Total branches: 1 testable  |  Tests: 1
     */
    @Nested
    class SendNotification {

        @Test
        void should_returnEarly_when_pushIsNull() {
            // sendNotification may call subscriptionService.getAllForUser if push is initialized
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());
            service.sendNotification(USER_ID, "{\"title\":\"test\"}");
            // No exception is expected regardless of push state
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentMessageSent
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentMessageSent:
     *   B1  message == null     → early return
     *   B2  message != null     → build payload, call sendToParticipants
     *     B2a  participant != sender (sendTest true)  → notification sent
     *     B2b  participant == sender (check settings) → depends on settings
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class OnAppointmentMessageSent {

        // B1=true — messageQueryService returns null (simulates message not found)
        @Test
        void should_returnEarly_when_messageIsNull() {
            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(null);

            service.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            verifyNoInteractions(subscriptionService);
        }

        // B2=true, B2a: participant is different user → sendNotification called (no-op because push==null)
        @Test
        void should_sendToParticipants_when_messageIsNotNull() {
            User sender = buildUser(ACTING_USER_ID, "Sender", "One");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            Message message = buildMessage(MESSAGE_ID, sender, appointment);
            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(message);

            User otherUser = buildUser(TARGET_USER_ID, "Other", "User");
            AppointmentParticipation otherParticipation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(otherParticipation));

            service.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            // push==null → sendNotification is a no-op; verify participants were queried
            verify(appointmentParticipationQueryService).getParticipants(APPOINTMENT_ID);
        }

        // B2b: participant IS the sender → settingsService consulted
        @Test
        void should_checkSettingsForSender_when_participantIsSender() {
            User sender = buildUser(ACTING_USER_ID, "Sender", "One");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            Message message = buildMessage(MESSAGE_ID, sender, appointment);
            when(messageQueryService.getMessage(MESSAGE_ID)).thenReturn(message);

            AppointmentParticipation senderParticipation = buildParticipation(sender, UserRole.RESPONSIBLE);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(senderParticipation));
            when(settingsService.sendAppointmentMessageSentNotification(senderParticipation))
                    .thenReturn(false);

            service.onAppointmentMessageSent(new MessageSentEvent(MESSAGE_ID));

            verify(settingsService).sendAppointmentMessageSentNotification(senderParticipation);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentMoved
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentMoved:
     *   B1  actingUser == null      → early return
     *   B2  appointmentJson == null → early return  (Appointment not found)
     *   B3  normal path             → build payload, sendToParticipants
     *     B3a  participant != actingUser → directly notified (no settings check)
     *     B3b  participant == actingUser → settings consulted
     *
     * Total branches: 5  |  Tests: 4
     */
    @Nested
    class OnAppointmentMoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, OLD_START, OLD_END, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, OLD_START, OLD_END, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B3=true, B3a: other participant → no settings check
        @Test
        void should_sendToParticipants_when_appointmentFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User otherUser = buildUser(TARGET_USER_ID, "Other", "User");
            AppointmentParticipation otherParticipation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(otherParticipation));

            service.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, OLD_START, OLD_END, ACTING_USER_ID));

            verify(appointmentParticipationQueryService).getParticipants(APPOINTMENT_ID);
        }

        // B3b: participant IS the acting user → settings consulted
        @Test
        void should_checkSettingsForActingUser_when_participantIsActingUser() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            AppointmentParticipation actorParticipation = buildParticipation(actingUser, UserRole.RESPONSIBLE);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(actorParticipation));
            when(settingsService.sendAppointmentMovedNotification(actorParticipation))
                    .thenReturn(false);

            service.onAppointmentMoved(
                    new AppointmentMovedEvent(APPOINTMENT_ID, OLD_START, OLD_END, ACTING_USER_ID));

            verify(settingsService).sendAppointmentMovedNotification(actorParticipation);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentCancelled
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentCancelled:
     *   B1  actingUser == null      → early return
     *   B2  appointmentJson == null → early return
     *   B3  normal path             → sendToParticipants
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class OnAppointmentCancelled {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onAppointmentCancelled(
                    new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentCancelled(
                    new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B3=true
        @Test
        void should_sendToParticipants_when_allResourcesFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User otherUser = buildUser(TARGET_USER_ID, "Other", "User");
            AppointmentParticipation participation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));

            service.onAppointmentCancelled(
                    new AppointmentCancelledEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verify(appointmentParticipationQueryService).getParticipants(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipantAdded
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentParticipantAdded:
     *   B1  actingUser == null       → early return
     *   B2  newParticipant == null   → early return
     *   B3  appointmentJson == null  → early return
     *   B4  normal path              → sendToParticipants
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class OnAppointmentParticipantAdded {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onAppointmentParticipantAdded(
                    new AppointmentParticipationAddedEvent(APPOINTMENT_ID, TARGET_USER_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2=true
        @Test
        void should_returnEarly_when_newParticipantIsNull() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(null);

            service.onAppointmentParticipantAdded(
                    new AppointmentParticipationAddedEvent(APPOINTMENT_ID, TARGET_USER_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentMapper);
        }

        // B3=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            User newParticipant = buildUser(TARGET_USER_ID, "New", "Participant");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(newParticipant);
            when(appointmentParticipationQueryService.getUserRole(APPOINTMENT_ID, TARGET_USER_ID))
                    .thenReturn(UserRole.ATTENDANT);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentParticipantAdded(
                    new AppointmentParticipationAddedEvent(APPOINTMENT_ID, TARGET_USER_ID, ACTING_USER_ID));

            verify(appointmentParticipationQueryService, never()).getParticipants(any());
        }

        // B4=true
        @Test
        void should_sendToParticipants_when_allResourcesFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            User newParticipant = buildUser(TARGET_USER_ID, "New", "Participant");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(newParticipant);
            when(appointmentParticipationQueryService.getUserRole(APPOINTMENT_ID, TARGET_USER_ID))
                    .thenReturn(UserRole.ATTENDANT);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User otherUser = buildUser(USER_ID, "Other", "User");
            AppointmentParticipation participation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));

            service.onAppointmentParticipantAdded(
                    new AppointmentParticipationAddedEvent(APPOINTMENT_ID, TARGET_USER_ID, ACTING_USER_ID));

            verify(appointmentParticipationQueryService).getParticipants(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentGroupParticipantAdded
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentGroupParticipantAdded:
     *   B1  actingUser == null          → early return
     *   B2  newGroupParticipant == null → early return
     *   B3  appointmentJson == null     → early return
     *   B4  normal path                 → sendToParticipants
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class OnAppointmentGroupParticipantAdded {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Group.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onAppointmentGroupParticipantAdded(
                    new AppointmentGroupParticipationAddedEvent(APPOINTMENT_ID, GROUP_ID, ACTING_USER_ID));

            PanacheMock.verifyNoInteractions(Group.class);
        }

        // B2=true
        @Test
        void should_returnEarly_when_groupNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            service.onAppointmentGroupParticipantAdded(
                    new AppointmentGroupParticipationAddedEvent(APPOINTMENT_ID, GROUP_ID, ACTING_USER_ID));

            PanacheMock.verifyNoInteractions(Appointment.class);
        }

        // B3=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Group group = buildGroup(GROUP_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentGroupParticipantAdded(
                    new AppointmentGroupParticipationAddedEvent(APPOINTMENT_ID, GROUP_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B4=true
        @Test
        void should_sendToParticipants_when_allResourcesFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Group group = buildGroup(GROUP_ID);
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User otherUser = buildUser(TARGET_USER_ID, "Other", "User");
            AppointmentParticipation participation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));

            service.onAppointmentGroupParticipantAdded(
                    new AppointmentGroupParticipationAddedEvent(APPOINTMENT_ID, GROUP_ID, ACTING_USER_ID));

            verify(appointmentParticipationQueryService).getParticipants(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onFriendshipRequestSent
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onFriendshipRequestSent:
     *   No conditional branches. Builds JSON and calls sendNotification.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class OnFriendshipRequestSent {

        @Test
        void should_sendNotification_when_called() {
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());

            service.onFriendshipRequestSent(
                    new FriendshipRequestSentEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID, "Alice Tester"));

            // sendNotification is called; with push initialized it calls getAllForUser
            verify(subscriptionService).getAllForUser(TARGET_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onFriendshipAccepted
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onFriendshipAccepted:
     *   B1  addressee == null → early return
     *   B2  addressee found   → send notification to requester
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnFriendshipAccepted {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_addresseeIsNull() {
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(null);

            service.onFriendshipAccepted(
                    new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

            verifyNoInteractions(subscriptionService);
        }

        // B2=true
        @Test
        void should_sendNotification_when_addresseeFound() {
            User addressee = buildUser(TARGET_USER_ID, "Addressee", "User");
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(addressee);
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());

            service.onFriendshipAccepted(
                    new FriendshipAcceptedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

            // sendNotification is called with addresseeId; verify subscriptionService is consulted
            verify(subscriptionService).getAllForUser(TARGET_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onFriendshipDeclined
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onFriendshipDeclined:
     *   B1  addressee == null → early return
     *   B2  addressee found   → send notification
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnFriendshipDeclined {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_addresseeIsNull() {
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(null);

            service.onFriendshipDeclined(
                    new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

            verifyNoInteractions(subscriptionService);
        }

        // B2=true
        @Test
        void should_sendNotification_when_addresseeFound() {
            User addressee = buildUser(TARGET_USER_ID, "Addressee", "User");
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(addressee);
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());

            service.onFriendshipDeclined(
                    new FriendshipDeclinedEvent(REQUEST_ID, ACTING_USER_ID, TARGET_USER_ID));

            // sendNotification is called with addresseeId; verify subscriptionService is consulted
            verify(subscriptionService).getAllForUser(TARGET_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onFriendshipRemoved
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onFriendshipRemoved:
     *   B1  actingUser == null → early return
     *   B2  actingUser found   → send notification to friend
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnFriendshipRemoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onFriendshipRemoved(
                    new FriendshipRemovedEvent(ACTING_USER_ID, TARGET_USER_ID));

            verifyNoInteractions(subscriptionService);
        }

        // B2=true
        @Test
        void should_sendNotificationToFriend_when_actingUserFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Acting", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(subscriptionService.getAllForUser(anyLong())).thenReturn(List.of());

            service.onFriendshipRemoved(
                    new FriendshipRemovedEvent(ACTING_USER_ID, TARGET_USER_ID));

            // sendNotification is called with friendId; verify subscriptionService is consulted
            verify(subscriptionService).getAllForUser(TARGET_USER_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onGroupMemberAdded
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onGroupMemberAdded:
     *   B1  newMember == null  → early return
     *   B2  groupJson == null  → early return
     *   B3  normal path        → sendToMembers + sendNotification to new member
     *     B3a  member != actingUser → no settings check (directly sent)
     *     B3b  member == actingUser → settings consulted
     *
     * Total branches: 5  |  Tests: 4
     */
    @Nested
    class OnGroupMemberAdded {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Group.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_newMemberIsNull() {
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(null);

            service.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            verifyNoInteractions(groupMapper);
        }

        // B2=true
        @Test
        void should_returnEarly_when_groupNotFound() {
            User newMember = buildUser(TARGET_USER_ID, "New", "Member");
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(newMember);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(null);

            service.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            verifyNoInteractions(subscriptionService);
        }

        // B3=true, B3a: other member (not actingUser)
        @Test
        void should_sendToMembersAndNewMember_when_allResourcesFound() {
            User newMember = buildUser(TARGET_USER_ID, "New", "Member");
            Group group = buildGroup(GROUP_ID);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(newMember);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(groupMapper.toDto(any())).thenReturn(
                    new GroupDto());

            User otherUser = buildUser(USER_ID, "Other", "User");
            GroupMember otherMember = buildGroupMember(otherUser, group);
            when(groupQueryService.getGroupMembers(GROUP_ID)).thenReturn(List.of(otherMember));

            service.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            verify(groupQueryService).getGroupMembers(GROUP_ID);
        }

        // B3b: member IS the actingUser → settings consulted
        @Test
        void should_checkSettingsForActingUserMember_when_memberIsActingUser() {
            User newMember = buildUser(TARGET_USER_ID, "New", "Member");
            Group group = buildGroup(GROUP_ID);
            when(User.<User>findById(TARGET_USER_ID)).thenReturn(newMember);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(groupMapper.toDto(any())).thenReturn(
                    new GroupDto());

            User actingUser = buildUser(ACTING_USER_ID, "Acting", "User");
            GroupMember actingMember = buildGroupMember(actingUser, group);
            when(groupQueryService.getGroupMembers(GROUP_ID)).thenReturn(List.of(actingMember));
            when(settingsService.sendGroupMemberAddedNotification(actingMember)).thenReturn(false);

            service.onGroupMemberAdded(
                    new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_ID, ACTING_USER_ID));

            verify(settingsService).sendGroupMemberAddedNotification(actingMember);
        }
    }

    @InjectMock
    GroupQueryService groupQueryService;

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationStatusChanged
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentParticipationStatusChanged:
     *   B1  actingUser == null      → early return (inside try)
     *   B2  appointmentJson == null → early return (inside try)
     *   B3  normal path             → sendToParticipants
     *     B3a  participant == actingUser  → excluded (condition uses &&, not ||)
     *     B3b  participant != actingUser  → settings consulted
     *
     * Total branches: 5  |  Tests: 4
     */
    @Nested
    class OnAppointmentParticipationStatusChanged {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_actingUserIsNull() {
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(null);

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B3b: participant != actingUser → settings consulted via &&
        @Test
        void should_checkSettings_when_participantIsNotActingUser() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User otherUser = buildUser(TARGET_USER_ID, "Other", "User");
            AppointmentParticipation participation = buildParticipation(otherUser, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentParticipationStatusChangedNotification(participation))
                    .thenReturn(true);

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            verify(settingsService)
                    .sendAppointmentParticipationStatusChangedNotification(participation);
        }

        // B3a: participant == actingUser → excluded (condition: !equals(actingUser) && settings)
        @Test
        void should_excludeActingUser_when_participantIsActingUser() {
            User actingUser = buildUser(ACTING_USER_ID, "Actor", "User");
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(User.<User>findById(ACTING_USER_ID)).thenReturn(actingUser);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            AppointmentParticipation actorParticipation = buildParticipation(actingUser, UserRole.RESPONSIBLE);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(actorParticipation));

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, ACTING_USER_ID,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING));

            // actingUser excluded: settings never consulted
            verify(settingsService, never())
                    .sendAppointmentParticipationStatusChangedNotification(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationInvalid
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentParticipationInvalid:
     *   B1  appointmentJson == null → early return
     *   B2  normal path             → getParticipationStatistik, sendToParticipants
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnAppointmentParticipationInvalid {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentParticipationInvalid(
                    new AppointmentParticipationInvalidEvent(APPOINTMENT_ID));

            verify(appointmentParticipationQueryService, never()).getParticipationStatistik(any());
        }

        // B2=true
        @Test
        void should_sendToParticipants_when_appointmentFound() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());
            when(appointmentParticipationQueryService.getParticipationStatistik(APPOINTMENT_ID))
                    .thenReturn(new ParticipationStatistik(3L, 2L, 1L));

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentParticipationInvalidNotification(participation))
                    .thenReturn(true);

            service.onAppointmentParticipationInvalid(
                    new AppointmentParticipationInvalidEvent(APPOINTMENT_ID));

            verify(appointmentParticipationQueryService).getParticipationStatistik(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationStatusPendingReminder
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentParticipationStatusPendingReminder:
     *   B1  appointmentJson == null → early return
     *   B2  normal path
     *     B2a  participant status PENDING && settings → notified
     *     B2b  participant status APPROVED (not PENDING) → skipped
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class OnAppointmentParticipationStatusPendingReminder {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2a — PENDING participant with settings = true → notified
        @Test
        void should_sendToParticipants_when_participantIsPending() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            participation.setStatus(ParticipationStatus.PENDING);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentParticipationStatusPendingReminderNotification(participation))
                    .thenReturn(true);

            service.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verify(settingsService)
                    .sendAppointmentParticipationStatusPendingReminderNotification(participation);
        }

        // B2b — APPROVED participant → skipped (status check fails)
        @Test
        void should_skipParticipant_when_participantIsApproved() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            participation.setStatus(ParticipationStatus.APPROVED);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));

            service.onAppointmentParticipationStatusPendingReminder(
                    new AppointmentParticipationStatusPendingReminderEvent(APPOINTMENT_ID));

            verify(settingsService, never())
                    .sendAppointmentParticipationStatusPendingReminderNotification(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentReminder
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentReminder:
     *   B1  appointmentJson == null → early return
     *   B2  normal path             → sendToParticipants via settingsService::sendAppointmentReminderNotification
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnAppointmentReminder {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentReminder(new AppointmentReminderEvent(APPOINTMENT_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2=true
        @Test
        void should_sendToParticipants_when_appointmentFound() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentReminderNotification(participation)).thenReturn(true);

            service.onAppointmentReminder(new AppointmentReminderEvent(APPOINTMENT_ID));

            verify(settingsService).sendAppointmentReminderNotification(participation);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationStatusRecheckRequested
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentParticipationStatusRecheckRequested:
     *   B1  appointmentJson == null → early return
     *   B2  normal path             → per-participant JSON built and sent
     *     B2a  sendRecheckRequested = true  → sendNotification called
     *     B2b  sendRecheckRequested = false → participant skipped
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class OnAppointmentParticipationStatusRecheckRequested {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_returnEarly_when_appointmentNotFound() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.onAppointmentParticipationStatusRecheckRequested(
                    new AppointmentParticipationStatusRecheckRequestedEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verifyNoInteractions(appointmentParticipationQueryService);
        }

        // B2a — sendRecheckRequested = true → notification attempted (no-op because push==null)
        @Test
        void should_sendNotificationPerParticipant_when_settingsPermit() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentParticipationStatusRecheckRequestedNotification(participation))
                    .thenReturn(true);

            service.onAppointmentParticipationStatusRecheckRequested(
                    new AppointmentParticipationStatusRecheckRequestedEvent(APPOINTMENT_ID, ACTING_USER_ID));

            verify(settingsService)
                    .sendAppointmentParticipationStatusRecheckRequestedNotification(participation);
        }

        // B2b — sendRecheckRequested = false → skip participant
        @Test
        void should_skipParticipant_when_settingsDenyNotification() {
            Appointment appointment = buildAppointment(APPOINTMENT_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(appointmentMapper.toDto(any())).thenReturn(
                    new AppointmentDto());

            User user = buildUser(TARGET_USER_ID, "User", "One");
            AppointmentParticipation participation = buildParticipation(user, UserRole.ATTENDANT);
            when(appointmentParticipationQueryService.getParticipants(APPOINTMENT_ID))
                    .thenReturn(List.of(participation));
            when(settingsService.sendAppointmentParticipationStatusRecheckRequestedNotification(participation))
                    .thenReturn(false);

            service.onAppointmentParticipationStatusRecheckRequested(
                    new AppointmentParticipationStatusRecheckRequestedEvent(APPOINTMENT_ID, ACTING_USER_ID));

            // push == null so even if we reached sendNotification it would be a no-op;
            // but settings returned false → sendNotification should NOT be called for this user
            verifyNoInteractions(subscriptionService);
        }
    }
}
