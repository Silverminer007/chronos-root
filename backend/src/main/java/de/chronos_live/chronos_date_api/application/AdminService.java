package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.presentation.AdminStatisticsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class AdminService {
    public long countEvents() {
        return Event.findAll().count();
    }

    public AdminStatisticsDto getStatistics() {
        long eventsCount = Event.findAll().count();
        long userCount = User.findAll().count();
        long groupCount = Group.findAll().count();
        long contactsCount = Contact.findAll().count();
        long pushSubscriptionCount = PushSubscription.findAll().count();
        return new AdminStatisticsDto(eventsCount, userCount, groupCount, contactsCount, pushSubscriptionCount);
    }
}
