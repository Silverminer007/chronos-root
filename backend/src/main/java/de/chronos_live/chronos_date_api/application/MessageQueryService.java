package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Timed("service.messageQuery")
public class MessageQueryService {

    @Inject
    MessageRepository messageRepository;

    public List<Message> getMessages(Long appointmentId) {
        return messageRepository.listByAppointment(appointmentId);
    }

    public Message getMessage(Long messageId) {
        return messageRepository.findByIdOrThrow(messageId);
    }
}
