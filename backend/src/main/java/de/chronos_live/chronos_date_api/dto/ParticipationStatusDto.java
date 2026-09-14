package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for changing participation status (RSVP).
 *
 * Status can be: APPROVED, REJECTED
 */
@Data
@NoArgsConstructor
public class ParticipationStatusDto {
    private String status;
}
