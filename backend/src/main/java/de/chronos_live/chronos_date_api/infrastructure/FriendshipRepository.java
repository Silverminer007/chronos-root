package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FriendshipRepository implements PanacheRepository<FriendshipRequest> {

    /**
     * Prüft ob eine Freundschaftsanfrage existiert (egal welcher Status)
     */
    public boolean existsRequest(Long userId1, Long userId2) {
        return count(
                "(requesterId = ?1 and addresseeId = ?2) or (requesterId = ?2 and addresseeId = ?1)",
                userId1, userId2
        ) > 0;
    }

    /**
     * Prüft, ob zwei User befreundet sind (ACCEPTED)
     */
    public boolean areFriends(Long userId1, Long userId2) {
        return count(
                "status = ?1 and ((requesterId = ?2 and addresseeId = ?3) or (requesterId = ?3 and addresseeId = ?2))",
                FriendshipStatus.ACCEPTED, userId1, userId2
        ) > 0;
    }

    /**
     * Findet Freundschaftsanfrage zwischen zwei Usern
     */
    public Optional<FriendshipRequest> findRequest(Long userId1, Long userId2) {
        return find(
                "(requesterId = ?1 and addresseeId = ?2) or (requesterId = ?2 and addresseeId = ?1)",
                userId1, userId2
        ).firstResultOptional();
    }

    /**
     * Findet aktive Freundschaft zwischen zwei Usern
     */
    public Optional<FriendshipRequest> findFriendship(Long userId1, Long userId2) {
        return find(
                "status = ?1 and ((requesterId = ?2 and addresseeId = ?3) or (requesterId = ?3 and addresseeId = ?2))",
                FriendshipStatus.ACCEPTED, userId1, userId2
        ).firstResultOptional();
    }

    /**
     * Lädt alle Freunde eines Users
     */
    public Set<Long> getFriendIds(Long userId) {
        List<FriendshipRequest> friendships = find(
                "status = ?1 and (requesterId = ?2 or addresseeId = ?2)",
                FriendshipStatus.ACCEPTED, userId
        ).list();

        return friendships.stream()
                .map(f -> f.getRequesterId().equals(userId) ?
                        f.getAddresseeId() : f.getRequesterId())
                .collect(Collectors.toSet());
    }

    /**
     * Lädt eingehende Anfragen (PENDING)
     */
    public List<FriendshipRequest> getIncomingRequests(Long userId) {
        return find("addresseeId = ?1 and status = ?2", userId, FriendshipStatus.PENDING).list();
    }

    /**
     * Lädt ausgehende Anfragen (PENDING)
     */
    public List<FriendshipRequest> getOutgoingRequests(Long userId) {
        return find("requesterId = ?1 and status = ?2", userId, FriendshipStatus.PENDING).list();
    }

    /**
     * Lädt alle Freundschaften eines Users
     */
    public List<FriendshipRequest> getFriendships(Long userId) {
        return find(
                "status = ?1 and (requesterId = ?2 or addresseeId = ?2)",
                FriendshipStatus.ACCEPTED, userId
        ).list();
    }
}