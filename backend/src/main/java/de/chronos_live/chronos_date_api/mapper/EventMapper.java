package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.EventStatus;
import de.chronos_live.chronos_date_api.presentation.EventDto;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Mapper(componentModel = "cdi")
public interface EventMapper {

    EventMapper INSTANCE = Mappers.getMapper(EventMapper.class);

    DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ============================
    // Entity → DTO
    // ============================
    @Mapping(target = "status", source = "eventStatus")
    @Mapping(target = "start", expression = "java(event.getStartTime() != null ? event.getStartTime().toString() : null)")
    @Mapping(target = "end", expression = "java(event.getEndTime() != null ? event.getEndTime().toString() : null)")
    EventDto toDto(Event event);

    // ============================
    // DTO → Entity
    // ============================
    @Mapping(target = "eventStatus", source = "status")
    @Mapping(target = "startTime", expression = "java(dto.start() != null ? java.time.Instant.parse(dto.start()) : null)")
    @Mapping(target = "endTime", expression = "java(dto.end() != null ? java.time.Instant.parse(dto.end()) : null)")
    @Mapping(target = "lastUpdate", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    Event toEntity(EventDto dto);

    List<EventDto> toDtoList(List<Event> events);
    List<Event> toEntityList(List<EventDto> dtoList);

    // ============================
    // Enum Mapping
    // ============================
    default String map(EventStatus status) {
        return status != null ? status.name() : null;
    }

    default EventStatus map(String status) {
        return status != null ? EventStatus.valueOf(status) : null;
    }
}