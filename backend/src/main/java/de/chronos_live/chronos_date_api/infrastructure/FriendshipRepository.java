package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FriendshipRepository implements PanacheRepository<FriendshipRequest> {

    public boolean existsRequest(String oidcId1, String oidcId2) {
        return count(
                "(requesterId = ?1 and addresseeId = ?2) or (requesterId = ?2 and addresseeId = ?1)",
                oidcId1, oidcId2
        ) > 0;
    }

    public boolean areFriends(String oidcId1, String oidcId2) {
        return count(
                "status = ?1 and ((requesterId = ?2 and addresseeId = ?3) or (requesterId = ?3 and addresseeId = ?2))",
                FriendshipStatus.ACCEPTED, oidcId1, oidcId2
        ) > 0;
    }

    public Optional<FriendshipRequest> findRequest(String oidcId1, String oidcId2) {
        return find(
                "(requesterId = ?1 and addresseeId = ?2) or (requesterId = ?2 and addresseeId = ?1)",
                oidcId1, oidcId2
        ).firstResultOptional();
    }

    public Optional<FriendshipRequest> findFriendship(String oidcId1, String oidcId2) {
        return find(
                "status = ?1 and ((requesterId = ?2 and addresseeId = ?3) or (requesterId = ?3 and addresseeId = ?2))",
                FriendshipStatus.ACCEPTED, oidcId1, oidcId2
        ).firstResultOptional();
    }

    @Timed(value = "repo.friendships.getFriendOidcIds")
    public Set<String> getFriendOidcIds(String oidcId) {
        List<FriendshipRequest> friendships = find(
                "status = ?1 and (requesterId = ?2 or addresseeId = ?2)",
                FriendshipStatus.ACCEPTED, oidcId
        ).list();

        return friendships.stream()
                .map(f -> f.getRequesterId().equals(oidcId) ? f.getAddresseeId() : f.getRequesterId())
                .collect(Collectors.toSet());
    }

    public List<FriendshipRequest> getIncomingRequests(String oidcId) {
        return find("addresseeId = ?1 and status = ?2", oidcId, FriendshipStatus.PENDING).list();
    }

    public List<FriendshipRequest> getOutgoingRequests(String oidcId) {
        return find("requesterId = ?1 and status = ?2", oidcId, FriendshipStatus.PENDING).list();
    }

    public List<FriendshipRequest> getFriendships(String oidcId) {
        return find(
                "status = ?1 and (requesterId = ?2 or addresseeId = ?2)",
                FriendshipStatus.ACCEPTED, oidcId
        ).list();
    }
}
