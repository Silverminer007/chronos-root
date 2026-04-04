package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.AppointmentGroupParticipation;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(
        componentModel = "jakarta",
        uses = {GroupMemberMapper.class}
)
public interface AppointmentGroupParticipationMapper {

    @Mapping(target = "name", source = "group.groupName")
    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "members", source = "group.members")
    GroupDto toDto(AppointmentGroupParticipation group);

    List<GroupDto> toDtoList(List<Group> groups);
}
