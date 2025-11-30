package de.chronos_live.chronos_date_api.config;

import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

@Getter
@ApplicationScoped
public class PushConfig {

    private final String mailto;
    private final String publicKey;
    private final String privateKey;

    public PushConfig(
            @ConfigProperty(name = "push.vapid.mailto") String mailto,
            @ConfigProperty(name = "push.vapid.public") String publicKey,
            @ConfigProperty(name = "push.vapid.private") String privateKey) {
        this.mailto = mailto;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

}