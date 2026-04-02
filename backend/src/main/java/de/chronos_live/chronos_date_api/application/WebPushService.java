package de.chronos_live.chronos_date_api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.NotificationPort;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.PushAppointmentMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@ApplicationScoped
@Timed("service.webpush")
public class WebPushService {

    private static final Logger LOGGER = Logger.getLogger(WebPushService.class);

    @Inject
    NotificationPort notificationPort;

    @Inject
    SettingsService settingsService;

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
    AppointmentQueryService appointmentQueryService;

    @Inject
    UserQueryService userQueryService;

    @Inject
    MeterRegistry meterRegistry;

    public String getPublicKey() {
        return notificationPort.getVapidPublicKey();
    }

    public void sendToUser(Long userId, String title, String body) {
        JsonObject payload = Json.createObjectBuilder()
                .add("title", title)
                .add("body", body)
                .build();
        notificationPort.send(userId, payload.toString());
    }

    public void sendNotification(Long userId, String payload) {
        notificationPort.send(userId, payload);
    }

    private String getAndParseAppointment(Long appointmentId) {
        Appointment appointment = appointmentQueryService.findById(appointmentId);
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
        Group group = groupQueryService.findById(groupId);
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
        List<AppointmentParticipation> participants =
                this.appointmentParticipationQueryService.getParticipants(appointmentId);
        for (AppointmentParticipation participation : participants) {
            if (!sendTest.test(participation)) {
                continue;
            }
            notificationPort.send(participation.getUser().id, payload);
        }
    }

    private void sendToMembers(Long groupId, Predicate<GroupMember> sendTest, String payload) {
        List<GroupMember> members = this.groupQueryService.getGroupMembers(groupId);
        for (GroupMember groupMember : members) {
            if (!sendTest.test(groupMember)) {
                continue;
            }
            notificationPort.send(groupMember.getUser().id, payload);
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
                ap -> !Objects.equals(ap.getUser().id, message.getSender().id)
                        && this.settingsService.sendAppointmentMessageSentNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentMoved(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentMovedEvent event) {
        User actingUser = userQueryService.findById(event.actingUserId());
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
                ap -> !Objects.equals(ap.getUser().id, event.actingUserId())
                        && this.settingsService.sendAppointmentMovedNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentCancelled(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentCancelledEvent event) {
        User actingUser = userQueryService.findById(event.actingUserId());
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
                ap -> !Objects.equals(ap.getUser().id, event.actingUserId())
                        && this.settingsService.sendAppointmentCancelledNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipantAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationAddedEvent event) {
        User actingUser = userQueryService.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        User newParticipant = userQueryService.findById(event.targetUserId());
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
                ap -> !Objects.equals(ap.getUser().id, event.actingUserId())
                        && this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
                payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentGroupParticipantAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentGroupParticipationAddedEvent event) {
        User actingUser = userQueryService.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        Group newGroupParticipant = groupQueryService.findById(event.groupId());
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
                ap -> !Objects.equals(ap.getUser().id, event.actingUserId())
                        && this.settingsService.sendAppointmentParticipantAddedEventNotification(ap),
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

        notificationPort.send(event.addresseeId(), payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipAccepted(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipAcceptedEvent event) {
        User addressee = userQueryService.findById(event.addresseeId());
        if (addressee == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_ACCEPTED")
                .add("addressee_id", event.addresseeId())
                .add("addressee_name", addressee.getName())
                .build();

        notificationPort.send(event.addresseeId(), payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipDeclined(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipDeclinedEvent event) {
        User addressee = userQueryService.findById(event.addresseeId());
        if (addressee == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_DECLINED")
                .add("addressee_id", event.addresseeId())
                .add("addressee_name", addressee.getName())
                .build();

        notificationPort.send(event.addresseeId(), payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onFriendshipRemoved(@Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipRemovedEvent event) {
        User actingUser = userQueryService.findById(event.actingUserId());
        if (actingUser == null) {
            return;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "FRIENDSHIP_REMOVED")
                .add("acting_user_id", event.actingUserId())
                .add("acting_user_name", actingUser.getName())
                .build();

        notificationPort.send(event.friendId(), payload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onGroupMemberAdded(@Observes(during = TransactionPhase.AFTER_SUCCESS) GroupMemberAddedEvent event) {
        User newMember = userQueryService.findById(event.newMemberId());
        if (newMember == null) {
            return;
        }

        String groupJson = getAndParseGroup(event.groupId());
        if (groupJson == null) {
            return;
        }

        JsonObject groupPayload = Json.createObjectBuilder()
                .add("type", "NEW_GROUP_MEMBER")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        this.sendToMembers(
                event.groupId(),
                gm -> !Objects.equals(newMember.id, gm.getUser().id)
                        && this.settingsService.sendGroupMemberAddedNotification(gm),
                groupPayload.toString());

        JsonObject newMemberPayload = Json.createObjectBuilder()
                .add("type", "ADDED_TO_GROUP")
                .add("group", groupJson)
                .add("new_member", newMember.getName())
                .build();
        notificationPort.send(newMember.id, newMemberPayload.toString());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusChanged(@ObservesAsync AppointmentParticipationStatusChangedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            User actingUser = userQueryService.findById(event.actingUserId());
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
                    ap -> !Objects.equals(event.actingUserId(), ap.getUser().id)
                            && this.settingsService.sendAppointmentParticipationStatusChangedNotification(ap),
                    payload.toString());
        } finally {
            sample.stop(Timer.builder("observer.webpush.onParticipationStatusChanged")
                    .description("Time for sending push notifications on participation status change")
                    .register(meterRegistry));
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationInvalid(@Observes(during = TransactionPhase.AFTER_SUCCESS) AppointmentParticipationInvalidEvent event) {
        String appointmentJson = this.getAndParseAppointment(event.appointmentId());
        if (appointmentJson == null) {
            return;
        }

        ParticipationStatistik stats =
                this.appointmentParticipationQueryService.getParticipationStatistik(event.appointmentId());

        JsonObject payload = Json.createObjectBuilder()
                .add("type", "APPOINTMENT_PARTICIPATION_INVALID")
                .add("appointment", appointmentJson)
                .add("approved_participation", stats.approvedCount())
                .add("rejected_participation", stats.rejectedCount())
                .add("pending_participation",
                        stats.participantCount() - stats.approvedCount() - stats.rejectedCount())
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

        List<AppointmentParticipation> participants =
                this.appointmentParticipationQueryService.getParticipants(event.appointmentId());

        for (AppointmentParticipation participation : participants) {
            if (!this.settingsService.sendAppointmentParticipationStatusRecheckRequestedNotification(participation)) {
                continue;
            }

            JsonObject payload = Json.createObjectBuilder()
                    .add("type", "PARTICIPATION_STATUS_RECHECK")
                    .add("appointment", appointmentJson)
                    .add("participation_status", participation.getStatus().toString())
                    .build();

            notificationPort.send(participation.getUser().id, payload.toString());
        }
    }
}
