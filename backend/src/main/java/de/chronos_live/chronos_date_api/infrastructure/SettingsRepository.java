package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.Settings;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class SettingsRepository implements PanacheRepository<Settings> {

    public Optional<Settings> findByUserOidcId(String userOidcId) {
        return Settings.<Settings>find("userOidcId", userOidcId).firstResultOptional();
    }
}
