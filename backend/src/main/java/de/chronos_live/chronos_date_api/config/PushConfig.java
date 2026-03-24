package de.chronos_live.chronos_date_api.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Getter
@ApplicationScoped
public class PushConfig {

    private final Optional<String> mailto;
    private final Optional<String> publicKey;
    private final Optional<String> privateKey;

    public PushConfig(
            @ConfigProperty(name = "push.vapid.mailto") Optional<String> mailto,
            @ConfigProperty(name = "push.vapid.public") Optional<String> publicKey,
            @ConfigProperty(name = "push.vapid.private") Optional<String> privateKey) {
        this.mailto = mailto;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

}
