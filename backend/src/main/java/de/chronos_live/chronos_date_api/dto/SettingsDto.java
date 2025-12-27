package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SettingsDto {
    private String appointment_moved;
    private String appointment_message;
    private String appointment_cancelled;
    private String appointment_participant_added;
    private String appointment_participation_status_changed;
    private String appointment_participation_invalid;
    private String appointment_participation_status_pending;
    private String appointment_reminder;
    private String group_member_added;
}
