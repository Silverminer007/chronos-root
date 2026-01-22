package de.chronos_live.admin.dto;

import lombok.Data;

@Data
public class AdminChangeParticipationStatusDto {
    private Long userId, appointmentId;
    private String participationStatus;
}
