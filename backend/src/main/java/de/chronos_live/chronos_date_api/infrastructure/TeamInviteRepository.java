package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.TeamInvite;
import de.chronos_live.chronos_date_api.domain.TeamInviteStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TeamInviteRepository implements PanacheRepository<TeamInvite> {

    public Optional<TeamInvite> findByToken(String token) {
        return TeamInvite.<TeamInvite>find("token = ?1", token).firstResultOptional();
    }

    public List<TeamInvite> findByTeam(Long teamId) {
        return TeamInvite.<TeamInvite>find("team.id = ?1", teamId).list();
    }

    public List<TeamInvite> findActiveByTeam(Long teamId) {
        return TeamInvite.<TeamInvite>find(
                "team.id = ?1 AND status = ?2", teamId, TeamInviteStatus.ACTIVE
        ).list();
    }
}
