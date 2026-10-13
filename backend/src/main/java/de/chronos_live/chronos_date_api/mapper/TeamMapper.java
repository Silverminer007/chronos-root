package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Team;
import de.chronos_live.chronos_date_api.dto.TeamDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "jakarta")
public interface TeamMapper {

    @Mapping(target = "members", ignore = true)
    TeamDto toDto(Team team);

    List<TeamDto> toDtoList(List<Team> teams);
}
