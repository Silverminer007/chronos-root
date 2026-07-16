package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface PrincipalMapper {

    @Mapping(target = "id", source = "oidcId")
    @Mapping(target = "first_name", source = "firstName")
    @Mapping(target = "last_name", source = "lastName")
    PrincipalDto toDto(UserIdentity identity);
}
