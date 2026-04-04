package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "jakarta")
public interface GroupMemberMapper {

    // ============================
    // Entity → DTO
    // ============================
    @Mapping(target = "first_name", source = "user.firstName")
    @Mapping(target = "last_name", source = "user.lastName")
    UserDto toDto(GroupMember groupMember);

    // ============================
    // Collections
    // ============================
    List<UserDto> toDtoList(List<GroupMember> groupMemberList);
}
