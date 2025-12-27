package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddGroupParticipantDto {
    private Long group_id;
    private String user_role;
}
