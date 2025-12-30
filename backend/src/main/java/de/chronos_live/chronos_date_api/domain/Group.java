package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "groups")
public class Group extends PanacheEntity {
    @ManyToOne
    private User owner;
    private String groupName;
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "group")
    @EqualsAndHashCode.Exclude
    private Set<GroupMember> members;
}
