package de.chronos_live.chronos_date_api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.config.PushConfig;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.infrastructure.PushNotificationLogRepository;
import de.chronos_live.chronos_date_api.mapper.AppointmentMapper;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.PushAppointmentMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@ApplicationScoped
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
    PushAppointmentMapper appointmentMapper;

    @Inject
    GroupMapper groupMapper;

    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @Inject
    MessageQueryService messageQueryService;

    @Inject
    GroupQueryService groupQueryService;

    @Inject
    PushNotificationLogRepository pushNotificationLogRepository;

    @jakarta.annotation.PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            if (config.getPublicKey().isEmpty() || config.getPrivateKey().isEmpty() || config.getMailto().isEmpty()) {
                push = null;
                Log.warn("VAPID Keys are not set. No Push Notifications will be sent. Please set VAPID_PUBLIC, VAPID_PRIVATE and VAPID_MAILTO environment variables");
                return;
            }
            push = new PushService(
                    config.getPublicKey().orElse(""),
                    config.getPrivateKey().orElse(""),
                    config.getMailto().orElse("")
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize WebPushService", e);
        }
    }

    public String getPublicKey() {
        return config.getPublicKey().orElse("");
    }

    public void sendToUser(Long userId, String title, String body) {
        JsonObject payload = Json.createObjectBuilder()
                .add("title", title)
                .add("body", body)
                .build();
        this.sendNotification(userId, payload.toString());
    }

    public void sendNotification(Long userId, String payload) {
        if (push == null) {
            LOGGER.warn("Omitting Push notification, because VAPID Keys are not set");
            return;
        }
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

                var response = push.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();
                boolean success = statusCode >= 200 && statusCode < 300;

                logNotification(userId, payload, sub.getEndpoint(), statusCode, success, null);

                if (statusCode == 410 || statusCode == 404) {
                    Log.infof("[Notifications] Subscription expired (HTTP %d), removing endpoint %s", statusCode, sub.getEndpoint());
                    subscriptionService.deleteByEndpoint(sub.getEndpoint());
                } else if (!success) {
                    Log.warnf("[Notifications] Push service returned HTTP %d for endpoint %s", statusCode, sub.getEndpoint());
                }

            } catch (Exception e) {
                Log.errorf(e, "[Notifications] Failed to send notification to endpoint %s", sub.getEndpoint());
                logNotification(userId, payload, sub.getEndpoint(), null, false, e.getMessage());
                subscriptionService.deleteByEndpoint(sub.getEndpoint());
            }
        });
    }

    private void logNotification(Long userId, String payload, String endpoint,
                                  Integer httpStatusCode, boolean success, String errorMessage) {
        try {
            String notificationType = null;
            try {
                var jsonObject = Json.createReader(new StringReader(payload)).readObject();
                if (jsonObject.containsKey("type")) {
                    notificationType = jsonObject.getString("type");
                }
            } catch (Exception ignored) {
            }

            if (errorMessage != null && errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000);
            }

            pushNotificationLogRepository.log(userId, notificationType, payload,
                    endpoint, httpStatusCode, success, errorMessage);
        } catch (Exception e) {
            Log.errorf(e, "[Notifications] Failed to persist audit log for endpoint %s", endpoint);
        }
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentMessageSent(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageSentEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentMoved(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentMovedEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentCancelled(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentCancelledEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipantAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationAddedEvent event) {
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
                .add("type", "NEW_PARTICIPANT")
                .add("appointment", appointmentJson)
                .add("new_participant", newParticipant.getName())
                .add("participant_type", "user")
                .add("participant_role", newParticipantRole)
                .add("acting_user_name", actingUser.getName())
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentGroupParticipantAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentGroupParticipationAddedEvent event) {
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
                .add("type", "NEW_PARTICIPANT")
                .add("appointment", appointmentJson)
                .add("new_participant", newGroupParticipant.getGroupName())
                .add("participant_type", "group")
                .add("acting_user_name", actingUser.getName())
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(ap.getUser().id, event.actingUserId())
                                || this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipRequestSent(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipRequestSentEvent event) {
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "NEW_FRIENDSHIP_REQUEST")
                .add("requester_id", event.requesterId())
                .add("requester_name", event.requesterName())
                .add("request_id", event.requestId())
                .build();

        this.sendNotification(event.addresseeId(), payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipAccepted(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipAcceptedEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipDeclined(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipDeclinedEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipRemoved(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipRemovedEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onGroupMemberAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) GroupMemberAddedEvent event) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationStatusChangedEvent event) {
        User actingUser = User.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        String appointmentJson = getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "PARTICIPATION_STATUS_CHANGED")
                .add("appointment", appointmentJson)
                .add("new_participation_status", event.newParticipationStatus().toString())
                .add("user_name", actingUser.getName())
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap ->
                        !Objects.equals(event.actingUserId(), ap.getUser().id)
                                && this.settingsService.sendAppointmentParticipationStatusChangedNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationInvalid(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationInvalidEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        ParticipationStatistik participationStatistik =
                this.appointmentParticipationQueryService.getParticipationStatistik(event.appointmentId());

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "APPOINTMENT_PARTICIPATION_INVALID")
                .add("appointment", appointmentJson)
                .add("approved_participation", participationStatistik.approvedCount())
                .add("rejected_participation", participationStatistik.rejectedCount())
                .add("pending_participation",
                        participationStatistik.participantCount()
                                - participationStatistik.approvedCount()
                                - participationStatistik.rejectedCount())
                .build();
        this.sendToParticipants(event.appointmentId(),
                this.settingsService::sendAppointmentParticipationInvalidNotification,
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusPendingReminder(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationStatusPendingReminderEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "PARTICIPATION_STATUS_PENDING")
                .add("appointment", appointmentJson)
                .build();

        this.sendToParticipants(event.appointmentId(),
                ap -> ParticipationStatus.PENDING.equals(ap.getStatus())
                        && this.settingsService.sendAppointmentParticipationStatusPendingReminderNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentReminder(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentReminderEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }
        JsonObject payload = Json.createObjectBuilder()
                .add("type", "PARTICIPATION_REMINDER")
                .add("appointment", appointmentJson)
                .build();

        this.sendToParticipants(event.appointmentId(),
                this.settingsService::sendAppointmentReminderNotification,
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusRecheckRequested(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationStatusRecheckRequestedEvent event) {
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
                    .add("type", "PARTICIPATION_STATUS_RECHECK")
                    .add("appointment", appointmentJson)
                    .add("participation_status", participation.getStatus().toString())
                    .build();

            this.sendNotification(participation.getUser().id, payload.toString());
        }
    }
}