package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.AdminStatisticsDto;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
@Timed("service.admin.statistics")
public class AdminStatisticsService {

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
        return new AdminStatisticsDto.UserStatistics(User.count());
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
