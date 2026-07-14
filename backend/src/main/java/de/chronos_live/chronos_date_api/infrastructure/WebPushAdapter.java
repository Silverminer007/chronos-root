package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.application.PushSubscriptionService;
import de.chronos_live.chronos_date_api.application.ports.NotificationLogPort;
import de.chronos_live.chronos_date_api.application.ports.NotificationPort;
import de.chronos_live.chronos_date_api.config.PushConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.Security;

@ApplicationScoped
public class WebPushAdapter implements NotificationPort {

    private static final Logger LOGGER = Logger.getLogger(WebPushAdapter.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    @Inject
    PushSubscriptionService subscriptionService;

    @Inject
    NotificationLogPort notificationLog;

    @Inject
    PushConfig config;

    private PushService push;

    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            if (config.getPublicKey().isEmpty() || config.getPrivateKey().isEmpty() || config.getMailto().isEmpty()) {
                push = null;
                Log.warn("VAPID Keys are not set. No Push Notifications will be sent. " +
                        "Please set VAPID_PUBLIC, VAPID_PRIVATE and VAPID_MAILTO environment variables");
                return;
            }
            push = new PushService(
                    config.getPublicKey().get(),
                    config.getPrivateKey().get(),
                    config.getMailto().get()
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize WebPushAdapter", e);
        }
    }

    @Override
    public String getVapidPublicKey() {
        return config.getPublicKey().orElse("");
    }

    @Override
    public void send(String userOidcId, String payload) {
        if (push == null) {
            LOGGER.warn("Omitting push notification because VAPID keys are not set");
            return;
        }
        Log.debugf("[Notifications] Sending notification to user [%s]: %s", userOidcId, payload);
        String notificationType = extractType(payload);

        subscriptionService.getAllForUser(userOidcId).forEach(sub -> {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload
                );
                var response = push.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();
                boolean success = statusCode >= 200 && statusCode < 300;

                notificationLog.log(userOidcId, notificationType, payload, sub.getEndpoint(), statusCode, success, null);

                if (statusCode == 410 || statusCode == 404) {
                    Log.infof("[Notifications] Subscription expired (HTTP %d), removing endpoint %s",
                            statusCode, sub.getEndpoint());
                    subscriptionService.deleteByEndpoint(sub.getEndpoint());
                } else if (!success) {
                    Log.warnf("[Notifications] Push service returned HTTP %d for endpoint %s",
                            statusCode, sub.getEndpoint());
                }
            } catch (Exception e) {
                Log.errorf(e, "[Notifications] Failed to send notification to endpoint %s", sub.getEndpoint());
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
                    errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
                }
                notificationLog.log(userOidcId, notificationType, payload, sub.getEndpoint(), null, false, errorMessage);
                subscriptionService.deleteByEndpoint(sub.getEndpoint());
            }
        });
    }

    private static String extractType(String payload) {
        try {
            var json = Json.createReader(new StringReader(payload)).readObject();
            return json.containsKey("type") ? json.getString("type") : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
