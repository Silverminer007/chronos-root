package de.chronos_live.chronos_date_api.application.scheduler;

import de.chronos_live.chronos_date_api.application.AppointmentReminderService;
import de.chronos_live.chronos_date_api.application.LeaderElectionService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentReminderScheduler {

    @Inject
    AppointmentReminderService service;

    @Inject
    LeaderElectionService leaderElectionService;

    @Scheduled(cron = "0 */15 * * * ?")
    void triggerPendingReminders() {
        if (!leaderElectionService.isLeader()) return;
        service.sendPendingReminders();
    }
}
