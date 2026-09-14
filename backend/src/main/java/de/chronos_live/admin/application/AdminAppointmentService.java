package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.dto.AppointmentDto;
import de.chronos_live.chronos_date_api.dto.PagedResponse;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import de.chronos_live.chronos_date_api.mapper.AppointmentMapper;
import io.micrometer.core.annotation.Timed;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Service for admin appointment operations.
 *
 * <p>Provides access to all appointments in the system for administrative purposes.
 */
@ApplicationScoped
@Transactional
@Timed("service.admin.appointments")
public class AdminAppointmentService {

    @Inject
    AppointmentRepository appointmentRepository;

    @Inject
    AppointmentMapper appointmentMapper;

    public PagedResponse<AppointmentDto> listAppointments(int page, int size) {
        Page pageRequest = Page.of(page, size);
        var appointments = appointmentRepository.findAll().page(pageRequest).list();
        long total = appointmentRepository.count();

        List<AppointmentDto> items = appointments.stream()
                .map(appointmentMapper::toDto)
                .toList();

        return new PagedResponse<>(
                items,
                new PagedResponse.Meta(page, size, total)
        );
    }
}
