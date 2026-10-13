package de.chronos_live.chronos_date_api.application.events;

import de.chronos_live.chronos_date_api.domain.TeamRole;

public record TeamMemberRoleChangedEvent(Long teamId, String targetUserOidcId, TeamRole oldRole, TeamRole newRole, String actingUserOidcId) {
}
