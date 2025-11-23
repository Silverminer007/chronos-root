package de.chronos_live.chronos_date_api.config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PushConfig {

    @ConfigProperty(name = "push.vapid.mailto")
    public String mailto;

    @ConfigProperty(name = "push.vapid.public")
    public String publicKey;

    @ConfigProperty(name = "push.vapid.private")
    public String privateKey;
}