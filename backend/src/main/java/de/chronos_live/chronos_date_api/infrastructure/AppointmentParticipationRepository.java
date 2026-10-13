package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.AppointmentGroupParticipation;
import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AppointmentParticipationRepository implements PanacheRepository<AppointmentParticipation> {

    // ── AppointmentParticipation queries ─────────────────────────────────────

    public Optional<AppointmentParticipation> findByAppointmentAndUser(Long appointmentId, String userOidcId) {
        return AppointmentParticipation
                .<AppointmentParticipation>find("appointment.id = ?1 AND userOidcId = ?2",
                        appointmentId, userOidcId)
                .firstResultOptional();
    }

    public boolean existsByAppointmentAndUser(Long appointmentId, String userOidcId) {
        return AppointmentParticipation
                .<AppointmentParticipation>find("appointment.id = ?1 AND userOidcId = ?2", appointmentId, userOidcId)
                .count() > 0;
    }

    public List<AppointmentParticipation> listByAppointment(Long appointmentId) {
        return AppointmentParticipation.list("appointment.id = ?1", appointmentId);
    }

    public long deleteByAppointmentAndUser(Long appointmentId, String userOidcId) {
        return AppointmentParticipation
                .delete("appointment.id = ?1 AND userOidcId = ?2", appointmentId, userOidcId);
    }

    public List<Long> findAppointmentIdsByUser(String userOidcId) {
        return AppointmentParticipation.<AppointmentParticipation>list("userOidcId = ?1", userOidcId)
                .stream()
                .map(ap -> ap.getAppointment().id)
                .distinct()
                .toList();
    }

    public List<String> findParticipantOidcIdsByAppointment(Long appointmentId, String excludeOidcId) {
        return AppointmentParticipation.<AppointmentParticipation>list(
                        "appointment.id = ?1 AND userOidcId != ?2", appointmentId, excludeOidcId)
                .stream()
                .map(AppointmentParticipation::getUserOidcId)
                .distinct()
                .toList();
    }

    public void deleteByGroupAndUser(Long groupId, String userOidcId) {
        AppointmentParticipation.delete("groupParticipationId = ?1 AND userOidcId = ?2", groupId, userOidcId);
    }

    public void deleteByGroupAndAppointment(Long groupId, Long appointmentId) {
        AppointmentParticipation.delete("groupParticipationId = ?1 AND appointment.id = ?2", groupId, appointmentId);
    }

    public long countByStatus(ParticipationStatus status) {
        return AppointmentParticipation.count("status", status);
    }

    // ── AppointmentGroupParticipation queries ─────────────────────────────────

    public List<AppointmentGroupParticipation> listGroupParticipationsByGroup(Long groupId) {
        return AppointmentGroupParticipation.list("group.id = ?1", groupId);
    }

    public boolean existsGroupParticipation(Long appointmentId, Long groupId) {
        return AppointmentGroupParticipation
                .<AppointmentGroupParticipation>find("appointment.id = ?1 AND group.id = ?2", appointmentId, groupId)
                .count() > 0;
    }

    public long deleteGroupParticipation(Long appointmentId, Long groupId) {
        return AppointmentGroupParticipation
                .delete("appointment.id = ?1 AND group.id = ?2", appointmentId, groupId);
    }

    public List<AppointmentGroupParticipation> listGroupParticipationsForUser(Long appointmentId, String userOidcId) {
        return AppointmentGroupParticipation.list(
                "SELECT agp FROM AppointmentGroupParticipation agp " +
                        "JOIN agp.group g JOIN g.members m " +
                        "WHERE agp.appointment.id = ?1 AND m.userOidcId = ?2",
                appointmentId, userOidcId);
    }

    public void persistGroupParticipation(AppointmentGroupParticipation agp) {
        agp.persist();
    }
}
