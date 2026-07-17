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
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
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

@ApplicationScoped
@Transactional
@Timed("service.message")
public class MessageService {
    private static final Logger LOGGER = Logger.getLogger(MessageService.class);
    @Inject
    AuthorizationService authorizationService;
    @Inject
    MessageQueryService messageQueryService;
    @Inject
    IdentityPort identityPort;
    @Inject
    AppointmentRepository appointmentRepository;
    @Inject
    MessageRepository messageRepository;
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
        long hourDelta = event.oldStartTime().until(appointment.getStartTime(), ChronoUnit.HOURS);
        String text;
        if (hourDelta > 0) {
            text = "%s hat diesen Termin um %s Stunden verschoben".formatted(user.getName(), hourDelta);
        } else if (hourDelta < 0) {
            text = "%s hat diesen Termin um %s Stunden vor verlegt".formatted(user.getName(), hourDelta * -1);
        } else {
            hourDelta = event.oldEndTime().until(appointment.getEndTime(), ChronoUnit.HOURS);
            if (hourDelta > 0) {
                text = "%s hat das Ende dieses Termins um %s Stunden verschoben".formatted(user.getName(), hourDelta);
            } else {
                text = "%s hat das Ende dieses Termins um %s Stunden vor verlegt".formatted(user.getName(), hourDelta * -1);
            }
        }
        persistMessage(text, event.actingUserOidcId(), appointment);
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

    public List<Message> getMessages(Long appointmentId, String requestingUserOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading Messages", requestingUserOidcId, appointmentId);
        authorizationService.requireReadAppointment(appointmentId, requestingUserOidcId);
        return messageQueryService.getMessages(appointmentId);
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
