package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.dto.AdminStatisticsDto;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentParticipationRepository;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import de.chronos_live.chronos_date_api.infrastructure.MessageRepository;
import de.chronos_live.chronos_date_api.infrastructure.PushSubscriptionRepository;
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
    AppointmentRepository appointmentRepository;
    @Inject
    AppointmentParticipationRepository participationRepository;
    @Inject
    GroupRepository groupRepository;
    @Inject
    FriendshipRepository friendshipRepository;
    @Inject
    MessageRepository messageRepository;
    @Inject
    PushSubscriptionRepository pushSubscriptionRepository;

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
        // User count is now sourced from Keycloak — not available here without Admin API call
        return new AdminStatisticsDto.UserStatistics(-1L);
    }

    private AdminStatisticsDto.AppointmentStatistics getAppointmentStatistics() {
        return new AdminStatisticsDto.AppointmentStatistics(
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.PLANNED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                appointmentRepository.countByStatus(AppointmentStatus.DELETED),
                appointmentRepository.countByStatus(AppointmentStatus.NOT_ENOUGH_ATTENDEES)
        );
    }

    private AdminStatisticsDto.ParticipationStatistics getParticipationStatistics() {
        return new AdminStatisticsDto.ParticipationStatistics(
                participationRepository.count(),
                participationRepository.countByStatus(ParticipationStatus.PENDING),
                participationRepository.countByStatus(ParticipationStatus.APPROVED),
                participationRepository.countByStatus(ParticipationStatus.REJECTED)
        );
    }

    private AdminStatisticsDto.GroupStatistics getGroupStatistics() {
        return new AdminStatisticsDto.GroupStatistics(
                groupRepository.count(),
                groupRepository.countMembers()
        );
    }

    private AdminStatisticsDto.FriendshipStatistics getFriendshipStatistics() {
        return new AdminStatisticsDto.FriendshipStatistics(
                friendshipRepository.count(),
                friendshipRepository.count("status", FriendshipStatus.PENDING),
                friendshipRepository.count("status", FriendshipStatus.ACCEPTED),
                friendshipRepository.count("status", FriendshipStatus.DECLINED)
        );
    }

    private AdminStatisticsDto.MessageStatistics getMessageStatistics() {
        return new AdminStatisticsDto.MessageStatistics(messageRepository.count());
    }

    private AdminStatisticsDto.PushSubscriptionStatistics getPushSubscriptionStatistics() {
        return new AdminStatisticsDto.PushSubscriptionStatistics(pushSubscriptionRepository.count());
    }
}
