package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(
        componentModel = "jakarta",
        uses = {GroupMemberMapper.class}
)
public interface GroupMapper {

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "teamId", source = "team.id")
    @Mapping(target = "members", source = "members")
    GroupDto toDto(Group group);

    List<GroupDto> toDtoList(List<Group> groups);

    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "members", source = "members")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "team", ignore = true)
    Group toEntity(GroupDto dto);
}
