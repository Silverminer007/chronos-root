package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.PushSubscription;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.infrastructure.PushSubscriptionRepository;
import de.chronos_live.chronos_date_api.presentation.PushSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
@Transactional
public class PushSubscriptionService {

    @Inject
    PushSubscriptionRepository repo;

    @Transactional
    public void saveSubscription(Long userId, PushSubscriptionDto dto) {
        // Replace existing subscription with same endpoint
        PushSubscription existing = repo.findByEndpoint(dto.endpoint());
        if (existing != null) {
            repo.delete(existing);
        }

        PushSubscription sub = new PushSubscription();
        sub.setEndpoint(dto.endpoint());
        sub.setAuth(dto.keys().auth());
        sub.setP256dh(dto.keys().p256dh());
        sub.setUser(User.findById(userId));

        repo.persist(sub);
    }

    @Transactional
    public void deleteByEndpoint(String endpoint) {
        PushSubscription existing = repo.findByEndpoint(endpoint);
        if (existing != null) {
            repo.delete(existing);
        }
    }

    public List<PushSubscription> getAllForUser(Long userId) {
        return repo.findByUserId(userId);
    }
}