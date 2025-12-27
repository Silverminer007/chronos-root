package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface PrincipalMapper {

    // ============================
    // Entity → DTO
    // ============================
    @Mapping(target = "first_name", source = "firstName")
    @Mapping(target = "last_name", source = "lastName")
    PrincipalDto toDto(User user);

    // ============================
    // DTO → Entity
    // ============================
    @Mapping(target = "firstName", source = "first_name")
    @Mapping(target = "lastName", source = "last_name")
    @Mapping(target = "oidcId", ignore = true) // optional, kann auch entfernt werden
    User toEntity(PrincipalDto dto);

    // ============================
    // Collections
    // ============================
    List<PrincipalDto> toDtoList(List<User> users);
    List<User> toEntityList(List<PrincipalDto> dtoList);
}