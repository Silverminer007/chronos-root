package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.Team;
import de.chronos_live.chronos_date_api.domain.TeamMember;
import de.chronos_live.chronos_date_api.domain.TeamRole;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class TeamRepository implements PanacheRepository<Team> {

    // ── Team queries ──────────────────────────────────────────────────────────

    public List<Team> findTeamsForUser(String userOidcId) {
        return Team.<Team>find(
                "SELECT t FROM TeamMember tm JOIN tm.team t WHERE tm.userOidcId = ?1",
                userOidcId
        ).list();
    }

    // ── TeamMember queries ────────────────────────────────────────────────────

    public boolean isMember(Long teamId, String userOidcId) {
        return TeamMember.count("team.id = ?1 AND userOidcId = ?2", teamId, userOidcId) > 0;
    }

    public boolean shareTeam(String oidcId1, String oidcId2) {
        return TeamMember.<TeamMember>find("userOidcId = ?1", oidcId1)
                .stream()
                .anyMatch(tm -> TeamMember.count(
                        "team.id = ?1 AND userOidcId = ?2", tm.getTeam().id, oidcId2) > 0);
    }

    public Optional<TeamMember> findMember(Long teamId, String userOidcId) {
        return TeamMember.<TeamMember>find(
                "team.id = ?1 AND userOidcId = ?2", teamId, userOidcId
        ).firstResultOptional();
    }

    public List<TeamMember> listMembers(Long teamId) {
        return TeamMember.<TeamMember>find("team.id = ?1", teamId).list();
    }

    public boolean isAdminOrOwner(Long teamId, String userOidcId) {
        return TeamMember.count(
                "team.id = ?1 AND userOidcId = ?2 AND (role = ?3 OR role = ?4)",
                teamId, userOidcId, TeamRole.ADMIN, TeamRole.OWNER
        ) > 0;
    }

    public boolean isOwner(Long teamId, String userOidcId) {
        return TeamMember.count(
                "team.id = ?1 AND userOidcId = ?2 AND role = ?3",
                teamId, userOidcId, TeamRole.OWNER
        ) > 0;
    }

    public void persistMember(TeamMember member) {
        member.persist();
    }

    public void deleteMember(Long teamId, String userOidcId) {
        TeamMember.delete("team.id = ?1 AND userOidcId = ?2", teamId, userOidcId);
    }

    public Set<Long> findTeamIds(String userOidcId) {
        return TeamMember.<TeamMember>find("userOidcId = ?1", userOidcId)
                .stream()
                .map(tm -> tm.getTeam().id)
                .collect(java.util.stream.Collectors.toSet());
    }
}
