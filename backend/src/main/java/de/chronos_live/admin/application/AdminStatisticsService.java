package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.PushSubscription;
import de.chronos_live.chronos_date_api.dto.AdminStatisticsDto;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;

@ApplicationScoped
@Transactional
@Timed("service.admin.statistics")
public class AdminStatisticsService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    public AdminStatisticsDto getStatistics() {
        return new AdminStatisticsDto(
                getUserStatistics(),
                getAppointmentStatistics(),
                getParticipationStatistics(),
                getGroupStatistics(),
                getFriendshipStatistics(),
                getMessageStatistics(),
                getPushSubscriptionStatistics()
        );
    }

    private AdminStatisticsDto.UserStatistics getUserStatistics() {
        return new AdminStatisticsDto.UserStatistics((long) keycloak.realm(realm).users().count());
    }

    private AdminStatisticsDto.AppointmentStatistics getAppointmentStatistics() {
        return new AdminStatisticsDto.AppointmentStatistics(
                Appointment.count(),
                Appointment.count("status", AppointmentStatus.PLANNED),
                Appointment.count("status", AppointmentStatus.CANCELLED),
                Appointment.count("status", AppointmentStatus.DELETED),
                Appointment.count("status", AppointmentStatus.NOT_ENOUGH_ATTENDEES)
        );
    }

    private AdminStatisticsDto.ParticipationStatistics getParticipationStatistics() {
        return new AdminStatisticsDto.ParticipationStatistics(
                AppointmentParticipation.count(),
                AppointmentParticipation.count("status", ParticipationStatus.PENDING),
                AppointmentParticipation.count("status", ParticipationStatus.APPROVED),
                AppointmentParticipation.count("status", ParticipationStatus.REJECTED)
        );
    }

    private AdminStatisticsDto.GroupStatistics getGroupStatistics() {
        return new AdminStatisticsDto.GroupStatistics(
                Group.count(),
                GroupMember.count()
        );
    }

    private AdminStatisticsDto.FriendshipStatistics getFriendshipStatistics() {
        return new AdminStatisticsDto.FriendshipStatistics(
                FriendshipRequest.count(),
                FriendshipRequest.count("status", FriendshipStatus.PENDING),
                FriendshipRequest.count("status", FriendshipStatus.ACCEPTED),
                FriendshipRequest.count("status", FriendshipStatus.DECLINED)
        );
    }

    private AdminStatisticsDto.MessageStatistics getMessageStatistics() {
        return new AdminStatisticsDto.MessageStatistics(Message.count());
    }

    private AdminStatisticsDto.PushSubscriptionStatistics getPushSubscriptionStatistics() {
        return new AdminStatisticsDto.PushSubscriptionStatistics(PushSubscription.count());
    }
}
