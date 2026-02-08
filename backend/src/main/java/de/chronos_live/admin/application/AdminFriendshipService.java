package de.chronos_live.admin.application;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.domain.User;
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

    public void befriend(List<Long> friends) {
        for (int i = 0; i < friends.size(); i++) {
            for (int j = i; j < friends.size(); j++) {
                befriend(friends.get(i), friends.get(j));
            }
        }
    }

    @Transactional
    public void befriend(Long requesterId, Long addresseeId) {
        // Validierung
        if (requesterId.equals(addresseeId)) {
            return;
        }

        // Prüfe ob Empfänger existiert
        if (User.findByIdOptional(addresseeId).isEmpty()) {
            return;
        }

        // Prüfe ob bereits eine Anfrage oder Freundschaft existiert
        Optional<FriendshipRequest> existing = friendshipRepo.findRequest(requesterId, addresseeId);
        if (existing.isPresent()) {
            FriendshipRequest existingRequest = existing.get();

            // Status = DECLINED: Erlaube neue Anfrage nach Ablehnung
            if (existingRequest.getStatus() == FriendshipStatus.DECLINED) {
                // Lösche alte abgelehnte Anfrage
                friendshipRepo.delete(existingRequest);
            } else {
                return;
            }
        }

        // Erstelle neue Anfrage
        FriendshipRequest request = new FriendshipRequest();
        request.setRequesterId(requesterId);
        request.setAddresseeId(addresseeId);
        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        request.setCreatedAt(Instant.now());
        friendshipRepo.persist(request);
    }
}
