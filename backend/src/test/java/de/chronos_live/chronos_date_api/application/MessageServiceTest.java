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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageService}.
 *
 * <p>User identity is now read via {@link IdentityPort}, not from the (deleted)
 * {@code User} entity. {@link PanacheMock} still intercepts {@link Appointment}
 * and {@link Message} static calls.
 */
@QuarkusTest
class MessageServiceTest {

    private static final Long    APPOINTMENT_ID  = 10L;
    private static final String  USER_OIDC       = "oidc-user-1";
    private static final String  MESSAGE_TEXT    = "Hello appointment!";
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant BASE_START      = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant BASE_END        = Instant.parse("2024-06-01T12:00:00Z");

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

    private static UserIdentity buildUserIdentity() {
        return new UserIdentity(USER_OIDC, "Max", "Mustermann", "max@example.com", null);
    }

    private static Appointment buildAppointment(Instant start, Instant end) {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setStartTime(start);
        a.setEndTime(end);
        return a;
    }

    @Nested
    class OnAppointmentParticipationStatusChanged {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistMessageWithZugesagt_when_statusIsApproved() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, BASE_END));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, USER_OIDC,
                            ParticipationStatus.APPROVED, ParticipationStatus.PENDING));
        }

        @Test
        void should_persistMessageWithAbgesagt_when_statusIsRejected() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, BASE_END));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentParticipationStatusChanged(
                    new AppointmentParticipationStatusChangedEvent(
                            APPOINTMENT_ID, USER_OIDC,
                            ParticipationStatus.REJECTED, ParticipationStatus.PENDING));
        }
    }

    @Nested
    class OnAppointmentCancelled {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistCancellationMessage_when_appointmentCancelledEventReceived() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, BASE_END));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentCancelled(new AppointmentCancelledEvent(APPOINTMENT_ID, USER_OIDC));
        }
    }

    @Nested
    class OnAppointmentMoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistMovedLaterMessage_when_startMovedForward() {
            Instant newStart = BASE_START.plusSeconds(7200);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(newStart, BASE_END));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentMoved(new AppointmentMovedEvent(APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC));
        }

        @Test
        void should_persistVorVerlegt_when_startMovedBackward() {
            Instant newStart = BASE_START.minusSeconds(7200);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(newStart, BASE_END));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentMoved(new AppointmentMovedEvent(APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC));
        }

        @Test
        void should_persistEndExtendedMessage_when_startUnchangedAndEndMovedForward() {
            Instant newEnd = BASE_END.plusSeconds(7200);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, newEnd));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentMoved(new AppointmentMovedEvent(APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC));
        }

        @Test
        void should_persistEndVorVerlegtMessage_when_startUnchangedAndEndMovedBackward() {
            Instant newEnd = BASE_END.minusSeconds(7200);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, newEnd));
            when(identityPort.findById(USER_OIDC)).thenReturn(buildUserIdentity());

            service.onAppointmentMoved(new AppointmentMovedEvent(APPOINTMENT_ID, BASE_START, BASE_END, USER_OIDC));
        }
    }

    @Nested
    class SendMessage {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_persistMessageAndFireEvent_when_called() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, BASE_END));

            Message result = service.sendMessage(APPOINTMENT_ID, MESSAGE_TEXT, USER_OIDC, FIXED_TIMESTAMP);

            assertThat(result.getBody()).isEqualTo(MESSAGE_TEXT);
            assertThat(result.getSenderOidcId()).isEqualTo(USER_OIDC);
            assertThat(result.getAppointment()).isNotNull();
            assertThat(result.getTimeStamp()).isEqualTo(FIXED_TIMESTAMP);
            verify(messageSentEvent).fire(any(MessageSentEvent.class));
            verify(authorizationService).requireSendMessage(APPOINTMENT_ID, USER_OIDC);
        }

        @Test
        void should_delegateToTimestampOverload_when_calledWithoutTimestamp() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment(BASE_START, BASE_END));

            Message result = service.sendMessage(APPOINTMENT_ID, MESSAGE_TEXT, USER_OIDC);

            assertThat(result.getBody()).isEqualTo(MESSAGE_TEXT);
            assertThat(result.getTimeStamp()).isNotNull();
            verify(messageSentEvent).fire(any(MessageSentEvent.class));
        }
    }

    @Nested
    class GetMessages {

        @Test
        void should_returnEnrichedMessageDtos_when_userIsAuthorized() {
            Message msg = new Message();
            msg.id = MESSAGE_ID;
            msg.setBody(MESSAGE_TEXT);
            msg.setSenderOidcId(USER_OIDC_ID);
            msg.setTimeStamp(FIXED_TIMESTAMP);

            List<Message> result = service.getMessages(APPOINTMENT_ID, USER_OIDC);

            assertThat(result).isSameAs(expected);
            verify(authorizationService).requireReadAppointment(APPOINTMENT_ID, USER_OIDC);
        }
    }
}
