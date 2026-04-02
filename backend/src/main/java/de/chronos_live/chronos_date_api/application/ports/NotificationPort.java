package de.chronos_live.chronos_date_api.application.ports;

public interface NotificationPort {
    void send(Long userId, String payload);
    String getVapidPublicKey();
}
