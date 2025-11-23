package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.presentation.GroupDto;
import de.chronos_live.chronos_date_api.presentation.GroupWithMembersDto;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "cdi",
        uses = { UserMapper.class }
)
public interface GroupMapper {

    // =====================================
    // Group → GroupDto (simple)
    // =====================================

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "owner",
            expression = "java(group.getOwner() != null && currentUserId != null && group.getOwner().id.equals(currentUserId))")
    GroupDto toDto(Group group, @Context Long currentUserId);

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "owner", ignore = true)
    GroupDto toDto(Group group);

    // =====================================
    // Group → GroupWithMembersDto
    // =====================================

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "members", source = "members")
    @Mapping(target = "owner",
            expression = "java(group.getOwner() != null && currentUserId != null && group.getOwner().id.equals(currentUserId))")
    GroupWithMembersDto toDtoWithMembers(Group group, @Context Long currentUserId);

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", source = "members")
    GroupWithMembersDto toDtoWithMembers(Group group);

    // =====================================
    // DTO → Entity
    // =====================================

    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", ignore = true)
    Group toEntity(GroupDto dto);

    // Wird wahrscheinlich selten benötigt:
    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", ignore = true)
    Group toEntity(GroupWithMembersDto dto);

    // =====================================
    // Collections
    // =====================================

    List<GroupDto> toDtoList(List<Group> groups, @Context Long currentUserId);
    List<GroupDto> toDtoList(List<Group> groups);

    List<GroupWithMembersDto> toDtoWithMembersList(List<Group> groups, @Context Long currentUserId);
    List<GroupWithMembersDto> toDtoWithMembersList(List<Group> groups);

    List<Group> toEntityList(List<GroupDto> dtos);
    List<Group> toEntityListFromWithMembers(List<GroupWithMembersDto> dtos);
}