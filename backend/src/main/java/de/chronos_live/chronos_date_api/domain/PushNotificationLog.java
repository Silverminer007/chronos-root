package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "push_notification_log")
public class PushNotificationLog extends PanacheEntity {

    @Column(name = "user_oidcid", nullable = false)
    private String userOidcId;

    @Column(name = "notification_type", length = 100)
    private String notificationType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
