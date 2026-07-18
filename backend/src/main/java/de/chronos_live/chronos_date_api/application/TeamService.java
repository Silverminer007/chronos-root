package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.TeamCreatedEvent;
import de.chronos_live.chronos_date_api.application.events.TeamMemberJoinedEvent;
import de.chronos_live.chronos_date_api.application.events.TeamMemberRoleChangedEvent;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.Team;
import de.chronos_live.chronos_date_api.domain.TeamMember;
import de.chronos_live.chronos_date_api.domain.TeamRole;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.TeamDto;
import de.chronos_live.chronos_date_api.dto.TeamMemberDto;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.TeamRepository;
import de.chronos_live.chronos_date_api.mapper.TeamMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
@Timed("service.team")
public class TeamService {
    private static final Logger LOGGER = Logger.getLogger(TeamService.class);

    @Inject
    TeamRepository teamRepository;
    @Inject
    TeamMapper teamMapper;
    @Inject
    IdentityPort identityPort;
    @Inject
    Event<TeamCreatedEvent> teamCreatedEvent;
    @Inject
    Event<TeamMemberJoinedEvent> teamMemberJoinedEvent;
    @Inject
    Event<TeamMemberRoleChangedEvent> teamMemberRoleChangedEvent;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onTeamCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) TeamCreatedEvent event) {
        LOGGER.infof("Team %s was created, adding creator as OWNER", event.teamId());
        TeamMember member = new TeamMember();
        member.setTeam(teamRepository.findById(event.teamId()));
        member.setUserOidcId(event.actingUserOidcId());
        member.setRole(TeamRole.OWNER);
        member.setJoinedAt(Instant.now());
        teamRepository.persistMember(member);
    }

    public TeamDto createTeam(String actingUserOidcId, String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Team name is required");
        }
        LOGGER.debugf("[Principal %s] Creating Team: %s", actingUserOidcId, name);

        Team team = new Team();
        team.setName(name.trim());
        team.setCreatedAt(Instant.now());
        teamRepository.persist(team);

        teamCreatedEvent.fire(new TeamCreatedEvent(team.id, actingUserOidcId));
        return teamMapper.toDto(team);
    }

    public List<TeamDto> listTeamsForUser(String userOidcId) {
        LOGGER.debugf("[Principal %s] Listing teams", userOidcId);
        List<Team> teams = teamRepository.findTeamsForUser(userOidcId);
        List<TeamDto> dtos = teamMapper.toDtoList(teams);

        for (int i = 0; i < teams.size(); i++) {
            dtos.get(i).setMembers(buildMemberDtos(teams.get(i).id));
        }
        return dtos;
    }

    public TeamDto getTeam(String requestingUserOidcId, Long teamId) {
        LOGGER.debugf("[Principal %s][Team %s] Getting team", requestingUserOidcId, teamId);
        requireMember(teamId, requestingUserOidcId);
        Team team = teamRepository.findById(teamId);
        if (team == null) {
            throw new ResourceNotFoundException("team", teamId);
        }
        TeamDto dto = teamMapper.toDto(team);
        dto.setMembers(buildMemberDtos(teamId));
        return dto;
    }

    public void updateMemberRole(String actingUserOidcId, Long teamId, String targetUserOidcId, TeamRole newRole) {
        LOGGER.debugf("[Principal %s][Team %s][Target %s] Updating role to %s",
                actingUserOidcId, teamId, targetUserOidcId, newRole);

        TeamMember actingMember = teamRepository.findMember(teamId, actingUserOidcId)
                .orElseThrow(() -> new ForbiddenException("Du bist kein Mitglied dieses Teams"));
        TeamMember targetMember = teamRepository.findMember(teamId, targetUserOidcId)
                .orElseThrow(() -> new ResourceNotFoundException("Mitglied nicht gefunden"));

        if (newRole == TeamRole.OWNER) {
            throw new ValidationException("Nutze 'Ownership übertragen' um die Owner-Rolle zu vergeben");
        }

        TeamRole actingRole = actingMember.getRole();
        TeamRole currentTargetRole = targetMember.getRole();

        if (actingRole == TeamRole.OWNER) {
            if (actingUserOidcId.equals(targetUserOidcId)) {
                throw new ValidationException("Nutze 'Ownership übertragen' um die Owner-Rolle abzugeben");
            }
        } else if (actingRole == TeamRole.ADMIN) {
            if (currentTargetRole == TeamRole.OWNER) {
                throw new ForbiddenException("Der Owner kann nicht verwaltet werden");
            }
            if (currentTargetRole == TeamRole.ADMIN) {
                throw new ForbiddenException("Nur der Team-Owner kann Admins verwalten");
            }
        } else {
            throw new ForbiddenException("Nur Admins und der Owner können Rollen verwalten");
        }

        TeamRole oldRole = targetMember.getRole();
        targetMember.setRole(newRole);
        teamMemberRoleChangedEvent.fire(new TeamMemberRoleChangedEvent(teamId, targetUserOidcId, oldRole, newRole, actingUserOidcId));
    }

    public void removeMember(String actingUserOidcId, Long teamId, String targetUserOidcId) {
        LOGGER.debugf("[Principal %s][Team %s][Target %s] Removing member",
                actingUserOidcId, teamId, targetUserOidcId);

        TeamMember actingMember = teamRepository.findMember(teamId, actingUserOidcId)
                .orElseThrow(() -> new ForbiddenException("Du bist kein Mitglied dieses Teams"));
        TeamMember targetMember = teamRepository.findMember(teamId, targetUserOidcId)
                .orElseThrow(() -> new ResourceNotFoundException("Mitglied nicht gefunden"));

        TeamRole actingRole = actingMember.getRole();
        TeamRole targetRole = targetMember.getRole();

        if (targetRole == TeamRole.OWNER) {
            throw new ForbiddenException("Der Eigentümer kann nicht entfernt werden");
        }
        if (actingUserOidcId.equals(targetUserOidcId)) {
            throw new ValidationException("Nutze 'Team verlassen' um das Team zu verlassen");
        }
        if (actingRole == TeamRole.ADMIN && targetRole == TeamRole.ADMIN) {
            throw new ForbiddenException("Nur der Team-Eigentümer kann Admins entfernen");
        }
        if (actingRole == TeamRole.MEMBER) {
            throw new ForbiddenException("Nur Admins und der Eigentümer können Mitglieder entfernen");
        }

        teamRepository.deleteMember(teamId, targetUserOidcId);
    }

    public void transferOwnership(String actingUserOidcId, Long teamId, String targetUserOidcId) {
        LOGGER.debugf("[Principal %s][Team %s] Transferring ownership to %s",
                actingUserOidcId, teamId, targetUserOidcId);

        TeamMember actingMember = teamRepository.findMember(teamId, actingUserOidcId)
                .orElseThrow(() -> new ForbiddenException("Du bist kein Mitglied dieses Teams"));

        if (actingMember.getRole() != TeamRole.OWNER) {
            throw new ForbiddenException("Nur der Owner kann die Ownership übertragen");
        }
        if (actingUserOidcId.equals(targetUserOidcId)) {
            throw new ValidationException("Du kannst die Ownership nicht an dich selbst übertragen");
        }

        TeamMember targetMember = teamRepository.findMember(teamId, targetUserOidcId)
                .orElseThrow(() -> new ResourceNotFoundException("Mitglied nicht gefunden"));

        TeamRole oldTargetRole = targetMember.getRole();
        actingMember.setRole(TeamRole.ADMIN);
        targetMember.setRole(TeamRole.OWNER);

        teamMemberRoleChangedEvent.fire(new TeamMemberRoleChangedEvent(
                teamId, actingUserOidcId, TeamRole.OWNER, TeamRole.ADMIN, actingUserOidcId));
        teamMemberRoleChangedEvent.fire(new TeamMemberRoleChangedEvent(
                teamId, targetUserOidcId, oldTargetRole, TeamRole.OWNER, actingUserOidcId));
    }

    private List<TeamMemberDto> buildMemberDtos(Long teamId) {
        List<TeamMember> members = teamRepository.listMembers(teamId);
        Map<String, UserIdentity> identities = identityPort.findByIds(
                members.stream().map(TeamMember::getUserOidcId).toList()
        );
        return members.stream().map(m -> {
            TeamMemberDto dto = new TeamMemberDto();
            dto.setUserId(m.getUserOidcId());
            dto.setRole(m.getRole());
            UserIdentity identity = identities.get(m.getUserOidcId());
            if (identity != null) {
                dto.setFirstName(identity.firstName());
                dto.setLastName(identity.lastName());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private void requireMember(Long teamId, String userOidcId) {
        if (!teamRepository.isMember(teamId, userOidcId)) {
            throw new ForbiddenException("Du bist kein Mitglied dieses Teams");
        }
    }
}
