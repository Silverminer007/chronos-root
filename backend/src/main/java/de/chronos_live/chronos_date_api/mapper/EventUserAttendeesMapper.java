package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.EventUserAttendees;
import de.chronos_live.chronos_date_api.domain.EventAttendeeRole;
import de.chronos_live.chronos_date_api.presentation.EventUserAttendeesDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(
        componentModel = "cdi",
        uses = { UserMapper.class }
)
public interface EventUserAttendeesMapper {

    @Mapping(target = "role", source = "role", qualifiedByName = "roleToString")
    EventUserAttendeesDto toDto(EventUserAttendees entity);

    @Mapping(target = "role", source = "role", qualifiedByName = "stringToRole")
    EventUserAttendees toEntity(EventUserAttendeesDto dto);


    // ---- Custom Converters ----

    @Named("roleToString")
    default String roleToString(EventAttendeeRole role) {
        return role != null ? role.name() : null;
    }

    @Named("stringToRole")
    default EventAttendeeRole stringToRole(String role) {
        return role != null ? EventAttendeeRole.valueOf(role) : null;
    }


    List<EventUserAttendeesDto> toDtoList(List<EventUserAttendees> list);
    List<EventUserAttendees> toEntityList(List<EventUserAttendeesDto> list);
}