package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MessageService {
    private final NotificationService notificationService;
    private final EventAccessService eventAccessService;

    public MessageService(NotificationService notificationService, EventAccessService eventAccessService) {
        this.notificationService = notificationService;
        this.eventAccessService = eventAccessService;
    }

    public void sendMessage(Event event, String messageTitle, String messageText, User sender) {
        Message message = new Message();
        message.setTitle(messageTitle);
        message.setMessage(messageText);
        message.setSender(sender);
        message.setEvent(event);
        message.persist();

        for (User user : eventAccessService.getAttendees(event)) {
            this.notificationService.notify(user, messageTitle, messageText);
        }
    }
}
