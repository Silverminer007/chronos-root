package de.chronos_live.chronos_date_api.presentation;

import java.util.List;

public record EventAttendeesDto(List<EventGroupAttendeesDto> groupAttendees, List<EventUserAttendeesDto> userAttendees) {
}
