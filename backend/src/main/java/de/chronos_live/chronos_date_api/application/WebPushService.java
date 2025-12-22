package de.chronos_live.chronos_date_api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chronos_live.chronos_date_api.config.PushConfig;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.mapper.EventMapper;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jboss.logging.Logger;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
@Transactional
public class WebPushService {
    private static final Logger LOGGER = Logger.getLogger(WebPushService.class);

    @Inject
    PushSubscriptionService subscriptionService;

    private PushService push;

    @Inject
    PushConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventMapper eventMapper;

    @Inject
    GroupMapper groupMapper;

    @jakarta.annotation.PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            push = new PushService(
                    config.getPublicKey(),
                    config.getPrivateKey(),
                    config.getMailto()
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize WebPushService", e);
        }
    }

    public String getPublicKey() {
        return config.getPublicKey();
    }

    private void sendNotification(Long userId, String payload) {
        var subs = subscriptionService.getAllForUser(userId);

        subs.forEach(sub -> {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload
                );

                push.send(notification);

            } catch (Exception e) {
                // invalid endpoint -> löschen
                subscriptionService.deleteByEndpoint(sub.getEndpoint());
            }
        });
    }

    private String eventToJson(Event event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(this.eventMapper.toDto(event));
    }

    private String groupToJson(Group group) throws JsonProcessingException {
        return objectMapper.writeValueAsString(this.groupMapper.toDto(group));
    }
    
    private void sendToAdmin(String subject) {
        
    }

    public void sendToUser(Long userId, String title, String body) {
        JsonObject payload = Json.createObjectBuilder()
                .add("title", title)
                .add("body", body)
                .build();
        this.sendNotification(userId, payload.toString());
    }

    public void sendEventMessageNotification(String messageTitle, String messageBody, User sender, Set<User> recipients) {
        // TODO Wir sollten wahrscheinlich noch Absender und zugehöriges Event irgendwie in die Nachricht einfügen
        for (User recipient : recipients) {
            if (Objects.equals(sender.id, recipient.id)) {
                continue;
            }
            sendToUser(recipient.id, messageTitle, messageBody);
        }
    }

    public void sendEventMovedNotification(Event newEvent, Event oldEvent, Set<User> users) {
        String newEventJson;
        String oldEventJson;
        try {
            newEventJson = eventToJson(newEvent);
            oldEventJson = eventToJson(oldEvent);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "EVENT_MOVED")
                .add("new_event", newEventJson)
                .add("old_event", oldEventJson)
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendEventCancelledNotification(Event event, User whoCancelled, Set<User> users) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "EVENT_CANCELLED")
                .add("event", eventJson)
                .add("who_cancelled", whoCancelled.getName())
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendNewEventAttendeeNotification(Event event, String newAttendeeName, String attendeeType, String newAttendeeRole, Set<User> users) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_ATTENDEE")
                .add("event", eventJson)
                .add("new_attendee", newAttendeeName)
                .add("attendee_type", attendeeType)
                .add("attendee_role", newAttendeeRole)
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendNewEventInviteNotification() {
    }

    public void sendEventInviteAnsweredNotification() {
    }

    public void sendNewFriendshipInviteNotification() {
    }

    public void sendFriendshipInviteAnsweredNotification() {
    }

    public void sendFriendshipCancelledNotification() {
    }

    public void sendNewGroupMemberNotification(Group group, User newMember, Set<User> members) {
        String groupJson;
        try {
            groupJson = groupToJson(group);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse group. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_GROUP_MEMBER")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        for (User user : members) {
            if (Objects.equals(user.id, newMember.id)) {
                continue;
            }
            this.sendNotification(user.id, payload.toString());
        }
        JsonObject newMemberMessage = Json.createObjectBuilder()
                .add("type", "ADDED_TO_GROUP")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        this.sendNotification(newMember.id, newMemberMessage.toString());
    }

    public void sendLeftGroupNotification(Group group, User oldMember, Set<User> members) {
        String groupJson;
        try {
            groupJson = groupToJson(group);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse group. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "GROUP_MEMBER_LEFT")
                .add("group", groupJson)
                .add("old_member", oldMember.getName())
                .build();
        for (User user : members) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendAttendanceStatusChangedNotification(Event event, Attendance attendance, Set<User> users) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "ATTENDANCE_STATUS_CHANGED")
                .add("event", eventJson)
                .add("new_attendance_status", attendance.getStatus().toString())
                .add("user_name", attendance.getUser().getName())
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendNotEnoughAttendeesNotification(Event event, List<Attendance> attendanceList, Set<User> users) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        long approved = attendanceList.stream().filter(a -> a.getStatus().equals(AttendanceStatus.APPROVED)).count();
        long rejected = attendanceList.stream().filter(a -> a.getStatus().equals(AttendanceStatus.REJECTED)).count();
        long pending = attendanceList.stream().filter(a -> a.getStatus().equals(AttendanceStatus.PENDING)).count();
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NOT_ENOUGH_ATTENDEES")
                .add("event", eventJson)
                .add("approved_attendances", approved)
                .add("rejected_attendances", rejected)
                .add("pending_attendances", pending)
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendAttendanceStatusPendingReminderNotification(Event event, Set<User> recipients) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "ATTENDANCE_STATUS_PENDING")
                .add("event", eventJson)
                .build();
        for(User recipient : recipients) {
            this.sendNotification(recipient.id, payload.toString());
        }
    }

    public void sendEventReminderNotification(Event event, Set<User> users) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "EVENT_REMINDER")
                .add("event", eventJson)
                .build();
        for (User user : users) {
            this.sendNotification(user.id, payload.toString());
        }
    }

    public void sendAttendanceStatusRecheckNotification(Event event, List<Attendance> attendances) {
        String eventJson;
        try {
            eventJson = eventToJson(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse event. Trying to send notification", e);
            this.sendToAdmin("Failed to send notification");
            return;
        }
        for (Attendance attendance : attendances) {
            JsonObject payload = Json.createObjectBuilder()
                    .add("type", "ATTENDANCE_STATUS_RECHECK")
                    .add("event", eventJson)
                    .add("attendance_status", attendance.getStatus().toString())
                    .build();
            this.sendNotification(attendance.getUser().id, payload.toString());
        }
    }
}