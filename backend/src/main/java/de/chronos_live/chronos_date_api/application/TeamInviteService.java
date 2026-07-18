package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.TeamMemberJoinedEvent;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.CreateInviteDto;
import de.chronos_live.chronos_date_api.dto.TeamInviteDto;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.TeamInviteRepository;
import de.chronos_live.chronos_date_api.infrastructure.TeamRepository;
import de.chronos_live.chronos_date_api.mapper.TeamInviteMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
@Timed("service.team_invite")
public class TeamInviteService {
    private static final Logger LOGGER = Logger.getLogger(TeamInviteService.class);

    @Inject
    TeamRepository teamRepository;
    @Inject
    TeamInviteRepository inviteRepository;
    @Inject
    TeamInviteMapper inviteMapper;
    @Inject
    Event<TeamMemberJoinedEvent> teamMemberJoinedEvent;

    public List<TeamInviteDto> listInvites(String actingUserOidcId, Long teamId) {
        requireAdminOrOwner(teamId, actingUserOidcId);
        return inviteMapper.toDtoList(inviteRepository.findByTeam(teamId));
    }

    public TeamInviteDto createInvite(String actingUserOidcId, Long teamId, CreateInviteDto dto) {
        requireAdminOrOwner(teamId, actingUserOidcId);

        if (dto.getType() == null) {
            throw new ValidationException("Link-Typ ist erforderlich");
        }

        Team team = teamRepository.findById(teamId);
        if (team == null) {
            throw new ResourceNotFoundException("team", teamId);
        }

        TeamInvite invite = new TeamInvite();
        invite.setTeam(team);
        invite.setToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setType(dto.getType());
        invite.setStatus(TeamInviteStatus.ACTIVE);
        invite.setUseCount(0);
        invite.setCreatedByOidcId(actingUserOidcId);
        invite.setCreatedAt(Instant.now());

        if (dto.getType() == TeamInviteType.MULTI_USE) {
            int hours = dto.getExpiryHours() != null ? dto.getExpiryHours() : 24;
            if (hours != 24 && hours != 168 && hours != 720) {
                throw new ValidationException("Ungültige Ablaufzeit. Erlaubt: 24, 168 oder 720 Stunden");
            }
            invite.setExpiresAt(Instant.now().plus(hours, ChronoUnit.HOURS));
        }

        if (dto.getType() == TeamInviteType.SINGLE_USE) {
            invite.setTargetEmail(dto.getTargetEmail());
        }

        inviteRepository.persist(invite);
        LOGGER.debugf("[Principal %s][Team %s] Created %s invite", actingUserOidcId, teamId, dto.getType());
        return inviteMapper.toDto(invite);
    }

    public void revokeInvite(String actingUserOidcId, Long teamId, Long inviteId) {
        requireAdminOrOwner(teamId, actingUserOidcId);

        TeamInvite invite = inviteRepository.findByIdOptional(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Einladungslink", inviteId));

        if (!invite.getTeam().id.equals(teamId)) {
            throw new ForbiddenException("Dieser Link gehört nicht zu deinem Team");
        }
        if (invite.getStatus() != TeamInviteStatus.ACTIVE) {
            throw new ValidationException("Dieser Link ist bereits " +
                    (invite.getStatus() == TeamInviteStatus.REVOKED ? "widerrufen" : "eingelöst"));
        }

        invite.setStatus(TeamInviteStatus.REVOKED);
        LOGGER.debugf("[Principal %s][Team %s][Invite %s] Revoked invite", actingUserOidcId, teamId, inviteId);
    }

    public Response redeemInvite(String userOidcId, String token) {
        TeamInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Einladungslink nicht gefunden"));

        if (invite.getStatus() == TeamInviteStatus.REVOKED) {
            return Response.status(Response.Status.GONE)
                    .entity("{\"message\":\"Dieser Einladungslink wurde widerrufen. Bitte wende dich an einen Team-Admin.\"}")
                    .build();
        }

        if (invite.getType() == TeamInviteType.MULTI_USE) {
            if (invite.getStatus() != TeamInviteStatus.ACTIVE) {
                return Response.status(Response.Status.GONE)
                        .entity("{\"message\":\"Dieser Einladungslink ist nicht mehr gültig.\"}")
                        .build();
            }
            if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
                return Response.status(Response.Status.GONE)
                        .entity("{\"message\":\"Dieser Einladungslink ist abgelaufen. Bitte wende dich an einen Team-Admin.\"}")
                        .build();
            }
        }

        if (invite.getType() == TeamInviteType.SINGLE_USE) {
            if (invite.getStatus() != TeamInviteStatus.ACTIVE) {
                return Response.status(Response.Status.GONE)
                        .entity("{\"message\":\"Dieser Einladungslink wurde bereits eingelöst.\"}")
                        .build();
            }
        }

        Long teamId = invite.getTeam().id;

        if (teamRepository.isMember(teamId, userOidcId)) {
            return Response.ok("{\"message\":\"Du bist bereits Mitglied dieses Teams.\"}").build();
        }

        TeamMember newMember = new TeamMember();
        newMember.setTeam(invite.getTeam());
        newMember.setUserOidcId(userOidcId);
        newMember.setRole(TeamRole.MEMBER);
        newMember.setJoinedAt(Instant.now());
        teamRepository.persistMember(newMember);

        if (invite.getType() == TeamInviteType.MULTI_USE) {
            invite.setUseCount(invite.getUseCount() + 1);
        } else {
            invite.setStatus(TeamInviteStatus.USED);
        }

        teamMemberJoinedEvent.fire(new TeamMemberJoinedEvent(teamId, userOidcId));
        LOGGER.debugf("[Principal %s][Team %s] Joined via invite %s", userOidcId, teamId, invite.id);
        return Response.ok("{\"message\":\"Du bist dem Team erfolgreich beigetreten.\"}").build();
    }

    private void requireAdminOrOwner(Long teamId, String userOidcId) {
        if (!teamRepository.isAdminOrOwner(teamId, userOidcId)) {
            throw new ForbiddenException("Nur Admins und der Owner können Einladungslinks verwalten");
        }
    }
}
