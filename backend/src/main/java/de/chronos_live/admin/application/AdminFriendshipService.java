package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Timed("service.admin.friendship")
public class AdminFriendshipService {
    @Inject
    FriendshipRepository friendshipRepo;

    public void befriend(List<String> friendOidcIds) {
        for (int i = 0; i < friendOidcIds.size(); i++) {
            for (int j = i; j < friendOidcIds.size(); j++) {
                befriend(friendOidcIds.get(i), friendOidcIds.get(j));
            }
        }
    }

    @Transactional
    public void befriend(String requesterOidcId, String addresseeOidcId) {
        if (requesterOidcId.equals(addresseeOidcId)) return;

        Optional<FriendshipRequest> existing = friendshipRepo.findRequest(requesterOidcId, addresseeOidcId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == FriendshipStatus.DECLINED) {
                friendshipRepo.delete(existing.get());
            } else {
                return;
            }
        }

        FriendshipRequest request = new FriendshipRequest();
        request.setRequesterId(requesterOidcId);
        request.setAddresseeId(addresseeOidcId);
        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        request.setCreatedAt(Instant.now());
        friendshipRepo.persist(request);
    }
}
