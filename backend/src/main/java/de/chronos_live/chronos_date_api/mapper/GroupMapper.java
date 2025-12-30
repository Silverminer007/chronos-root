package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "cdi",
        uses = {GroupMemberMapper.class}
)
public interface GroupMapper {

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "members", source = "members")
    GroupDto toDto(Group group);

    List<GroupDto> toDtoList(List<Group> groups);

    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "members", source = "members")
    Group toEntity(GroupDto dto);
}