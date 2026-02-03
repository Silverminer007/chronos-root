package de.chronos_live.admin.dto;

import java.time.Instant;

public record AdminUserDto(
        Long id,
        String firstName,
        String lastName,
        String email,
        Instant createdAt,
        Instant lastUpdate,
        Instant lastSeen
) {
}
