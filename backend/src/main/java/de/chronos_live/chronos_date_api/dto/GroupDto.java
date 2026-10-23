package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class GroupDto {
    private Long id;
    private String name;
    private Long teamId;
    private List<UserDto> members;
}