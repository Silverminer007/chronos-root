package de.chronos_live.chronos_date_api.dto;

import de.chronos_live.chronos_date_api.domain.TeamRole;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateMemberRoleDto {
    private TeamRole role;
}
