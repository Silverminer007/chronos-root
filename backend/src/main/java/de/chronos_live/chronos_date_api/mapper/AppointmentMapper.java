package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.dto.AppointmentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface AppointmentMapper {

    // ============================
    // Entity → DTO
    // ============================
    @Mapping(target = "start", expression = "java(appointment.getStartTime() != null ? appointment.getStartTime().toString() : null)")
    @Mapping(target = "end", expression = "java(appointment.getEndTime() != null ? appointment.getEndTime().toString() : null)")
    @Mapping(target = "minimal_attendees", source = "minimalAttendees")
    @Mapping(target = "group_participants", source = "groupParticipants")
    AppointmentDto toDto(Appointment appointment);

    List<AppointmentDto> toDtoList(List<Appointment> appointments);

    // ============================
    // Enum Mapping
    // ============================
    default String map(AppointmentStatus status) {
        return status != null ? status.name() : null;
    }
}