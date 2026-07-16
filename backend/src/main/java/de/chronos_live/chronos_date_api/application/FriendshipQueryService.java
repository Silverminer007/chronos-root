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

    public boolean areFriends(String oidcId1, String oidcId2) {
        return friendshipRepo.areFriends(oidcId1, oidcId2);
    }

    public Set<String> getFriends(String oidcId) {
        return friendshipRepo.getFriendOidcIds(oidcId);
    }

    public Optional<FriendshipRequest> findFriendship(String oidcId1, String oidcId2) {
        return friendshipRepo.findFriendship(oidcId1, oidcId2);
    }

    public FriendshipStatus getFriendshipStatus(String requesterOidcId, String addresseeOidcId) {
        return friendshipRepo.findRequest(requesterOidcId, addresseeOidcId)
                .map(FriendshipRequest::getStatus)
                .orElse(null);
    }
}
