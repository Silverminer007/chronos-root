package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@Timed("service.messageQuery")
public class MessageQueryService {

    public List<Message> getMessages(Long appointmentId) {
        return Message.find("appointment.id = ?1 ORDER BY timeStamp DESC", appointmentId).list();
    }

    public Message getMessage(Long messageId) {
        return (Message) Message
                .find("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.appointment WHERE m.id = ?1",
                        messageId)
                .firstResultOptional()
                .orElseThrow(() -> new ResourceNotFoundException("message", messageId));
    }
}
