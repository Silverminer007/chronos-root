package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "friendship_requests",
        indexes = {
                @Index(name = "idx_friendship_requester", columnList = "requester_oidcid"),
                @Index(name = "idx_friendship_addressee", columnList = "addressee_oidcid"),
                @Index(name = "idx_friendship_status", columnList = "status")
        })
public class FriendshipRequest extends PanacheEntity {
    @Column(name = "requester_oidcid", nullable = false)
    private String requesterId;

    @Column(name = "addressee_oidcid", nullable = false)
    private String addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
