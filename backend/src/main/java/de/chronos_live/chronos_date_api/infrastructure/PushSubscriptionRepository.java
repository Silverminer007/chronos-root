package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.PushSubscription;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PushSubscriptionRepository implements PanacheRepository<PushSubscription> {

    public List<PushSubscription> findByUserId(Long userId) {
        return list("user.id", userId);
    }

    public PushSubscription findByEndpoint(String endpoint) {
        return find("endpoint", endpoint).firstResult();
    }
}