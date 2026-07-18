package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MessageRepository implements PanacheRepository<Message> {

    public List<Message> listByAppointment(Long appointmentId) {
        return Message.find("appointment.id = ?1 ORDER BY timeStamp DESC", appointmentId).list();
    }

    public Message findByIdOrThrow(Long messageId) {
        return Message.<Message>find("SELECT m FROM Message m JOIN FETCH m.appointment WHERE m.id = ?1", messageId)
                .firstResultOptional()
                .orElseThrow(() -> new ResourceNotFoundException("message", messageId));
    }
}
