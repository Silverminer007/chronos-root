package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.TeamInvite;
import de.chronos_live.chronos_date_api.dto.TeamInviteDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "jakarta")
public interface TeamInviteMapper {

    TeamInviteDto toDto(TeamInvite invite);

    List<TeamInviteDto> toDtoList(List<TeamInvite> invites);
}
