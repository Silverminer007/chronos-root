package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentCancelledEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentMovedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusChangedEvent;
import de.chronos_live.chronos_date_api.application.events.MessageSentEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
@Transactional
public class MessageService {
    @Inject
    AuthorizationService authorizationService;

    @Inject
    MessageQueryService messageQueryService;

    @Inject
    Event<MessageSentEvent> messageSentEvent;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusChanged(@Observes AppointmentParticipationStatusChangedEvent event) {
        Appointment appointment = Appointment.findById(event.appointmentId());
        User user = User.findById(event.actingUserId());

        String messageText = "%s hat %s".formatted(user.getName(), event.newParticipationStatus().equals(ParticipationStatus.APPROVED)
                ? "zugesagt" : "abgesagt");

        Message message = new Message();
        message.setBody(messageText);
        message.setSender(user);
        message.setAppointment(appointment);
        message.setTimeStamp(Instant.now());
        message.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentCancelled(@Observes AppointmentCancelledEvent event) {
        Appointment appointment = Appointment.findById(event.cancelledAppointmentId());
        User user = User.findById(event.actingUserId());

        String messageText = "%s hat diesen Termin abgesagt. Er wird nicht stattfinden!"
                .formatted(user.getName());

        Message message = new Message();
        message.setBody(messageText);
        message.setSender(user);
        message.setAppointment(appointment);
        message.setTimeStamp(Instant.now());
        message.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentMoved(@Observes AppointmentMovedEvent event) {
        Appointment appointment = Appointment.findById(event.appointmentId());
        User user = User.findById(event.actingUserId());

        long hourDelta = event.oldStartTime().until(appointment.getStartTime(), ChronoUnit.HOURS);

        String messageText;
        if (hourDelta > 0) {
            messageText = "%s hat diesen Termin um %s Stunden verschoben"
                    .formatted(
                            user.getName(),
                            hourDelta
                    );
        } else if (hourDelta < 0) {
            messageText = "%s hat diesen Termin um %s Stunden vor verlegt"
                    .formatted(
                            user.getName(),
                            hourDelta * -1
                    );
        } else {
            hourDelta = event.oldEndTime().until(appointment.getEndTime(), ChronoUnit.HOURS);
            if (hourDelta > 0) {
                messageText = "%s hat hat das Ende dieses Termins um %s Stunden verschoben"
                        .formatted(
                                user.getName(),
                                hourDelta
                        );
            } else {
                messageText = "%s hat hat das Ende dieses Termins um %s Stunden vor verlegt"
                        .formatted(
                                user.getName(),
                                hourDelta * -1
                        );
            }
        }

        Message message = new Message();
        message.setBody(messageText);
        message.setSender(user);
        message.setAppointment(appointment);
        message.setTimeStamp(Instant.now());
        message.persist();
    }

    public Message sendMessage(Long appointmentId, String messageText, Long actingUserId) {
        return this.sendMessage(appointmentId, messageText, actingUserId, Instant.now());
    }

    public Message sendMessage(Long appointmentId, String messageText, Long actingUserId, Instant timeStamp) {
        this.authorizationService.requireSendMessage(appointmentId, actingUserId);
        Appointment appointment = Appointment.findById(appointmentId);

        Message message = new Message();
        message.setBody(messageText);
        User user = (User) User
                .findByIdOptional(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user", actingUserId));
        message.setSender(user);
        message.setAppointment(appointment);
        message.setTimeStamp(timeStamp);
        message.persist();

        this.messageSentEvent.fire(new MessageSentEvent(message.id));

        return message;
    }

    public List<Message> getMessages(Long appointmentId, Long requestingUserId) {
        this.authorizationService.requireReadAppointment(appointmentId, requestingUserId);

        return this.messageQueryService.getMessages(appointmentId);
    }
}
