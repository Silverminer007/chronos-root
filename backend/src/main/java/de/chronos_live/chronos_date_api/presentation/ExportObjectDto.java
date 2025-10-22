package de.chronos_live.chronos_date_api.presentation;

import java.util.List;

public record ExportObjectDto(List<DateDto> dates, List<UserDto> users, List<GroupDto> groups, List<GroupUserDto> groupUsers,
                              List<OrganisationDto> organisations, List<OrganisationUserDto> organisationUsers,
                              List<MessageDto> messages, List<AttendanceDto> attendances) {
}