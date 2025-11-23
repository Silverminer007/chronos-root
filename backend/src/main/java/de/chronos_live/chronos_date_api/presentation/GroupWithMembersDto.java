package de.chronos_live.chronos_date_api.presentation;

import java.util.List;

public record GroupWithMembersDto(Long id, String name, Boolean owner, List<UserDto> members) {}