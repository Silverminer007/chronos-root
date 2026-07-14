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
    @Mapping(target = "id", source = "userOidcId")
    @Mapping(target = "first_name", ignore = true)
    @Mapping(target = "last_name", ignore = true)
    UserDto toDto(GroupMember groupMember);

    // ============================
    // Collections
    // ============================
    List<UserDto> toDtoList(List<GroupMember> groupMemberList);
}
