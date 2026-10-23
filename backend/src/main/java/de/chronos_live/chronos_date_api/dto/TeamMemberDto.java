package de.chronos_live.chronos_date_api.dto;

import de.chronos_live.chronos_date_api.domain.TeamRole;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TeamMemberDto {
    private String userId;
    private String firstName;
    private String lastName;
    private TeamRole role;
}
