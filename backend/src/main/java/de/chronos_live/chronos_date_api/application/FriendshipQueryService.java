package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Timed("service.friendshipQuery")
public class FriendshipQueryService {

    @Inject
    FriendshipRepository friendshipRepo;

    public boolean areFriends(Long userId1, Long userId2) {
        return friendshipRepo.areFriends(userId1, userId2);
    }

    public Set<Long> getFriends(Long userId) {
        return friendshipRepo.getFriendIds(userId);
    }

    public Optional<FriendshipRequest> findFriendship(Long userId1, Long userId2) {
        return friendshipRepo.findFriendship(userId1, userId2);
    }

    public FriendshipStatus getFriendshipStatus(Long requesterId, Long addresseeId) {
        return friendshipRepo.findRequest(requesterId, addresseeId)
                .map(FriendshipRequest::getStatus)
                .orElse(null);
    }
}
