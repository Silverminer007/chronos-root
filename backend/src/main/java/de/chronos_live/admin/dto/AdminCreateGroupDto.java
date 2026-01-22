package de.chronos_live.admin.dto;

import lombok.Data;

@Data
public class AdminCreateGroupDto {
    private Long ownerId;
    private String groupName;
}
