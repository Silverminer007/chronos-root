package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "jakarta")
public interface AppointmentParticipationMapper {

    @Mapping(target = "user_id", source = "userOidcId")
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "profile_picture_url", ignore = true)
    @Mapping(target = "via_group_id", source = "groupParticipationId")
    @Mapping(target = "via_group_name", ignore = true)
    UserParticipantDto toDto(AppointmentParticipation participation);

    List<UserParticipantDto> toDtoList(Set<AppointmentParticipation> participations);
}
