package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Transactional
public class MessageService {

    public Message sendMessage(Long eventId, String messageTitle, String messageText, User sender) {
        Event event = Event.findById(eventId);

        Message message = new Message();
        message.setTitle(messageTitle);
        message.setMessage(messageText);
        message.setSender(sender);
        message.setEvent(event);
        message.setTimeStamp(Instant.now());
        message.persist();

        return message;
    }

    public List<Message> getMessages(Long eventId) {
        return Message.find("event.id = ?1 ORDER BY timeStamp DESC", eventId).list();
    }
}
