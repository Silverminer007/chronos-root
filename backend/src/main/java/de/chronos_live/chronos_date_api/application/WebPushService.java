package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.config.PushConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

import java.security.GeneralSecurityException;

@ApplicationScoped
public class WebPushService {

    @Inject
    PushSubscriptionService subscriptionService;

    private PushService push;

    @Inject
    PushConfig config;

    @jakarta.annotation.PostConstruct
    void init() {
        try {
            push = new PushService(
                    config.publicKey,
                    config.privateKey,
                    config.mailto
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize WebPushService", e);
        }
    }

    public void sendToUser(Long userId, String title, String body) {
        var subs = subscriptionService.getAllForUser(userId);

        subs.forEach(sub -> {
            try {
                String payload = """
                {
                    "title": "%s",
                    "body": "%s"
                }
                """.formatted(title, body);

                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload
                );

                push.send(notification);

            } catch (Exception e) {
                // invalid endpoint -> löschen
                subscriptionService.deleteByEndpoint(sub.getEndpoint());
            }
        });
    }
}