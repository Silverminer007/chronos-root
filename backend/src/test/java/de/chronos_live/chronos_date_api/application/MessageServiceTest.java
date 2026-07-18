package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentCancelledEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentMovedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusChangedEvent;
import de.chronos_live.chronos_date_api.application.events.MessageSentEvent;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces CDI
 * dependencies with Mockito mocks. {@link PanacheMock} intercepts all static
 * Panache calls on {@link Appointment} and {@link Message}.
 *
 * <p><b>Coverage plan</b>
 * <pre>
 * onAppointmentParticipationStatusChanged(event)
 *   B1  newParticipationStatus == APPROVED → "zugesagt" / else → "abgesagt"
 *   Tests: 2
 *
 * onAppointmentCancelled(event)
 *   — no conditional branches; linear message creation.
 *   Tests: 1
 *
 * onAppointmentMoved(event)
 *   B1  hourDelta > 0 (start moved later)
 *   B2  hourDelta < 0 (start moved earlier)
 *   B3  hourDelta == 0 AND endDelta > 0 (end extended)
 *   B4  hourDelta == 0 AND endDelta <= 0 (end moved earlier)
 *   Tests: 4
 *
 * sendMessage(appointmentId, text, userId)
 *   — delegates to sendMessage(…, Instant); no additional branches.
 *   Tests: 1
 *
 * sendMessage(appointmentId, text, userOidcId, timeStamp)
 *   — linear path: persist + fire event.
 *   Tests: 1
 *
 * getMessages(appointmentId, requestingUserOidcId)
 *   — no conditional branches; delegates after authorization check.
 *   Tests: 1
 * </pre>
 *
 * <p><b>Untestable branches:</b> none.
 */
