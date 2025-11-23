package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.EventAccessService;
import de.chronos_live.chronos_date_api.application.EventService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.EventMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.List;

@Path("/api/v2/events")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventsResource {
    @Inject
    EventService eventService;
    @Inject
    EventAccessService eventAccessService;
    @Inject
    UserService userService;
    @Inject
    JsonWebToken jwt;
    @Inject
    EventMapper mapper;


    @GET
    @Path("/")
    public Response getAgenda(@QueryParam("page") Integer page, @QueryParam("size") Integer size,
                            @QueryParam("start") String start, @QueryParam("end") String end,
                              @QueryParam("search") String search) {
        User user = this.userService.getUser(jwt.getSubject());

        LocalDate after;
        if (start != null) {
            after = LocalDate.parse(start);
        } else {
            after = LocalDate.MIN;
        }
        LocalDate before;
        if (end != null) {
            before = LocalDate.parse(end);
        } else {
            before = LocalDate.MAX;
        }

        List<Event> events;
        if (page == null && size == null) {
            events = eventService.searchEvent(search, after, before);
        } else {
            if (size == null) {
                size = 50;
            }
            if (page == null) {
                page = 1;
            }
            events = eventService.searchEvent(search, after, before, page, size);
        }

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(mapper.toDtoList(events)).build();
    }

    @GET
    @Path("/{year}")
    public Response getDates(@PathParam("year") int year) {
        User user = userService.getUser(jwt.getSubject());

        LocalDate after = LocalDate.of(year, Month.JANUARY, 1).minusDays(1);
        LocalDate before = LocalDate.of(year, Month.DECEMBER, 31).plusDays(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(mapper.toDtoList(events)).build();
    }

    @GET
    @Path("/{year}/{month}")
    public Response getDates(@PathParam("year") int year, @PathParam("month") int month) {
        User user = userService.getUser(jwt.getSubject());

        if (month < 1 || month > 12) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        LocalDate after = LocalDate.of(year, Month.of(month), 1).minusDays(1);
        LocalDate before = LocalDate.of(year, Month.of(month), Month.of(month).length(Year.isLeap(year))).plusDays(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(mapper.toDtoList(events)).build();
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

        LocalDate after = LocalDate.of(year, Month.of(month), dayOfMonth).minusDays(1);
        LocalDate before = LocalDate.of(year, Month.of(month), dayOfMonth).plusDays(1);

        List<Event> events = eventService.searchEvent(null, after, before);

        events = this.eventAccessService.filterEvents(events, user);

        return Response.ok(mapper.toDtoList(events)).build();
    }
}