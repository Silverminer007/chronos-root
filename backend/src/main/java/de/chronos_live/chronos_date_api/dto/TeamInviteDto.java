package de.chronos_live.chronos_date_api.dto;

import de.chronos_live.chronos_date_api.domain.TeamInviteStatus;
import de.chronos_live.chronos_date_api.domain.TeamInviteType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class TeamInviteDto {
    private Long id;
    private String token;
    private TeamInviteType type;
    private String targetEmail;
    private Instant expiresAt;
    private TeamInviteStatus status;
    private int useCount;
    private Instant createdAt;
}
