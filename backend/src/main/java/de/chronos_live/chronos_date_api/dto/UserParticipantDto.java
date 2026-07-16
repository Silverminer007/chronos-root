package de.chronos_live.chronos_date_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserParticipantDto {

    // User-Daten
    private String user_id;
    private String name;
    private String profile_picture_url;

    // Participation-Daten
    private UserRole role;
    private ParticipationStatus status;

    // Gruppen-Kontext (optional)
    private Long via_group_id;        // Wurde über Gruppe hinzugefügt?
    private String via_group_name;    // Name der Gruppe
}