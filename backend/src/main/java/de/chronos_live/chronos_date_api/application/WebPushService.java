package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.config.PushConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jboss.logging.Logger;

import java.security.GeneralSecurityException;
import java.security.Security;

@ApplicationScoped
@Transactional
public class WebPushService {
    private static final Logger LOGGER = Logger.getLogger(WebPushService.class);

    @Inject
    PushSubscriptionService subscriptionService;

    private PushService push;

    @Inject
    PushConfig config;

    @jakarta.annotation.PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            push = new PushService(
                    config.getPublicKey(),
                    config.getPrivateKey(),
                    config.getMailto()
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize WebPushService", e);
        }
    }

    public String getPublicKey() {
        return config.getPublicKey();
    }

    public void sendToUser(Long userId, String title, String body) {
        var subs = subscriptionService.getAllForUser(userId);
        LOGGER.infof("Sending a message to user with id %d:\n%s\n%s", userId, title, body);

        subs.forEach(sub -> {
            try {
                JsonObject payload = Json.createObjectBuilder()
                        .add("title", title)
                        .add("body", body)
                        .build();

                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload.toString()
                );

                push.send(notification);

            } catch (Exception e) {
                // invalid endpoint -> löschen
                subscriptionService.deleteByEndpoint(sub.getEndpoint());
            }
        });
    }
}