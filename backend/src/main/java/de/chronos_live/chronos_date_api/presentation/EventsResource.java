package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AttendanceStatusService;
import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.EventService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Attendance;
import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.AttendanceMapper;
import de.chronos_live.chronos_date_api.mapper.EventMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Path("/api/v2/events")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventsResource {
    private static final Logger LOGGER = Logger.getLogger(EventsResource.class);
    @Inject
    EventService eventService;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    AttendanceStatusService attendanceStatusService;
    @Inject
    UserService userService;
    @Inject
    JsonWebToken jwt;
    @Inject
    EventMapper eventMapper;
    @Inject
    AttendanceMapper attendanceMapper;


    @GET
    @Path("/")
    public Response getAgenda(@QueryParam("page") Integer page, @QueryParam("size") Integer size,
                              @QueryParam("start") String start, @QueryParam("end") String end,
                              @QueryParam("search") String search, @QueryParam("attendances") Boolean includeAttendances) {
        User user = this.userService.getUser(jwt.getSubject());

        Instant after;
        if (start != null) {
            after = Instant.parse(start);
        } else {
            after = Instant.now();
        }
        Instant before;
        if (end != null) {
            before = Instant.parse(end);
        } else {
            before = Instant.now().plusSeconds(60L * 60 * 24 * 365 * 1000);
        }

        if (size == null) {
            size = 10;
        }
        if (page == null) {
            page = 0;
        }
        List<Event> events = eventService.searchEvent(search, after, before, page, size);

        events = this.eventAccessService.filterEvents(events, user);

        List<EventDto> eventDtos;
        if (includeAttendances != null && includeAttendances) {
            eventDtos = new ArrayList<>();
            for (Event event : events) {
                List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
                Attendance ownAttendance = this.attendanceStatusService.getAttendanceStatus(user, event.id);

                EventDto eventDto = eventMapper.toDto(event);
                eventDto.setOwn_attendance_status(ownAttendance.getStatus().name());
                eventDto.setAttendances(attendanceMapper.toDtoList(attendances));
                eventDtos.add(eventDto);
            }
        } else {
            eventDtos = eventMapper.toDtoList(events);
        }

        return Response.ok(eventDtos).build();
    }

    @GET
    @Path("/{year}")
    public Response getDates(@PathParam("year") int year) {
        User user = userService.getUser(jwt.getSubject());

        LocalDateTime after = LocalDate.of(year, Month.JANUARY, 1).minusDays(1).atStartOfDay();
        LocalDateTime before = after.plusYears(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(eventMapper.toDtoList(events)).build();
    }

    @GET
    @Path("/{year}/{month}")
    public Response getDates(@PathParam("year") int year, @PathParam("month") int month) {
        User user = userService.getUser(jwt.getSubject());

        if (month < 1 || month > 12) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        LocalDateTime after = LocalDate.of(year, Month.of(month), 1).minusDays(1).atStartOfDay();
        LocalDateTime before = after.plusMonths(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(eventMapper.toDtoList(events)).build();
    }

    @GET
    @Path("/{year}/{month}/{dayOfMonth}")
    public Response getDates(@PathParam("year") int year, @PathParam("month") int month, @PathParam("dayOfMonth") int dayOfMonth) {
        User user = userService.getUser(jwt.getSubject());

        if (month < 1 || month > 12) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (dayOfMonth < 1 || dayOfMonth > Month.of(month).length(Year.isLeap(year))) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        LocalDateTime after = LocalDate.of(year, Month.of(month), dayOfMonth).atStartOfDay();
        LocalDateTime before = after.plusDays(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(eventMapper.toDtoList(events)).build();
    }
}