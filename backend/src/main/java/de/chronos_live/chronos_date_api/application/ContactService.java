package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Contact;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ContactService {
    private final NotificationService notificationService;

    public ContactService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<User> getContacts(User user) {
        List<Contact> contacts = Contact.find("user = ?", user).list();
        return contacts.stream().map(Contact::getContact).toList();
    }

    public void addContact(User user, String contactEmail) {
        User contactUser = User.find("email = ?", contactEmail).firstResult();
        if (contactUser == null) {
            throw new IllegalArgumentException("No user with email " + contactEmail);
        }
        Contact contact = new Contact();
        contact.setUser(user);
        contact.setContact(contactUser);
        contact.persist();
        String message = String.format("%s added you to their contacts", user.getName());
        this.notificationService.notify(contactUser, message, message);
    }
}
