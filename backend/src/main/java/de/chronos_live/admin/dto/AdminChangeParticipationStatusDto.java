package de.chronos_live.admin.dto;

import lombok.Data;

@Data
public class AdminChangeParticipationStatusDto {
    private String userId;
    private Long appointmentId;
    private String participationStatus;
}
