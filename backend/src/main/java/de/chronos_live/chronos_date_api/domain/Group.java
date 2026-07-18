package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "groups")
public class Group extends PanacheEntity {
    private String groupName;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "group")
    @EqualsAndHashCode.Exclude
    private Set<GroupMember> members;
}
