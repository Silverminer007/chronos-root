package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Entity
@Table(name = "team_invite",
        indexes = {
                @Index(name = "idx_team_invite_team", columnList = "team_id"),
                @Index(name = "idx_team_invite_token", columnList = "token"),
                @Index(name = "idx_team_invite_status", columnList = "status")
        })
public class TeamInvite extends PanacheEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamInviteType type;

    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamInviteStatus status;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "created_by_oidcid", nullable = false)
    private String createdByOidcId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