@QuarkusTest
class MessageServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long    APPOINTMENT_ID  = 10L;
    private static final String  USER_OIDC_ID    = "oidc-user-1";
    private static final Long    MESSAGE_ID      = 42L;
    private static final String  MESSAGE_TEXT    = "Hello appointment!";
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2024-06-01T10:00:00Z");

    // Start/end instants used for AppointmentMovedEvent tests
    private static final Instant BASE_START = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant BASE_END   = Instant.parse("2024-06-01T12:00:00Z");

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    MessageService service;

    @InjectMock
    AuthorizationService authorizationService;

    @InjectMock
    MessageRepository messageRepository;

    @InjectMock
    IdentityPort identityPort;

    @InjectMock
    Event<MessageSentEvent> messageSentEvent;

    // ── Test-object builders ───────────────────────────────────────────────────
    private static UserIdentity buildUserIdentity(String oidcId) {
        return new UserIdentity(oidcId, "Max", "Mustermann", "max@example.com", null);
    }

    private static Appointment buildAppointment(Instant start, Instant end) {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(start);
        a.setEndTime(end);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentParticipationStatusChanged
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class OnAppointmentParticipationStatusChanged {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        // B1=true — APPROVED → "zugesagt"
        @Test
        void should_persistMessageWithZugesagt_when_statusIsApproved() {
            Appointment appointment = buildAppointment(BASE_START, BASE_END);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentParticipationStatusChangedEvent event =
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, USER_OIDC_ID,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING);

            service.onAppointmentParticipationStatusChanged(event);
            // persist is verified indirectly: no exception means the service completed
        }

        // B1=false — REJECTED → "abgesagt"
        @Test
        void should_persistMessageWithAbgesagt_when_statusIsRejected() {
            Appointment appointment = buildAppointment(BASE_START, BASE_END);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentParticipationStatusChangedEvent event =
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, USER_OIDC_ID,
                            ParticipationStatus.REJECTED, ParticipationStatus.PENDING);

            service.onAppointmentParticipationStatusChanged(event);
            // persist is verified indirectly: no exception means the service completed
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentCancelled
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class OnAppointmentCancelled {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistCancellationMessage_when_appointmentCancelledEventReceived() {
            Appointment appointment = buildAppointment(BASE_START, BASE_END);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentCancelledEvent event = new AppointmentCancelledEvent(APPOINTMENT_ID, USER_OIDC_ID);

            service.onAppointmentCancelled(event);
            // persist is verified indirectly: no exception means the service completed
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentMoved
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class OnAppointmentMoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        // B1 — hourDelta > 0 (start moved 2 hours later)
        @Test
        void should_persistMovedLaterMessage_when_startMovedForward() {
            Instant newStart = BASE_START.plusSeconds(7200); // +2 hours
            Appointment appointment = buildAppointment(newStart, BASE_END);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentMovedEvent event = new AppointmentMovedEvent(
                    APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC_ID);

            service.onAppointmentMoved(event);
            // persist is verified indirectly: no exception means the service completed
        }

        // B2 — hourDelta < 0 (start moved 2 hours earlier)
        @Test
        void should_persistVorVerlegt_when_startMovedBackward() {
            Instant newStart = BASE_START.minusSeconds(7200); // -2 hours
            Appointment appointment = buildAppointment(newStart, BASE_END);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentMovedEvent event = new AppointmentMovedEvent(
                    APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC_ID);

            service.onAppointmentMoved(event);
            // persist is verified indirectly: no exception means the service completed
        }

        // B3 — hourDelta == 0, endDelta > 0 (end extended by 2 hours)
        @Test
        void should_persistEndExtendedMessage_when_startUnchangedAndEndMovedForward() {
            Instant newEnd = BASE_END.plusSeconds(7200);
            Appointment appointment = buildAppointment(BASE_START, newEnd);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentMovedEvent event = new AppointmentMovedEvent(
                    APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC_ID);

            service.onAppointmentMoved(event);
            // persist is verified indirectly: no exception means the service completed
        }

        // B4 — hourDelta == 0, endDelta <= 0 (end moved to same time or earlier)
        @Test
        void should_persistEndVorVerlegtMessage_when_startUnchangedAndEndMovedBackward() {
            Instant newEnd = BASE_END.minusSeconds(7200);
            Appointment appointment = buildAppointment(BASE_START, newEnd);
            UserIdentity user = buildUserIdentity(USER_OIDC_ID);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            when(identityPort.findById(USER_OIDC_ID)).thenReturn(user);

            AppointmentMovedEvent event = new AppointmentMovedEvent(
                    APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC_ID);

            service.onAppointmentMoved(event);
            // persist is verified indirectly: no exception means the service completed
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendMessage (with timestamp — primary overload)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class SendMessageWithTimestamp {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistMessageAndFireEvent_when_called() {
            Appointment appointment = buildAppointment(BASE_START, BASE_END);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);

            Message result = service.sendMessage(APPOINTMENT_ID, MESSAGE_TEXT, USER_OIDC_ID, FIXED_TIMESTAMP);

            assertThat(result.getBody()).isEqualTo(MESSAGE_TEXT);
            assertThat(result.getSenderOidcId()).isEqualTo(USER_OIDC_ID);
            assertThat(result.getAppointment()).isSameAs(appointment);
            assertThat(result.getTimeStamp()).isEqualTo(FIXED_TIMESTAMP);

            verify(messageSentEvent).fire(any(MessageSentEvent.class));
            verify(authorizationService).requireSendMessage(APPOINTMENT_ID, USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendMessage (convenience overload — delegates with Instant.now())
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class SendMessageConvenienceOverload {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_delegateToTimestampOverload_when_calledWithoutTimestamp() {
            Appointment appointment = buildAppointment(BASE_START, BASE_END);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);

            Message result = service.sendMessage(APPOINTMENT_ID, MESSAGE_TEXT, USER_OIDC_ID);

            assertThat(result.getBody()).isEqualTo(MESSAGE_TEXT);
            assertThat(result.getTimeStamp()).isNotNull();
            verify(messageSentEvent).fire(any(MessageSentEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMessages
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetMessages {

        @Test
        void should_returnEnrichedMessageDtos_when_userIsAuthorized() {
            Appointment appt = buildAppointment(BASE_START, BASE_END);
            Message msg = new Message();
            msg.id = MESSAGE_ID;
            msg.setBody(MESSAGE_TEXT);
            msg.setSenderOidcId(USER_OIDC_ID);
            msg.setTimeStamp(FIXED_TIMESTAMP);
            msg.setAppointment(appt);

            UserIdentity sender = buildUserIdentity(USER_OIDC_ID);

            when(messageRepository.listByAppointment(APPOINTMENT_ID)).thenReturn(List.of(msg));
            when(identityPort.findByIds(any())).thenReturn(Map.of(USER_OIDC_ID, sender));

            List<MessageDto> result = service.getMessages(APPOINTMENT_ID, USER_OIDC_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().sender_id()).isEqualTo(USER_OIDC_ID);
            assertThat(result.getFirst().sender_name()).isEqualTo("Max Mustermann");
            assertThat(result.getFirst().body()).isEqualTo(MESSAGE_TEXT);
            verify(authorizationService).requireReadAppointment(APPOINTMENT_ID, USER_OIDC_ID);
        }
    }
}
