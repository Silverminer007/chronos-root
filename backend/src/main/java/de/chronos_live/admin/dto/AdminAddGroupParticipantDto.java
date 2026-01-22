package de.chronos_live.admin.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AdminAddGroupParticipantDto {
    private Long group_id;
    private String user_role;
}
