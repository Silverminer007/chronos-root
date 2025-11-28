package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.presentation.GroupDto;
import de.chronos_live.chronos_date_api.presentation.GroupWithMembersDto;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "cdi",
        uses = {UserMapper.class}
)
public interface GroupMapper {

    // ---- Einzelobjekt ----
    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "owner",
            expression = "java(group.getOwner() != null && currentUserId != null && group.getOwner().id.equals(currentUserId))")
    @Named("withOwner")
    GroupDto toDtoWithOwner(Group group, @Context Long currentUserId);

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "owner", ignore = true)
    @Named("withoutOwner")
    GroupDto toDto(Group group);

    // ---- Liste mit @Named ----
    @IterableMapping(qualifiedByName = "withOwner")
    List<GroupDto> toDtoListWithOwner(List<Group> groups, @Context Long currentUserId);

    @IterableMapping(qualifiedByName = "withoutOwner")
    List<GroupDto> toDtoList(List<Group> groups);

    // =====================================
    // Group → GroupWithMembersDto
    // =====================================
    @Named("withOwnerMembers")
    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "members", source = "members")
    @Mapping(target = "owner",
            expression = "java(group.getOwner() != null && currentUserId != null && group.getOwner().id.equals(currentUserId))")
    GroupWithMembersDto toDtoWithMembersWithOwner(Group group, @Context Long currentUserId);

    @Mapping(target = "name", source = "groupName")
    @Mapping(target = "members", source = "members")
    @Mapping(target = "owner", ignore = true)
    @Named("withoutOwnerMembers")
    GroupWithMembersDto toDtoWithMembers(Group group);

    // ---- Listen mit @Named ----
    @IterableMapping(qualifiedByName = "withOwnerMembers")
    List<GroupWithMembersDto> toDtoWithMembersListWithOwner(List<Group> groups, @Context Long currentUserId);

    @IterableMapping(qualifiedByName = "withoutOwnerMembers")
    List<GroupWithMembersDto> toDtoWithMembersList(List<Group> groups);

    // =====================================
    // DTO → Entity
    // =====================================
    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", ignore = true)
    Group toEntity(GroupDto dto);

    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", ignore = true)
    Group toEntity(GroupWithMembersDto dto);
}