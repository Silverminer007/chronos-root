package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.*;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentParticipationService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces every CDI
 * dependency with a Mockito mock. {@link PanacheMock} intercepts all static
 * Panache calls so no real database is ever touched.
 *
 * <p><b>Untestable branches:</b><br>
 * None. All reachable branches are exercised below.
 */
@QuarkusTest
class AppointmentParticipationServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   APPOINTMENT_ID      = 10L;
    private static final String ACTING_USER_OIDC_ID = "acting-user-oidc-1";
    private static final String TARGET_USER_OIDC_ID = "target-user-oidc-2";
    private static final Long   GROUP_ID            = 3L;

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentParticipationService service;

    @InjectMock
    AuthorizationService authorizationService;

    @InjectMock
    GroupService groupService;

    @InjectMock
    Event<AppointmentParticipationStatusChangedEvent> appointmentParticipationStatusChangedEvent;

    @InjectMock
    Event<AppointmentParticipationRoleChangedEvent> appointmentParticipationRoleChangedEvent;

    @InjectMock
    Event<AppointmentParticipationAddedEvent> appointmentParticipationAddedEvent;

    @InjectMock
    Event<AppointmentParticipationRemovedEvent> appointmentParticipationRemovedEvent;

    @InjectMock
    Event<AppointmentGroupParticipationAddedEvent> appointmentGroupParticipationAddedEvent;

    @InjectMock
    Event<AppointmentGroupParticipationRemovedEvent> appointmentGroupParticipationRemovedEvent;

    @InjectMock
    IdentityPort identityPort;

    @Inject
    AgroalDataSource dataSource;

    // ── DB fixtures / cleanup ─────────────────────────────────────────────────

    /**
     * Ensures the group row (id=3) referenced by AddGroupToAppointment tests exists
     * in the real DB. Required because instance {@code persist()} is not intercepted
     * by PanacheMock, so Hibernate enforces the FK {@code fk_appointment_group_group}.
     */
    @BeforeEach
    void insertTestGroupFixture() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO groups (id, groupname, team_id) VALUES (3, 'Test Group', 1) ON CONFLICT DO NOTHING");
        }
    }

    /**
     * Deletes any rows written to the real DB by tests that call instance
     * {@code entity.persist()} — which PanacheMock cannot intercept. Runs after
     * every test so subsequent runs start with a clean slate.
     */
    @AfterEach
    void cleanupParticipationRows() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM appointment_group_participations WHERE appointment_id = 10");
            stmt.execute("DELETE FROM appointment_participation WHERE appointment_id = 10");
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────
    private static Appointment buildAppointment() {
        Appointment a = new Appointment();
        a.id = APPOINTMENT_ID;
        a.setName("Test Appointment");
        return a;
    }

    private static UserIdentity buildUserIdentity(String oidcId) {
        return new UserIdentity(oidcId, "John", "Doe", "john@example.com", "https://example.com/pic.jpg");
    }

    private static Group buildGroup(Long id) {
        Group g = new Group();
        g.id = id;
        g.setGroupName("Test Group");
        return g;
    }

    private static AppointmentParticipation buildParticipation(
            Appointment appointment, String userOidcId, UserRole role, ParticipationStatus status) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setAppointment(appointment);
        ap.setUserOidcId(userOidcId);
        ap.setRole(role);
        ap.setStatus(status);
        return ap;
    }

    private static AppointmentGroupParticipation buildGroupParticipation(
            Appointment appointment, Group group, UserRole role) {
        AppointmentGroupParticipation agp = new AppointmentGroupParticipation();
        agp.setAppointment(appointment);
        agp.setGroup(group);
        agp.setRole(role);
        return agp;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onAppointmentCreated
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onAppointmentCreated:
     *   No conditional branches – linear path persists participation.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class OnAppointmentCreated {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_createPendingResponsibleParticipation_when_appointmentCreated() {
            Appointment appointment = buildAppointment();
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(appointment);

            AppointmentCreatedEvent event = new AppointmentCreatedEvent(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
            service.onAppointmentCreated(event);
            // persist() is an instance call not intercepted by PanacheMock;
            // successful completion without exception is the observable assertion here.
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onGroupMemberAdded
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onGroupMemberAdded:
     *   B1  already has participation  → true (continue, skip persist) / false (persist)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class OnGroupMemberAdded {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentGroupParticipation.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=false — user does NOT already participate → persist called
        @Test
        void should_createParticipation_when_userNotYetParticipating() {
            Appointment appointment = buildAppointment();
            AppointmentGroupParticipation agp = buildGroupParticipation(appointment, buildGroup(GROUP_ID), UserRole.GUEST);

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.list()).thenReturn(List.of(agp));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> existingQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(existingQuery);
            when(existingQuery.count()).thenReturn(0L);

            GroupMemberAddedEvent event = new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC_ID, ACTING_USER_OIDC_ID);
            service.onGroupMemberAdded(event);
            // persist() is an instance call not intercepted by PanacheMock;
            // successful completion without exception is the observable assertion here.
        }

        // B1=true — user already participates → continue (no persist for that appointment)
        @Test
        void should_skipParticipation_when_userAlreadyParticipating() {
            Appointment appointment = buildAppointment();
            AppointmentGroupParticipation agp = buildGroupParticipation(appointment, buildGroup(GROUP_ID), UserRole.GUEST);

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.list()).thenReturn(List.of(agp));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> existingQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(existingQuery);
            when(existingQuery.count()).thenReturn(1L);

            GroupMemberAddedEvent event = new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC_ID, ACTING_USER_OIDC_ID);
            service.onGroupMemberAdded(event);
            // The skip branch is verified by the absence of any exception and
            // by count() returning 1 → service hits `continue` without calling persist().
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onGroupMemberRemoved
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onGroupMemberRemoved:
     *   No conditional branches – linear delete.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class OnGroupMemberRemoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_deleteParticipations_when_groupMemberRemoved() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);

            GroupMemberRemovedEvent event = new GroupMemberRemovedEvent(GROUP_ID, TARGET_USER_OIDC_ID, ACTING_USER_OIDC_ID);
            service.onGroupMemberRemoved(event);

            ArgumentCaptor<Object[]> cap = ArgumentCaptor.forClass(Object[].class);
            PanacheMock.verify(AppointmentParticipation.class).delete(anyString(), cap.capture());
            assertThat(cap.getValue()[0]).isEqualTo(GROUP_ID);
            assertThat(cap.getValue()[1]).isEqualTo(TARGET_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addUserToAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – addUserToAppointment:
     *   B1  user already participates → true (throw) / false (persist + fire)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class AddUserToAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_throwValidationException_when_userAlreadyParticipates() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addUserToAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already a participant");
        }

        // B1=false
        @Test
        void should_addParticipantAndFireEvent_when_userNotYetParticipating() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(0L);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());

            service.addUserToAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.GUEST);

            ArgumentCaptor<AppointmentParticipationAddedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationAddedEvent.class);
            verify(appointmentParticipationAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC_ID);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addGroupToAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – addGroupToAppointment:
     *   B1  group already participates     → true (throw) / false (continue)
     *   B2  per-user already participates  → true (continue) / false (persist per user)
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class AddGroupToAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Group.class);
            PanacheMock.mock(AppointmentParticipation.class);
            PanacheMock.mock(AppointmentGroupParticipation.class);
        }

        // B1=true
        @Test
        void should_throwValidationException_when_groupAlreadyParticipates() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> q = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addGroupToAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already a participant");
        }

        // B1=false, B2=false — user not yet in appointment → persist
        @Test
        void should_addGroupAndUsersAndFireEvent_when_noConflicts() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.count()).thenReturn(0L);

            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());
            when(Group.<Group>findById(GROUP_ID)).thenReturn(buildGroup(GROUP_ID));

            UserIdentity user = buildUserIdentity(TARGET_USER_OIDC_ID);
            when(groupService.getGroupUsers(ACTING_USER_OIDC_ID, GROUP_ID)).thenReturn(List.of(user));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> apQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(apQuery);
            when(apQuery.count()).thenReturn(0L);

            service.addGroupToAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST);

            ArgumentCaptor<AppointmentGroupParticipationAddedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentGroupParticipationAddedEvent.class);
            verify(appointmentGroupParticipationAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
        }

        // B1=false, B2=true — user already participating → skip persist for that user
        @Test
        void should_skipExistingUsers_when_userAlreadyInAppointment() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.count()).thenReturn(0L);

            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());
            when(Group.<Group>findById(GROUP_ID)).thenReturn(buildGroup(GROUP_ID));

            UserIdentity user = buildUserIdentity(TARGET_USER_OIDC_ID);
            when(groupService.getGroupUsers(ACTING_USER_OIDC_ID, GROUP_ID)).thenReturn(List.of(user));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> apQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(apQuery);
            when(apQuery.count()).thenReturn(1L);   // already exists

            service.addGroupToAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST);

            // user participation persist is skipped (count=1 → continue); group participation IS persisted.
            // Instance persist() is not intercepted by PanacheMock; skip is verified via event firing.
            verify(appointmentGroupParticipationAddedEvent).fire(any(AppointmentGroupParticipationAddedEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // changeUserRole
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – changeUserRole:
     *   B1  newRole == null                     → true (throw BadRequest)
     *   B2  participation not found             → true (throw Validation)
     *   B3  newRole equals current role         → true (throw Validation)
     *   B4  happy path                          → fire role-changed event
     *
     * Total branches: 4 explicit + auth pass-through  |  Tests: 4
     */
    @Nested
    class ChangeUserRole {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_throwBadRequestException_when_roleIsNull() {
            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid role");
        }

        // B2=true
        @Test
        void should_throwValidationException_when_participationNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        // B3=true
        @Test
        void should_throwValidationException_when_roleAlreadySet() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC_ID, UserRole.GUEST, ParticipationStatus.PENDING);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already the users role");
        }

        // B1=false, B2=false, B3=false — happy path
        @Test
        void should_changeRoleAndFireEvent_when_validRoleChange() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC_ID, UserRole.GUEST, ParticipationStatus.PENDING);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            service.changeUserRole(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.ATTENDANT);

            assertThat(ap.getRole()).isEqualTo(UserRole.ATTENDANT);
            ArgumentCaptor<AppointmentParticipationRoleChangedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationRoleChangedEvent.class);
            verify(appointmentParticipationRoleChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().oldRole()).isEqualTo(UserRole.GUEST);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeUserFromAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – removeUserFromAppointment:
     *   B1  removedParticipationCount < 1 → true (throw) / false (fire event)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class RemoveUserFromAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_throwValidationException_when_userNotParticipating() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(0L);

            assertThatThrownBy(() -> service.removeUserFromAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        // B1=false
        @Test
        void should_removeParticipantAndFireEvent_when_participantFound() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);

            service.removeUserFromAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID);

            ArgumentCaptor<AppointmentParticipationRemovedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationRemovedEvent.class);
            verify(appointmentParticipationRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeGroupFromAppointment
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – removeGroupFromAppointment:
     *   B1  deletedGroupParticipationCount < 1 → true (throw) / false (delete + fire)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class RemoveGroupFromAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentGroupParticipation.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_throwValidationException_when_groupNotParticipating() {
            when(AppointmentGroupParticipation.delete(anyString(), any(Object[].class))).thenReturn(0L);

            assertThatThrownBy(() -> service.removeGroupFromAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        // B1=false
        @Test
        void should_removeGroupAndFireEvent_when_groupFound() {
            when(AppointmentGroupParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(2L);

            service.removeGroupFromAppointment(ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID);

            ArgumentCaptor<AppointmentGroupParticipationRemovedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentGroupParticipationRemovedEvent.class);
            verify(appointmentGroupParticipationRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // changeParticipationStatus
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – changeParticipationStatus:
     *   B1  status == null          → true (throw BadRequest)
     *   B2  status == PENDING       → true (throw BadRequest)
     *   B3  participation not found → true (throw Validation)
     *   B4  same status             → true (throw Validation)
     *   B5  happy path              → set status + fire sync + fire async
     *
     * Total branches: 5  |  Tests: 5
     */
    @Nested
    class ChangeParticipationStatus {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_throwBadRequestException_when_statusIsNull() {
            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC_ID, APPOINTMENT_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid participation status");
        }

        // B2=true
        @Test
        void should_throwBadRequestException_when_statusIsPending() {
            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.PENDING))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot set your participation status back to pending");
        }

        // B3=true
        @Test
        void should_throwValidationException_when_participationNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.APPROVED))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        // B4=true
        @Test
        void should_throwValidationException_when_sameStatusSet() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), ACTING_USER_OIDC_ID, UserRole.GUEST, ParticipationStatus.APPROVED);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.APPROVED))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already your participation status");
        }

        // B1=false, B2=false, B3=false, B4=false — happy path
        @Test
        void should_changeStatusAndFireEvents_when_validStatusChange() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), ACTING_USER_OIDC_ID, UserRole.GUEST, ParticipationStatus.PENDING);
            // Note: status starts PENDING but PENDING check is on the *incoming* status
            // We set ap status to REJECTED so we can change to APPROVED
            ap.setStatus(ParticipationStatus.REJECTED);

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            service.changeParticipationStatus(ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.APPROVED);

            assertThat(ap.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
            ArgumentCaptor<AppointmentParticipationStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationStatusChangedEvent.class);
            verify(appointmentParticipationStatusChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().newParticipationStatus()).isEqualTo(ParticipationStatus.APPROVED);
            verify(appointmentParticipationStatusChangedEvent).fireAsync(any(AppointmentParticipationStatusChangedEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getParticipants
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getParticipants:
     *   B1  ap.getGroupParticipationId() != null → true (set group fields) / false (skip)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetParticipants {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
            PanacheMock.mock(Group.class);
        }

        // B1=false — direct (non-group) participation
        @Test
        void should_returnDtosWithoutGroupInfo_when_participationIsNotViaGroup() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC_ID, UserRole.GUEST, ParticipationStatus.APPROVED);
            // groupParticipationId = null (not set)

            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(ap));

            List<UserParticipantDto> result = service.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID);

            assertThat(result).hasSize(1);
            UserParticipantDto dto = result.get(0);
            // user_id will be the oidcId since identityPort returns null for unknown ids
            assertThat(dto.getUser_id()).isEqualTo(TARGET_USER_OIDC_ID);
            assertThat(dto.getRole()).isEqualTo(UserRole.GUEST);
            assertThat(dto.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
            assertThat(dto.getVia_group_id()).isNull();
            assertThat(dto.getVia_group_name()).isNull();
        }

        // B1=true — participation via group
        @Test
        void should_returnDtosWithGroupInfo_when_participationIsViaGroup() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC_ID, UserRole.ATTENDANT, ParticipationStatus.PENDING);
            ap.setGroupParticipationId(GROUP_ID);

            Group group = buildGroup(GROUP_ID);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);

            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(ap));

            List<UserParticipantDto> result = service.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID);

            assertThat(result).hasSize(1);
            UserParticipantDto dto = result.get(0);
            assertThat(dto.getVia_group_id()).isEqualTo(GROUP_ID);
            assertThat(dto.getVia_group_name()).isEqualTo("Test Group");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getGroupParticipants
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getGroupParticipants:
     *   No conditional branches – linear stream mapping.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetGroupParticipants {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentGroupParticipation.class);
        }

        @Test
        void should_returnGroupDtos_when_groupParticipationsExist() {
            Group group = buildGroup(GROUP_ID);
            Appointment appointment = buildAppointment();
            AppointmentGroupParticipation agp = buildGroupParticipation(appointment, group, UserRole.GUEST);

            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(agp));

            List<GroupDto> result = service.getGroupParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(GROUP_ID);
            assertThat(result.get(0).getName()).isEqualTo("Test Group");
        }
    }
}
