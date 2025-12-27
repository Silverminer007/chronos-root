package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

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
}