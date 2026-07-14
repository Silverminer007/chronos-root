package de.chronos_live.admin.dto;

import java.time.Instant;

public record AdminUserDto(
        String id,
        String firstName,
        String lastName,
        String email,
        Instant createdAt
) {
}
