package de.chronos_live.admin.dto;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserDto> items,
        int page,
        int size,
        long total
) {
}
