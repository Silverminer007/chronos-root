package de.chronos_live.chronos_date_api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.config.PushConfig;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.mapper.AppointmentMapper;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
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
import java.util.function.Predicate;

@ApplicationScoped
@Transactional
public class WebPushService {
    private static final Logger LOGGER = Logger.getLogger(WebPushService.class);

    @Inject
    PushSubscriptionService subscriptionService;

    @Inject
    SettingsService settingsService;

    private PushService push;

    @Inject
    PushConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AppointmentMapper appointmentMapper;

    @Inject
    GroupMapper groupMapper;

    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @Inject
    MessageQueryService messageQueryService;

    @Inject
    GroupQueryService groupQueryService;

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

    public void sendToUser(Long userId, String title, String body) {
        JsonObject payload = Json.createObjectBuilder()
                .add("title", title)
                .add("body", body)
                .build();
        this.sendNotification(userId, payload.toString());
    }

    private void sendNotification(Long userId, String payload) {
        Log.debugf("[Notifications] Sending notification to user with ID [%d] %s", userId, payload);
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

    private String getAndParseAppointment(Long appointmentId) {
        Appointment appointment = Appointment.findById(appointmentId);
        if (appointment == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(this.appointmentMapper.toDto(appointment));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse appointment while trying to send notification", e);
            return null;
        }
    }

    private String getAndParseGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(this.groupMapper.toDto(group));
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse group while trying to send notification", e);
            return null;
        }
    }

    private void sendToParticipants(Long appointmentId, Predicate<AppointmentParticipation> sendTest, String payload) {
        List<AppointmentParticipation> appointmentParticipationList =
                this.appointmentParticipationQueryService.getParticipants(appointmentId);

        for (AppointmentParticipation participation : appointmentParticipationList) {
            if (!sendTest.test(participation)) {
                continue;
            }

            this.sendNotification(participation.getUser().id, payload);
        }
    }

    private void sendToMembers(Long groupId, Predicate<GroupMember> sendTest, String payload) {
        List<GroupMember> groupMemberList = this.groupQueryService.getGroupMembers(groupId);

        for (GroupMember groupMember : groupMemberList) {
            if (!sendTest.test(groupMember)) {
                continue;
            }

            this.sendNotification(groupMember.getUser().id, payload);
        }
    }

    public void onAppointmentMessageSent(@ObservesAsync MessageSentEvent event) {
        Message message = this.messageQueryService.getMessage(event.messageId());
        if (message == null) {
            return;
        }

        String notificationTitle = "%s schreibt zu %s"
                .formatted(message.getSender().getName(), message.getAppointment().getName());

        JsonObject payload = Json.createObjectBuilder()
                .add("title", notificationTitle)
                .add("body", message.getBody())
                .build();

        this.sendToParticipants(message.getAppointment().id,
                ap ->
                        !Objects.equals(ap.getUser().id, message.getSender().id)
                                || this.settingsService.sendAppointmentMessageSentNotification(ap),
                payload.toString());
    }

    public void onAppointmentMoved(@ObservesAsync AppointmentMovedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        String appointmentJson = getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "APPOINTMENT_MOVED")
                .add("appointment", appointmentJson)
                .add("old_start_time", event.oldStartTime().toString())
                .add("old_end_time", event.oldEndTime().toString())
                .add("acting_user_id", event.actingUserId())
                .add("acting_user_name", actingUser.getName())
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentMovedNotification(ap),
                payload.toString());
    }

    public void onAppointmentCancelled(@ObservesAsync AppointmentCancelledEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        String appointmentJson = getAndParseAppointment(event.cancelledAppointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "APPOINTMENT_CANCELLED")
                .add("appointment", appointmentJson)
                .add("who_cancelled", actingUser.getName())
                .build();

        this.sendToParticipants(event.cancelledAppointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentCancelledNotification(ap),
                payload.toString());
    }

    public void onAppointmentParticipantAdded(@ObservesAsync AppointmentParticipationAddedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        User newParticipant = User.findById(event.targetUserId());
        if (newParticipant == null) {
            return;
        }
        String newParticipantRole =
                this.appointmentParticipationQueryService
                        .getUserRole(event.appointmentId(), event.targetUserId())
                        .toString();

        String appointmentJson = getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_ATTENDEE")
                .add("appointment", appointmentJson)
                .add("new_attendee", newParticipant.getName())
                .add("attendee_type", "user")
                .add("attendee_role", newParticipantRole)
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
                payload.toString());
    }

    public void onAppointmentGroupParticipantAdded(@ObservesAsync AppointmentGroupParticipationAddedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        Group newGroupParticipant = Group.findById(event.groupId());
        if (newGroupParticipant == null) {
            return;
        }

        String appointmentJson = getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_ATTENDEE")
                .add("appointment", appointmentJson)
                .add("new_attendee", newGroupParticipant.getGroupName())
                .add("attendee_type", "group")
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
                payload.toString());
    }

    public void onFriendshipRequestSent(@ObservesAsync FriendshipRequestSentEvent event) {
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_FRIENDSHIP_REQUEST")
                .add("requester_id", event.requesterId())
                .add("requester_name", event.requesterName())
                .add("request_id", event.requestId())
                .build();

        this.sendNotification(event.addresseeId(), payload.toString());
    }

    public void onFriendshipAccepted(@ObservesAsync FriendshipAcceptedEvent event) {
        User addressee = User.findById(event.addresseeId());
        if (addressee == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_ACCEPTED")
                .add("addressee_id", event.addresseeId())
                .add("addressee_name", addressee.getName())
                .build();

        this.sendNotification(event.addresseeId(), payload.toString());
    }

    public void onFriendshipDeclined(@ObservesAsync FriendshipDeclinedEvent event) {
        User addressee = User.findById(event.addresseeId());
        if (addressee == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_DECLINED")
                .add("addressee_id", event.addresseeId())
                .add("addressee_name", addressee.getName())
                .build();

        this.sendNotification(event.addresseeId(), payload.toString());
    }

    public void onFriendshipRemoved(@ObservesAsync FriendshipRemovedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_REMOVED")
                .add("acting_user_id", event.actingUserId())
                .add("acting_user_name", actingUser.getName())
                .build();

        this.sendNotification(event.friendId(), payload.toString());
    }

    public void onGroupMemberAdded(@ObservesAsync GroupMemberAddedEvent event) {
        User newMember = User.findById(event.newMemberId());
        if (newMember == null) {
            return;
        }

        String groupJson = getAndParseGroup(event.groupId());
        if (groupJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_GROUP_MEMBER")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        this.sendToMembers(
                event.groupId(),
                gm ->
                        !Objects.equals(event.actingUserId(), gm.getUser().id)
                                || this.settingsService.sendGroupMemberAddedNotification(gm),
                payload.toString());

        JsonObject newMemberMessage = Json.createObjectBuilder()
                .add("type", "ADDED_TO_GROUP")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        this.sendNotification(newMember.id, newMemberMessage.toString());
    }

    public void onAppointmentParticipationStatusChanged(@ObservesAsync AppointmentParticipationStatusChangedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        String appointmentJson = getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "ATTENDANCE_STATUS_CHANGED")
                .add("appointment", appointmentJson)
                .add("new_attendance_status", event.newParticipationStatus().toString())
                .add("user_name", actingUser.getName())
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(event.actingUserId(), ap.getUser().id)
                                && this.settingsService.sendAppointmentParticipationStatusChangedNotification(ap),
                payload.toString());
    }

    public void onAppointmentParticipationInvalid(@ObservesAsync AppointmentParticipationInvalidEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        ParticipationStatistik participationStatistik =
                this.appointmentParticipationQueryService.getParticipationStatistik(event.appointmentId());

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NOT_ENOUGH_ATTENDEES")
                .add("appointment", appointmentJson)
                .add("approved_attendances", participationStatistik.approvedCount())
                .add("rejected_attendances", participationStatistik.rejectedCount())
                .add("pending_attendances",
                        participationStatistik.participantCount()
                                - participationStatistik.approvedCount()
                                - participationStatistik.rejectedCount())
                .build();
        this.sendToParticipants(event.appointmentId(),
                this.settingsService::sendAppointmentParticipationInvalidNotification,
                payload.toString());
    }

    public void onAppointmentParticipationStatusPendingReminder(@ObservesAsync AppointmentParticipationStatusPendingReminderEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "ATTENDANCE_STATUS_PENDING")
                .add("appointment", appointmentJson)
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap -> ParticipationStatus.PENDING.equals(ap.getStatus())
                        && this.settingsService.sendAppointmentParticipationStatusPendingReminderNotification(ap),
                payload.toString());
    }

    public void onAppointmentReminder(@ObservesAsync AppointmentReminderEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "APPOINTMENT_REMINDER")
                .add("appointment", appointmentJson)
                .build();

        this.sendToParticipants(event.appointmentId(),
                this.settingsService::sendAppointmentReminderNotification,
                payload.toString());
    }

    public void onAppointmentParticipationStatusRecheckRequested(@ObservesAsync AppointmentParticipationStatusRecheckRequestedEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }
        List<AppointmentParticipation> appointmentParticipationList =
                this.appointmentParticipationQueryService.getParticipants(event.appointmentId());

        for (AppointmentParticipation participation : appointmentParticipationList) {
            if (!this.settingsService.sendAppointmentParticipationStatusRecheckRequestedNotification(participation)) {
                continue;
            }

            JsonObject payload = Json.createObjectBuilder()
                    .add("type", "ATTENDANCE_STATUS_RECHECK")
                    .add("appointment", appointmentJson)
                    .add("attendance_status", participation.getStatus().toString())
                    .build();

            this.sendNotification(participation.getUser().id, payload.toString());
        }
    }
}