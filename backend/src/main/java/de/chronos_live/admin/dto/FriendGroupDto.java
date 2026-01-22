package de.chronos_live.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class FriendGroupDto {
    private List<Long> userIds;
}
