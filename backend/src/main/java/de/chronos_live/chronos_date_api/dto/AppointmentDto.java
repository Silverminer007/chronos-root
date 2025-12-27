package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AppointmentDto {

    // Pflichtfelder
    private Long id;

    private String name;

    private String description;

    private String start;

    private String end;

    private String venue;

    private String status;

    private Integer minimal_attendees;

    private List<MessageDto> messages;
    private List<UserParticipantDto> participants;
    private List<GroupDto> group_participants;
}