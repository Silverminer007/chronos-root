package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.AppointmentGroupParticipation;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMember;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class GroupRepository implements PanacheRepository<Group> {

    // ── Group queries ─────────────────────────────────────────────────────────

    public List<Group> searchGroups(UserIdentity user, String searchQuery) {
        String sql = "SELECT g FROM GroupMember gm JOIN gm.group g LEFT JOIN FETCH g.members"
                + " WHERE gm.userOidcId = ?1 AND g.deletedAt IS NULL";
        if (searchQuery != null && !searchQuery.isEmpty()) {
            return Group.<Group>find(sql + " AND lower(g.groupName) LIKE lower(?2)",
                    user.oidcId(), "%" + searchQuery + "%").list();
        }
        return Group.<Group>find(sql, user.oidcId()).list();
    }

    public List<Group> findByIds(Set<Long> ids) {
        return Group.<Group>find("id in ?1 AND deletedAt IS NULL", ids).list();
    }

    @Override
    public Group findById(Long id) {
        return Group.findById(id);
    }

    @Override
    public boolean deleteById(Long id) {
        return Group.deleteById(id);
    }

    public boolean hasFutureAppointments(Long groupId) {
        return AppointmentGroupParticipation.count(
                "group.id = ?1 AND appointment.startTime > ?2 AND appointment.status != ?3",
                groupId, Instant.now(), AppointmentStatus.CANCELLED
        ) > 0;
    }

    // ── GroupMember queries ───────────────────────────────────────────────────

    public boolean isMember(Long groupId, String userOidcId) {
        return GroupMember.<GroupMember>find("group.id = ?1 AND userOidcId = ?2", groupId, userOidcId).count() > 0;
    }

    public List<GroupMember> listMembers(Long groupId) {
        return GroupMember.<GroupMember>find("group.id = ?1", groupId).list();
    }

    public Optional<GroupMember> findMember(Long groupId, String userOidcId) {
        return GroupMember.<GroupMember>find("group.id = ?1 AND userOidcId = ?2", groupId, userOidcId)
                .firstResultOptional();
    }

    public long countMembers() {
        return GroupMember.count();
    }

    public long countMembers(Long groupId) {
        return GroupMember.count("group.id = ?1", groupId);
    }

    public void persistMember(GroupMember member) {
        member.persist();
    }

    public void deleteMember(GroupMember member) {
        member.delete();
    }

    public void deleteAllMembers(Long groupId) {
        GroupMember.delete("group.id = ?1", groupId);
    }

    public List<GroupMember> listMembershipsInTeam(Long teamId, String userOidcId) {
        return GroupMember.<GroupMember>find(
                "userOidcId = ?1 AND group.team.id = ?2 AND group.deletedAt IS NULL",
                userOidcId, teamId
        ).list();
    }
}
