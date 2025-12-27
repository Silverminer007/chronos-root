package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Message;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MessageQueryService {

    public List<Message> getMessages(Long appointmentId) {
        return Message.find("appointment.id = ?1 ORDER BY timeStamp DESC", appointmentId).list();
    }

    public Message getMessage(Long messageId) {
        return Message.findById(messageId);
    }
}
