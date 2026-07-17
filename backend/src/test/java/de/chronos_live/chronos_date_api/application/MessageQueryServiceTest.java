package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageQueryService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@link PanacheMock} intercepts every
 * static Panache call on {@link Message} so no real database is ever touched.
 *
 * <p><b>Coverage plan</b>
 * <pre>
 * getMessages(appointmentId)
 *   — no conditional branches; delegates directly to Panache.
 *   Tests: 2 (non-empty list, empty list)
 *
 * getMessage(messageId)
 *   B1  Optional.isPresent() → true (return message) / false (throw)
 *   Tests: 2
 * </pre>
 *
 * <p><b>Untestable branches:</b> none.
 */
@QuarkusTest
class MessageQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   APPOINTMENT_ID  = 10L;
    private static final Long   MESSAGE_ID      = 42L;
    private static final Long   UNKNOWN_ID      = 999L;
    private static final String SENDER_OIDC     = "oidc-user-1";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    MessageQueryService service;

    // ── Test-object builders ───────────────────────────────────────────────────
    private static Message buildMessage() {
        Appointment appointment = new Appointment();
        appointment.id = APPOINTMENT_ID;

        Message m = new Message();
        m.id = MESSAGE_ID;
        m.setBody("Hello!");
        m.setSenderOidcId(SENDER_OIDC);
        m.setAppointment(appointment);
        m.setTimeStamp(Instant.parse("2024-06-01T10:00:00Z"));
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMessages
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetMessages {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Message.class);
        }

        @Test
        void should_returnMessages_when_messagesExistForAppointment() {
            Message msg = buildMessage();
            @SuppressWarnings("unchecked") PanacheQuery<Message> q = mock(PanacheQuery.class);
            when(Message.<Message>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of(msg));

            List<Message> result = service.getMessages(APPOINTMENT_ID);

            assertThat(result).containsExactly(msg);

            // Verify the query contains the appointment ID filter and ordering
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            ArgumentCaptor<String>   sqlCaptor    = ArgumentCaptor.forClass(String.class);
            PanacheMock.verify(Message.class).<Message>find(sqlCaptor.capture(), paramsCaptor.capture());
            assertThat(sqlCaptor.getValue()).contains("appointment.id = ?1");
            assertThat(paramsCaptor.getValue()[0]).isEqualTo(APPOINTMENT_ID);
        }

        @Test
        void should_returnEmptyList_when_noMessagesExistForAppointment() {
            @SuppressWarnings("unchecked") PanacheQuery<Message> q = mock(PanacheQuery.class);
            when(Message.<Message>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.list()).thenReturn(List.of());

            List<Message> result = service.getMessages(APPOINTMENT_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMessage
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetMessage {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Message.class);
        }

        // B1=true — message found
        @Test
        void should_returnMessage_when_messageExists() {
            Message expected = buildMessage();
            @SuppressWarnings("unchecked") PanacheQuery<Message> q = mock(PanacheQuery.class);
            when(Message.<Message>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(expected));

            Message result = service.getMessage(MESSAGE_ID);

            assertThat(result).isSameAs(expected);
        }

        // B1=false — message not found → exception
        @Test
        void should_throwResourceNotFoundException_when_messageDoesNotExist() {
            @SuppressWarnings("unchecked") PanacheQuery<Message> q = mock(PanacheQuery.class);
            when(Message.<Message>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMessage(UNKNOWN_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("message mit ID " + UNKNOWN_ID + " wurde nicht gefunden");
        }
    }
}
