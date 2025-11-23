package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.NotificationCategory;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class MessageService {
    private final NotificationService notificationService;
    private final EventAccessService eventAccessService;

    public MessageService(NotificationService notificationService, EventAccessService eventAccessService) {
        this.notificationService = notificationService;
        this.eventAccessService = eventAccessService;
    }

    public void sendMessage(Long eventId, String messageTitle, String messageText, User sender, NotificationCategory notificationCategory) {
        Event event = Event.findById(eventId);

        Message message = new Message();
        message.setTitle(messageTitle);
        message.setMessage(messageText);
        message.setSender(sender);
        message.setEvent(event);
        message.setTimeStamp(LocalDateTime.now());
        message.persist();

        for (User user : eventAccessService.getAttendees(event)) {
            if (Objects.equals(user.id, sender.id)) {
                continue;
            }
            this.notificationService.notify(user, messageTitle, messageText, notificationCategory);
        }
    }

    public List<Message> getMessages(Long eventId) {
        return Message.find("event.id ORDER BY timeStamp DESC", eventId).list();
    }
}
