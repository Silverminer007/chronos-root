package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.application.ports.NotificationLogPort;
import de.chronos_live.chronos_date_api.domain.PushNotificationLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PushNotificationLogRepository implements PanacheRepository<PushNotificationLog>, NotificationLogPort {

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(Long userId, String notificationType, String payload,
                    String endpoint, Integer httpStatusCode, boolean success,
                    String errorMessage) {
        PushNotificationLog entry = new PushNotificationLog();
        entry.setUserId(userId);
        entry.setNotificationType(notificationType);
        entry.setPayload(payload);
        entry.setEndpoint(endpoint);
        entry.setHttpStatusCode(httpStatusCode);
        entry.setSuccess(success);
        entry.setErrorMessage(errorMessage);
        entry.setCreatedAt(OffsetDateTime.now());
        persist(entry);
    }

    public List<PushNotificationLog> findFiltered(Long userId, Instant from, Instant to,
                                                   Boolean success, String notificationType,
                                                   int page, int size) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (userId != null) {
            conditions.add("userId = :userId");
            params.put("userId", userId);
        }
        if (from != null) {
            conditions.add("createdAt >= :from");
            params.put("from", from.atOffset(ZoneOffset.UTC));
        }
        if (to != null) {
            conditions.add("createdAt <= :to");
            params.put("to", to.atOffset(ZoneOffset.UTC));
        }
        if (success != null) {
            conditions.add("success = :success");
            params.put("success", success);
        }
        if (notificationType != null) {
            conditions.add("notificationType = :notificationType");
            params.put("notificationType", notificationType);
        }

        String query = conditions.isEmpty() ? "" : String.join(" AND ", conditions);

        return find(query, Sort.descending("createdAt"), params)
                .page(page, size)
                .list();
    }
}
