package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Contact;
import de.chronos_live.chronos_date_api.domain.NotificationCategory;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
@Transactional
public class ContactService {
    private final NotificationService notificationService;

    public ContactService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<User> getContacts(User user) {
        List<Contact> contacts = Contact.find("user.id = ?1", user.id).list();
        return contacts.stream().map(Contact::getContact).toList();
    }

    public List<User> searchContacts(User user, String searchQuery) {
        searchQuery = "%" + searchQuery + "%";
        List<Contact> contacts = Contact.find("user = ?1 " +
                        "AND (lower(contact.firstName) LIKE lower(?2) OR lower(contact.lastName) LIKE lower(?2))"
                , user, searchQuery).list();
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
        this.notificationService.notify(contactUser, message, message, NotificationCategory.CONTACTS);
    }
}
