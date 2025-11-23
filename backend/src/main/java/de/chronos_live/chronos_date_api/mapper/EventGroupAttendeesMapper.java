package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.EventAttendeeRole;
import de.chronos_live.chronos_date_api.domain.EventGroupAttendees;
import de.chronos_live.chronos_date_api.presentation.EventGroupAttendeesDto;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "cdi",
        uses = { GroupMapper.class }
)
public interface EventGroupAttendeesMapper {

    // ============================================
    // Entity → DTO
    // ============================================

    @Mapping(target = "group", source = "group")
    @Mapping(target = "role", source = "role")
    EventGroupAttendeesDto toDto(EventGroupAttendees entity, @Context Long currentUserId);

    // overload ohne Context (owner = null)
    @Mapping(target = "group", source = "group")
    @Mapping(target = "role", source = "role")
    EventGroupAttendeesDto toDto(EventGroupAttendees entity);


    // ============================================
    // DTO → Entity
    // ============================================

    @Mapping(target = "group",
            expression = "java(dto.group() != null ? Group.findById(dto.group().id()) : null)")
    @Mapping(target = "event", ignore = true) // Event kommt NICHT aus dem DTO
    @Mapping(target = "role", source = "role")
    EventGroupAttendees toEntity(EventGroupAttendeesDto dto);


    // ============================================
    // Enum Mapping
    // ============================================

    default String map(EventAttendeeRole role) {
        return role != null ? role.name() : null;
    }

    default EventAttendeeRole map(String role) {
        return role != null ? EventAttendeeRole.valueOf(role) : null;
    }


    // ============================================
    // Collections
    // ============================================

    List<EventGroupAttendeesDto> toDtoList(List<EventGroupAttendees> list, @Context Long currentUserId);
    List<EventGroupAttendeesDto> toDtoList(List<EventGroupAttendees> list);

    List<EventGroupAttendees> toEntityList(List<EventGroupAttendeesDto> list);
}