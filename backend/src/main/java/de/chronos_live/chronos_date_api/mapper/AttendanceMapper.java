package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Attendance;
import de.chronos_live.chronos_date_api.domain.AttendanceStatus;
import de.chronos_live.chronos_date_api.presentation.AttendanceDto;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface AttendanceMapper {

    AttendanceMapper INSTANCE = Mappers.getMapper(AttendanceMapper.class);

    // ============================
    // Entity → DTO
    // ============================
    @Mapping(target = "user_name", source = "java(dto.getName())")
    @Mapping(target = "event_id", source = "event.id")
    @Mapping(target = "status", source = "status")
    AttendanceDto toDto(Attendance attendance);


    // ============================
    // Enum Mapping
    // ============================
    default String map(AttendanceStatus status) {
        return status != null ? status.name() : null;
    }

    default AttendanceStatus map(String status) {
        return status != null ? AttendanceStatus.valueOf(status) : null;
    }

    // ============================
    // Collections
    // ============================
    List<AttendanceDto> toDtoList(List<Attendance> attendances);
}