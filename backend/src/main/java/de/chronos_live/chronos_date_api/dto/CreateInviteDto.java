package de.chronos_live.chronos_date_api.dto;

import de.chronos_live.chronos_date_api.domain.TeamInviteType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateInviteDto {
    private TeamInviteType type;
    private String targetEmail;
    /**
     * Expiry duration in hours. Valid values: 24, 168 (7 days), 720 (30 days).
     * Only relevant for MULTI_USE links.
     */
    private Integer expiryHours;
}
