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
    private Long userId;
    private String name;
    private String profilePictureUrl;

    // Participation-Daten
    private UserRole role;
    private ParticipationStatus status;

    // Gruppen-Kontext (optional)
    private Long viaGroupId;        // Wurde über Gruppe hinzugefügt?
    private String viaGroupName;    // Name der Gruppe
}