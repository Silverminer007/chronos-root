package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AppointmentRepository implements PanacheRepository<Appointment> {

    @SuppressWarnings("unchecked")
    public List<Appointment> findMatchingAppointments(
            int baseMinutes,
            int maxDays,
            int minAppointmentLength,
            int offsetWeeks
    ) {

        int maxMinutes = maxDays * 24 * 60;

        return getEntityManager()
                .createNativeQuery("""
                SELECT *
                FROM appointment a
                WHERE
                    a.end_time > a.start_time + (INTERVAL '1 hour' * :minAppointmentLength)
                AND
                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) > NOW()
                AND
                    EXTRACT(EPOCH FROM (
                        a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                    )) / 60 <= :maxMinutes
                AND
                    EXISTS (
                        SELECT 1
                        FROM generate_series(0, 20) AS n
                        WHERE
                            ABS(
                                EXTRACT(EPOCH FROM (
                                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                                )) / 60
                                - (:baseMinutes * POWER(2, n))
                            ) < :baseMinutes
                    )
            """, Appointment.class)
                .setParameter("baseMinutes", baseMinutes)
                .setParameter("maxMinutes", maxMinutes)
                .setParameter("minAppointmentLength", minAppointmentLength)
                .setParameter("offsetWeeks", offsetWeeks)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Appointment> findMatchingWeekdayAppointments(
            int baseMinutes,
            int maxDays,
            int maxAppointmentLength,
            int offsetWeeks
    ) {

        int maxMinutes = maxDays * 24 * 60;

        return getEntityManager()
                .createNativeQuery("""
                SELECT *
                FROM appointment a
                WHERE
                    a.end_time < a.start_time + (INTERVAL '1 hour' * :maxAppointmentLength)
                AND
                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) > NOW()
                AND
                    EXTRACT(EPOCH FROM (
                        a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                    )) / 60 <= :maxMinutes
                AND
                      -- Montag (1) bis Donnerstag (4) in UTC
                    EXTRACT(ISODOW FROM (a.start_time AT TIME ZONE 'UTC')) BETWEEN 1 AND 4
                AND
                    EXISTS (
                        SELECT 1
                        FROM generate_series(0, 20) AS n
                        WHERE
                            ABS(
                                EXTRACT(EPOCH FROM (
                                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                                )) / 60
                                - (:baseMinutes * POWER(2, n))
                            ) < :baseMinutes
                    )
            """, Appointment.class)
                .setParameter("baseMinutes", baseMinutes)
                .setParameter("maxMinutes", maxMinutes)
                .setParameter("maxAppointmentLength", maxAppointmentLength)
                .setParameter("offsetWeeks", offsetWeeks)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Appointment> findMatchingWeekendAppointments(
            int baseMinutes,
            int maxDays,
            int maxAppointmentLength,
            int offsetWeeks
    ) {

        int maxMinutes = maxDays * 24 * 60;

        return getEntityManager()
                .createNativeQuery("""
                SELECT *
                FROM appointment a
                WHERE
                    a.end_time < a.start_time + (INTERVAL '1 hour' * :maxAppointmentLength)
                AND
                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) > NOW()
                AND
                    EXTRACT(EPOCH FROM (
                        a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                    )) / 60 <= :maxMinutes
                AND
                      -- Freitag (5) bis Sonntag (7) in UTC
                    EXTRACT(ISODOW FROM (a.start_time AT TIME ZONE 'UTC')) BETWEEN 5 AND 7
                AND
                    EXISTS (
                        SELECT 1
                        FROM generate_series(0, 20) AS n
                        WHERE
                            ABS(
                                EXTRACT(EPOCH FROM (
                                    a.start_time - (INTERVAL '1 week' * :offsetWeeks) - NOW()
                                )) / 60
                                - (:baseMinutes * POWER(2, n))
                            ) < :baseMinutes
                    )
            """, Appointment.class)
                .setParameter("baseMinutes", baseMinutes)
                .setParameter("maxMinutes", maxMinutes)
                .setParameter("maxAppointmentLength", maxAppointmentLength)
                .setParameter("offsetWeeks", offsetWeeks)
                .getResultList();
    }

    // ── JPQL queries ──────────────────────────────────────────────────────────

    @Override
    public Appointment findById(Long id) {
        return Appointment.findById(id);
    }

    public Appointment getAppointment(Long appointmentId,
                                      boolean messages, boolean participants, boolean groupParticipants) {
        String sql = "SELECT a FROM Appointment a";
        if (messages) sql += " LEFT JOIN FETCH a.messages";
        if (participants) sql += " LEFT JOIN FETCH a.participants p";
        if (groupParticipants) sql += " LEFT JOIN FETCH a.groupParticipants gp LEFT JOIN FETCH gp.group g LEFT JOIN FETCH g.members";
        sql += " WHERE a.id = ?1 AND a.status != ?2";
        return Appointment.<Appointment>find(sql, appointmentId, AppointmentStatus.DELETED)
                .firstResultOptional()
                .orElseThrow(() -> new ResourceNotFoundException("appointment", appointmentId));
    }

    public record SearchResult(List<Appointment> items, long total) {
        public SearchResult {
            items = List.copyOf(items);
        }
    }

    public SearchResult search(String requestingUserOidcId, String query,
                               Instant after, Instant before, int page, int pageSize,
                               boolean messages, boolean participants, boolean groupParticipants) {
        String baseJoin = " FROM AppointmentParticipation ap JOIN ap.appointment a";
        String fetchJoins = "";
        if (messages) fetchJoins += " LEFT JOIN FETCH a.messages";
        if (participants) fetchJoins += " LEFT JOIN FETCH a.participants part";
        if (groupParticipants) fetchJoins += " LEFT JOIN FETCH a.groupParticipants";

        String where = " WHERE ap.userOidcId = ?1 AND a.endTime > ?2 AND a.startTime < ?3 AND a.status != ?4";
        if (query != null) {
            where += " AND (lower(a.name) LIKE lower(?5) OR lower(a.description) LIKE lower(?5) OR lower(a.venue) LIKE lower(?5))";
        }

        Object[] params = query != null
                ? new Object[]{requestingUserOidcId, after, before, AppointmentStatus.DELETED, "%" + query + "%"}
                : new Object[]{requestingUserOidcId, after, before, AppointmentStatus.DELETED};

        long total = Appointment.count("SELECT COUNT(DISTINCT a)" + baseJoin + where, params);
        List<Appointment> items = Appointment.<Appointment>find(
                "SELECT a" + baseJoin + fetchJoins + where + " ORDER BY a.startTime", params
        ).page(page, pageSize).list();
        return new SearchResult(items, total);
    }

    public Optional<Appointment> findActiveById(Long id) {
        return Appointment.<Appointment>findByIdOptional(id);
    }

    public List<Appointment> findNonCancelledBetween(Instant after, Instant before) {
        return Appointment.<Appointment>find(
                "startTime < ?1 AND startTime > ?2 AND status != ?3 AND status != ?4",
                before, after, AppointmentStatus.DELETED, AppointmentStatus.CANCELLED
        ).list();
    }

    public List<Appointment> findPlannedBetween(Instant after, Instant before) {
        return Appointment.<Appointment>find(
                "startTime < ?1 AND startTime > ?2 AND status = ?3",
                before, after, AppointmentStatus.PLANNED
        ).list();
    }

    public long countByStatus(AppointmentStatus status) {
        return Appointment.count("status", status);
    }
}