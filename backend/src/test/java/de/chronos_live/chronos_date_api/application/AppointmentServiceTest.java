package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.dto.CreateAppointmentDto;
import de.chronos_live.chronos_date_api.dto.UpdateAppointmentDto;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all CDI
 * dependencies with Mockito mocks. {@link PanacheMock} intercepts Panache-
 * enhanced calls ({@code findById}, {@code persist}) so no real database is
 * ever touched.
 *
 * <p>All branches are now testable (the previously dead {@code isBefore} branch was fixed
 * in the source to compare {@code newEndTime.isBefore(newStartTime)}).
 */
@QuarkusTest
class AppointmentServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   APPOINTMENT_ID = 42L;
    private static final String USER_OIDC      = "oidc-user-1";

    private static final String  START_STR     = "2024-06-01T09:00:00Z";
    private static final String  END_STR       = "2024-06-01T17:00:00Z";
    private static final Instant START         = Instant.parse(START_STR);
    private static final Instant END           = Instant.parse(END_STR);

    private static final String  NEW_START_STR = "2024-06-01T11:00:00Z";
    private static final String  NEW_END_STR   = "2024-06-02T17:00:00Z";
    private static final Instant NEW_START     = Instant.parse(NEW_START_STR);
    private static final Instant NEW_END       = Instant.parse(NEW_END_STR);

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentService service;

    @InjectMock
    AuthorizationService authorizationService;

    @InjectMock
    AppointmentRepository appointmentRepository;

    @InjectMock
    Event<AppointmentCreatedEvent> appointmentCreatedEvent;

    @InjectMock
    Event<AppointmentMovedEvent> appointmentMovedEvent;

    @InjectMock
    Event<AppointmentCancelledEvent> appointmentCancelledEvent;

    @InjectMock
    Event<AppointmentDeletedEvent> appointmentDeletedEvent;

    @InjectMock
    Event<AppointmentEditedEvent> appointmentEditedEvent;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.setName("Test Termin");
        a.setStartTime(START);
        a.setEndTime(END);
        a.setStatus(AppointmentStatus.PLANNED);
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – createAppointment:
     *   B1  name.isBlank()                       → true (throw) / false
     *   B2  description != null {@code &&} !isBlank() → true (set)  / false (null → skip)
     *   B3  venue != null {@code &&} !isBlank()       → true (set)  / false (null → skip)
     *   B4  end == null                          → true (throw) / false
     *   B5  start == null                        → true (throw) / false
     *   B6  endTime.isBefore(startTime)          → true (throw) / false
     *   B7  minAttendees != null {@code &&} < 0       → true (throw) / false (null or ≥0)
     *
     * Total branches: 14  |  Tests: 7
     */
    @Nested
    class CreateAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_throwValidationException_when_nameIsBlank() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "   ", null, START_STR, END_STR, null, null);

            assertThatThrownBy(() -> service.createAppointment(dto, USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B4=true
        @Test
        void should_throwValidationException_when_endIsNull() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", null, START_STR, null, null, null);

            assertThatThrownBy(() -> service.createAppointment(dto, USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("end");
        }

        // B5=true
        @Test
        void should_throwValidationException_when_startIsNull() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", null, null, END_STR, null, null);

            assertThatThrownBy(() -> service.createAppointment(dto, USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("start");
        }

        // B6=true — swap start/end so endTime < startTime
        @Test
        void should_throwValidationException_when_endIsBeforeStart() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", null, END_STR, START_STR, null, null);

            assertThatThrownBy(() -> service.createAppointment(dto, USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("end");
        }

        // B7=true
        @Test
        void should_throwValidationException_when_minimalAttendeesIsNegative() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", null, START_STR, END_STR, null, -1);

            assertThatThrownBy(() -> service.createAppointment(dto, USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("minimal_attendees");
        }

        // B1=false, B2=true, B3=true, B4=false, B5=false, B6=false, B7=false (value ≥0)
        // Also verifies AppointmentCreatedEvent is fired with the correct creatorId.
        @Test
        void should_createAppointmentAndFireEvent_when_allFieldsValid() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", "A description", START_STR, END_STR, "Main Hall", 5);
            ArgumentCaptor<AppointmentCreatedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentCreatedEvent.class);

            Appointment result = service.createAppointment(dto, USER_OIDC);

            assertThat(result.getName()).isEqualTo("Name");
            assertThat(result.getDescription()).isEqualTo("A description");
            assertThat(result.getVenue()).isEqualTo("Main Hall");
            assertThat(result.getStartTime()).isEqualTo(START);
            assertThat(result.getEndTime()).isEqualTo(END);
            assertThat(result.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
            assertThat(result.getMinimalAttendees()).isEqualTo(5);

            verify(appointmentCreatedEvent).fire(captor.capture());
            assertThat(captor.getValue().creatorOidcId()).isEqualTo(USER_OIDC);
        }

        // B2=false (null description), B3=false (null venue), B7=false (null minAttendees)
        @Test
        void should_createAppointment_when_optionalFieldsAreNull() {
            CreateAppointmentDto dto = new CreateAppointmentDto(
                    "Name", null, START_STR, END_STR, null, null);

            Appointment result = service.createAppointment(dto, USER_OIDC);

            assertThat(result.getDescription()).isNull();
            assertThat(result.getVenue()).isNull();
            assertThat(result.getMinimalAttendees()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – updateAppointment:
     *   B1  name != null {@code &&} isBlank()         → true (throw) / false (set) / null (skip)
     *   B2  description != null                  → set / null (skip)
     *   B3  venue != null                        → set / null (skip)
     *   B4  start != null {@code &&} end != null      → set both
     *   B5  else if start != null                → set start only
     *   B6  else if end != null                  → set end only
     *   B7  both null                            → keep originals
     *   B8  newEndTime.isBefore(newStartTime)    → true (throw) / false (continue)
     *   B9  times changed                        → true (fire moved) / false (no moved)
     *   B10 minAttendees != null                 → set / null (skip)
     *
     * Total testable branches: 22  |  Tests: 7
     */
    @Nested
    class UpdateAppointment {

        @BeforeEach
        void stubQueryService() {
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, true, true, true))
                    .thenReturn(buildAppointment());
        }

        // B1=true (non-null blank name)
        @Test
        void should_throwValidationException_when_nameIsBlankOnUpdate() {
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    "   ", null, null, null, null, null);

            assertThatThrownBy(() -> service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        // B8=true — end before start after parsing
        @Test
        void should_throwValidationException_when_endIsBeforeStartOnUpdate() {
            // Provide end earlier than start so newEndTime.isBefore(newStartTime) = true
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    null, null, NEW_END_STR, NEW_START_STR, null, null);

            assertThatThrownBy(() -> service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("end");
        }

        // B1=false (name set), B2=true, B3=true, B4=true (both times), B9=true, B10=true
        // Verifies moved event payload and that edited event is always fired.
        @Test
        void should_updateAllFieldsAndFireBothEvents_when_allFieldsProvided() {
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    "Updated", "New desc", NEW_START_STR, NEW_END_STR, "New Hall", 10);
            ArgumentCaptor<AppointmentMovedEvent> movedCaptor =
                    ArgumentCaptor.forClass(AppointmentMovedEvent.class);

            Appointment result = service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto);

            assertThat(result.getName()).isEqualTo("Updated");
            assertThat(result.getDescription()).isEqualTo("New desc");
            assertThat(result.getVenue()).isEqualTo("New Hall");
            assertThat(result.getStartTime()).isEqualTo(NEW_START);
            assertThat(result.getEndTime()).isEqualTo(NEW_END);
            assertThat(result.getMinimalAttendees()).isEqualTo(10);

            verify(appointmentMovedEvent).fire(movedCaptor.capture());
            assertThat(movedCaptor.getValue().oldStartTime()).isEqualTo(START);
            assertThat(movedCaptor.getValue().oldEndTime()).isEqualTo(END);
            assertThat(movedCaptor.getValue().actingUserOidcId()).isEqualTo(USER_OIDC);

            verify(appointmentEditedEvent).fire(any(AppointmentEditedEvent.class));
        }

        // B1=null (skip), B2=false, B3=false, B7 (both null → keep originals), B9=false, B10=false
        @Test
        void should_notFireMovedEvent_when_noFieldsUpdated() {
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    null, null, null, null, null, null);

            service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto);

            verify(appointmentMovedEvent, never()).fire(any());
            verify(appointmentEditedEvent).fire(any(AppointmentEditedEvent.class));
        }

        // B5: only start provided
        @Test
        void should_updateOnlyStart_when_onlyStartProvided() {
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    null, null, NEW_START_STR, null, null, null);

            Appointment result = service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto);

            assertThat(result.getStartTime()).isEqualTo(NEW_START);
            assertThat(result.getEndTime()).isEqualTo(END);
            verify(appointmentMovedEvent).fire(any(AppointmentMovedEvent.class));
        }

        // B6: only end provided
        @Test
        void should_updateOnlyEnd_when_onlyEndProvided() {
            UpdateAppointmentDto dto = new UpdateAppointmentDto(
                    null, null, null, NEW_END_STR, null, null);

            Appointment result = service.updateAppointment(APPOINTMENT_ID, USER_OIDC, dto);

            assertThat(result.getEndTime()).isEqualTo(NEW_END);
            assertThat(result.getStartTime()).isEqualTo(START);
            verify(appointmentMovedEvent).fire(any(AppointmentMovedEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – deleteAppointment:
     *   B1  appointment == null → true (early return) / false (set status + fire event)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class DeleteAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_doNothing_when_appointmentNotFound() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.deleteAppointment(APPOINTMENT_ID, USER_OIDC);

            verify(appointmentDeletedEvent, never()).fire(any());
        }

        // B1=false
        @Test
        void should_setStatusDeletedAndFireEvent_when_appointmentFound() {
            Appointment appointment = buildAppointment();
            appointment.id = APPOINTMENT_ID;
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            ArgumentCaptor<AppointmentDeletedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentDeletedEvent.class);

            service.deleteAppointment(APPOINTMENT_ID, USER_OIDC);

            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.DELETED);
            verify(appointmentDeletedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(USER_OIDC);
            assertThat(captor.getValue().deletedAppointmentId()).isEqualTo(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // cancelAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – cancelAppointment:
     *   B1  appointment == null → true (early return) / false (set status + fire event)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class CancelAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
        }

        // B1=true
        @Test
        void should_doNothing_when_appointmentNotFoundOnCancel() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(null);

            service.cancelAppointment(APPOINTMENT_ID, USER_OIDC);

            verify(appointmentCancelledEvent, never()).fire(any());
        }

        // B1=false
        @Test
        void should_setStatusCancelledAndFireEvent_when_appointmentFound() {
            Appointment appointment = buildAppointment();
            appointment.id = APPOINTMENT_ID;
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);
            ArgumentCaptor<AppointmentCancelledEvent> captor =
                    ArgumentCaptor.forClass(AppointmentCancelledEvent.class);

            service.cancelAppointment(APPOINTMENT_ID, USER_OIDC);

            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            verify(appointmentCancelledEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(USER_OIDC);
            assertThat(captor.getValue().cancelledAppointmentId()).isEqualTo(APPOINTMENT_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getAppointment:
     *   B1  !messages          → true (set null) / false (keep)
     *   B2  !participants      → true (set null) / false (keep)
     *   B3  !groupParticipants → true (set null) / false (keep)
     *
     * Total branches: 6  |  Tests: 2
     */
    @Nested
    class GetAppointment {

        // B1=true, B2=true, B3=true
        @Test
        void should_nullifyUnfetchedCollections_when_allFlagsFalse() {
            Appointment appointment = buildAppointment();
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, false, false, false))
                    .thenReturn(appointment);

            Appointment result = service.getAppointment(APPOINTMENT_ID, USER_OIDC, false, false, false);

            assertThat(result.getMessages()).isNull();
            assertThat(result.getParticipants()).isNull();
            assertThat(result.getGroupParticipants()).isNull();
        }

        // B1=false, B2=false, B3=false
        @Test
        void should_notNullifyCollections_when_allFlagsTrue() {
            Appointment appointment = buildAppointment();
            appointment.setMessages(Set.of());
            appointment.setParticipants(Set.of());
            appointment.setGroupParticipants(Set.of());
            when(appointmentRepository.getAppointment(APPOINTMENT_ID, true, true, true))
                    .thenReturn(appointment);

            Appointment result = service.getAppointment(APPOINTMENT_ID, USER_OIDC, true, true, true);

            assertThat(result.getMessages()).isNotNull();
            assertThat(result.getParticipants()).isNotNull();
            assertThat(result.getGroupParticipants()).isNotNull();
        }
    }
}
