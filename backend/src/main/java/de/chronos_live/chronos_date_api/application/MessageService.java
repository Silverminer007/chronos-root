package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentCancelledEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentMovedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusChangedEvent;
import de.chronos_live.chronos_date_api.application.events.MessageSentEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
import de.chronos_live.chronos_date_api.mapper.MessageMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Transactional
@Timed("service.message")
public class MessageService {
    private static final Logger LOGGER = Logger.getLogger(MessageService.class);
    @Inject
    AuthorizationService authorizationService;
    @Inject
    IdentityPort identityPort;
    @Inject
    AppointmentRepository appointmentRepository;
    @Inject
    MessageRepository messageRepository;
    @Inject
    MessageMapper messageMapper;
    @Inject
    Event<MessageSentEvent> messageSentEvent;
    @Inject
    MeterRegistry meterRegistry;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusChanged(@ObservesAsync AppointmentParticipationStatusChangedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Appointment appointment = appointmentRepository.findById(event.appointmentId());
            UserIdentity user = identityPort.findById(event.actingUserOidcId());
            String text = "%s hat %s".formatted(user.getName(),
                    event.newParticipationStatus().equals(ParticipationStatus.APPROVED) ? "zugesagt" : "abgesagt");
            persistMessage(text, event.actingUserOidcId(), appointment);
        } finally {
            sample.stop(Timer.builder("observer.message.onParticipationStatusChanged")
                    .description("Time for message creation on participation status change")
                    .register(meterRegistry));
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentCancelled(@Observes AppointmentCancelledEvent event) {
        Appointment appointment = appointmentRepository.findById(event.cancelledAppointmentId());
        UserIdentity user = identityPort.findById(event.actingUserOidcId());
        String text = "%s hat diesen Termin abgesagt. Er wird nicht stattfinden!".formatted(user.getName());
        persistMessage(text, event.actingUserOidcId(), appointment);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentMoved(@Observes AppointmentMovedEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId());
        UserIdentity user = identityPort.findById(event.actingUserOidcId());
        Optional<String> text = movedText(user.getName(), event.oldStartTime(), appointment.getStartTime(),
                event.oldEndTime(), appointment.getEndTime());
        if (text.isEmpty()) {
            return;
        }
        persistMessage(text.get(), event.actingUserOidcId(), appointment);
    }

    /**
     * Builds the system message for a moved appointment. The start time takes precedence; the end time is only
     * considered if the start is unchanged. Returns empty if neither changed by at least one minute.
     */
    static Optional<String> movedText(String userName, Instant oldStart, Instant newStart,
                                      Instant oldEnd, Instant newEnd) {
        long startMinutes = oldStart.until(newStart, ChronoUnit.MINUTES);
        if (startMinutes != 0) {
            return Optional.of("%s hat diesen Termin um %s %s".formatted(
                    userName, formatDuration(Math.abs(startMinutes)), direction(startMinutes)));
        }
        long endMinutes = oldEnd.until(newEnd, ChronoUnit.MINUTES);
        if (endMinutes != 0) {
            return Optional.of("%s hat das Ende dieses Termins um %s %s".formatted(
                    userName, formatDuration(Math.abs(endMinutes)), direction(endMinutes)));
        }
        return Optional.empty();
    }

    private static String direction(long deltaMinutes) {
        return deltaMinutes > 0 ? "nach hinten verschoben" : "vorverlegt";
    }

    private static String formatDuration(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return minutesText(rest);
        }
        String hourText = hours == 1 ? "1 Stunde" : hours + " Stunden";
        return rest == 0 ? hourText : hourText + " " + minutesText(rest);
    }

    private static String minutesText(long minutes) {
        return minutes == 1 ? "1 Minute" : minutes + " Minuten";
    }

    public Message sendMessage(Long appointmentId, String messageText, String actingUserOidcId) {
        return sendMessage(appointmentId, messageText, actingUserOidcId, Instant.now());
    }

    public Message sendMessage(Long appointmentId, String messageText, String actingUserOidcId, Instant timeStamp) {
        LOGGER.debugf("[Principal %s][Appointment %s] Sending message", actingUserOidcId, appointmentId);
        authorizationService.requireSendMessage(appointmentId, actingUserOidcId);
        Appointment appointment = appointmentRepository.findById(appointmentId);
        Message message = persistMessage(messageText, actingUserOidcId, appointment, timeStamp);
        messageSentEvent.fire(new MessageSentEvent(message.id));
        return message;
    }

    public List<MessageDto> getMessages(Long appointmentId, String requestingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading Messages", requestingUserOidcId, appointmentId);
        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);
        List<Message> messages = messageRepository.listByAppointment(appointmentId);

        Map<String, UserIdentity> senderMap = identityPort.findByIds(
                messages.stream().map(Message::getSenderOidcId)
                        .filter(java.util.Objects::nonNull).distinct().toList()
        );

        return messages.stream()
                .map(m -> {
                    MessageDto dto = messageMapper.toDto(m);
                    UserIdentity sender = senderMap.get(m.getSenderOidcId());
                    if (sender != null) {
                        return new MessageDto(dto.id(), dto.sender_id(), sender.getName(),
                                dto.appointment_id(), dto.body(), dto.timestamp());
                    }
                    return dto;
                })
                .toList();
    }

    private Message persistMessage(String text, String senderOidcId, Appointment appointment) {
        return persistMessage(text, senderOidcId, appointment, Instant.now());
    }

    private Message persistMessage(String text, String senderOidcId, Appointment appointment, Instant timeStamp) {
        Message message = new Message();
        message.setBody(text);
        message.setSenderOidcId(senderOidcId);
        message.setAppointment(appointment);
        message.setTimeStamp(timeStamp);
        messageRepository.persist(message);
        return message;
    }
}
