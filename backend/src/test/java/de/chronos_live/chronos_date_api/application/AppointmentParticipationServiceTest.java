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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentParticipationService}.
 *
 * <p>User IDs are now OIDC strings, not database Long IDs. Identity is read via
 * {@link IdentityPort} (mocked); the deleted {@code User} entity is gone.
 */
@QuarkusTest
class AppointmentParticipationServiceTest {

    private static final Long   APPOINTMENT_ID    = 10L;
    private static final String ACTING_USER_OIDC  = "oidc-acting-user";
    private static final String TARGET_USER_OIDC  = "oidc-target-user";
    private static final Long   GROUP_ID          = 3L;

    @Inject
    AppointmentParticipationService service;

    @InjectMock
    AuthorizationService authorizationService;

    @InjectMock
    GroupService groupService;

    @InjectMock
    IdentityPort identityPort;

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

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void insertTestGroupFixture() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO groups (id, groupname, owner_oidcid) VALUES (3, 'Test Group', 'oidc-acting-user') ON CONFLICT DO NOTHING");
        }
    }

    @AfterEach
    void cleanupParticipationRows() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM appointment_group_participations WHERE appointment_id = 10");
            stmt.execute("DELETE FROM appointment_participation WHERE appointment_id = 10");
        }
    }

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

    @Nested
    class OnAppointmentCreated {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_createPendingResponsibleParticipation_when_appointmentCreated() {
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());

            service.onAppointmentCreated(new AppointmentCreatedEvent(APPOINTMENT_ID, ACTING_USER_OIDC));
        }
    }

    @Nested
    class OnGroupMemberAdded {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentGroupParticipation.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

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

            service.onGroupMemberAdded(new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));
        }

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

            service.onGroupMemberAdded(new GroupMemberAddedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));
        }
    }

    @Nested
    class OnGroupMemberRemoved {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_deleteParticipations_when_groupMemberRemoved() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);

            service.onGroupMemberRemoved(new GroupMemberRemovedEvent(GROUP_ID, TARGET_USER_OIDC, ACTING_USER_OIDC));

            ArgumentCaptor<Object[]> cap = ArgumentCaptor.forClass(Object[].class);
            PanacheMock.verify(AppointmentParticipation.class).delete(anyString(), cap.capture());
            assertThat(cap.getValue()[0]).isEqualTo(GROUP_ID);
            assertThat(cap.getValue()[1]).isEqualTo(TARGET_USER_OIDC);
        }
    }

    @Nested
    class AddUserToAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_throwValidationException_when_userAlreadyParticipates() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addUserToAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already a participant");
        }

        @Test
        void should_addParticipantAndFireEvent_when_userNotYetParticipating() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(0L);
            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());

            service.addUserToAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, UserRole.GUEST);

            ArgumentCaptor<AppointmentParticipationAddedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationAddedEvent.class);
            verify(appointmentParticipationAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC);
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(ACTING_USER_OIDC);
        }
    }

    @Nested
    class AddGroupToAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(Appointment.class);
            PanacheMock.mock(Group.class);
            PanacheMock.mock(AppointmentParticipation.class);
            PanacheMock.mock(AppointmentGroupParticipation.class);
        }

        @Test
        void should_throwValidationException_when_groupAlreadyParticipates() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> q = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.count()).thenReturn(1L);

            assertThatThrownBy(() -> service.addGroupToAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already a participant");
        }

        @Test
        void should_addGroupAndUsersAndFireEvent_when_noConflicts() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.count()).thenReturn(0L);

            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());
            when(Group.<Group>findById(GROUP_ID)).thenReturn(buildGroup(GROUP_ID));

            UserIdentity user = buildUserIdentity(TARGET_USER_OIDC);
            when(groupService.getGroupUsers(ACTING_USER_OIDC, GROUP_ID)).thenReturn(List.of(user));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> apQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(apQuery);
            when(apQuery.count()).thenReturn(0L);

            service.addGroupToAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST);

            ArgumentCaptor<AppointmentGroupParticipationAddedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentGroupParticipationAddedEvent.class);
            verify(appointmentGroupParticipationAddedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
        }

        @Test
        void should_skipExistingUsers_when_userAlreadyInAppointment() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentGroupParticipation> agpQuery = mock(PanacheQuery.class);
            when(AppointmentGroupParticipation.<AppointmentGroupParticipation>find(anyString(), any(Object[].class))).thenReturn(agpQuery);
            when(agpQuery.count()).thenReturn(0L);

            when(Appointment.<Appointment>findById(APPOINTMENT_ID)).thenReturn(buildAppointment());
            when(Group.<Group>findById(GROUP_ID)).thenReturn(buildGroup(GROUP_ID));

            UserIdentity user = buildUserIdentity(TARGET_USER_OIDC);
            when(groupService.getGroupUsers(ACTING_USER_OIDC, GROUP_ID)).thenReturn(List.of(user));

            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> apQuery = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(apQuery);
            when(apQuery.count()).thenReturn(1L);

            service.addGroupToAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, GROUP_ID, UserRole.GUEST);

            verify(appointmentGroupParticipationAddedEvent).fire(any(AppointmentGroupParticipationAddedEvent.class));
        }
    }

    @Nested
    class ChangeUserRole {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_throwBadRequestException_when_roleIsNull() {
            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid role");
        }

        @Test
        void should_throwValidationException_when_participationNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        @Test
        void should_throwValidationException_when_roleAlreadySet() {
            AppointmentParticipation ap = buildParticipation(buildAppointment(), TARGET_USER_OIDC, UserRole.GUEST, ParticipationStatus.PENDING);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            assertThatThrownBy(() -> service.changeUserRole(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, UserRole.GUEST))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already the users role");
        }

        @Test
        void should_changeRoleAndFireEvent_when_validRoleChange() {
            AppointmentParticipation ap = buildParticipation(buildAppointment(), TARGET_USER_OIDC, UserRole.GUEST, ParticipationStatus.PENDING);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            service.changeUserRole(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC, UserRole.ATTENDANT);

            assertThat(ap.getRole()).isEqualTo(UserRole.ATTENDANT);
            ArgumentCaptor<AppointmentParticipationRoleChangedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationRoleChangedEvent.class);
            verify(appointmentParticipationRoleChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().oldRole()).isEqualTo(UserRole.GUEST);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC);
        }
    }

    @Nested
    class RemoveUserFromAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_throwValidationException_when_userNotParticipating() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(0L);

            assertThatThrownBy(() -> service.removeUserFromAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        @Test
        void should_removeParticipantAndFireEvent_when_participantFound() {
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);

            service.removeUserFromAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, TARGET_USER_OIDC);

            ArgumentCaptor<AppointmentParticipationRemovedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationRemovedEvent.class);
            verify(appointmentParticipationRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().targetUserOidcId()).isEqualTo(TARGET_USER_OIDC);
        }
    }

    @Nested
    class RemoveGroupFromAppointment {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentGroupParticipation.class);
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_throwValidationException_when_groupNotParticipating() {
            when(AppointmentGroupParticipation.delete(anyString(), any(Object[].class))).thenReturn(0L);

            assertThatThrownBy(() -> service.removeGroupFromAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, GROUP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        @Test
        void should_removeGroupAndFireEvent_when_groupFound() {
            when(AppointmentGroupParticipation.delete(anyString(), any(Object[].class))).thenReturn(1L);
            when(AppointmentParticipation.delete(anyString(), any(Object[].class))).thenReturn(2L);

            service.removeGroupFromAppointment(ACTING_USER_OIDC, APPOINTMENT_ID, GROUP_ID);

            ArgumentCaptor<AppointmentGroupParticipationRemovedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentGroupParticipationRemovedEvent.class);
            verify(appointmentGroupParticipationRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(captor.getValue().groupId()).isEqualTo(GROUP_ID);
        }
    }

    @Nested
    class ChangeParticipationStatus {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_throwBadRequestException_when_statusIsNull() {
            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC, APPOINTMENT_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid participation status");
        }

        @Test
        void should_throwBadRequestException_when_statusIsPending() {
            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC, APPOINTMENT_ID, ParticipationStatus.PENDING))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot set your participation status back to pending");
        }

        @Test
        void should_throwValidationException_when_participationNotFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC, APPOINTMENT_ID, ParticipationStatus.APPROVED))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not a participant");
        }

        @Test
        void should_throwValidationException_when_sameStatusSet() {
            AppointmentParticipation ap = buildParticipation(buildAppointment(), ACTING_USER_OIDC, UserRole.GUEST, ParticipationStatus.APPROVED);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            assertThatThrownBy(() -> service.changeParticipationStatus(ACTING_USER_OIDC, APPOINTMENT_ID, ParticipationStatus.APPROVED))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already your participation status");
        }

        @Test
        void should_changeStatusAndFireEvents_when_validStatusChange() {
            AppointmentParticipation ap = buildParticipation(buildAppointment(), ACTING_USER_OIDC, UserRole.GUEST, ParticipationStatus.REJECTED);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            service.changeParticipationStatus(ACTING_USER_OIDC, APPOINTMENT_ID, ParticipationStatus.APPROVED);

            assertThat(ap.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
            ArgumentCaptor<AppointmentParticipationStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(AppointmentParticipationStatusChangedEvent.class);
            verify(appointmentParticipationStatusChangedEvent).fire(captor.capture());
            assertThat(captor.getValue().newParticipationStatus()).isEqualTo(ParticipationStatus.APPROVED);
            verify(appointmentParticipationStatusChangedEvent).fireAsync(any(AppointmentParticipationStatusChangedEvent.class));
        }
    }

    @Nested
    class GetParticipants {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
            PanacheMock.mock(Group.class);
        }

        @Test
        void should_returnDtosWithoutGroupInfo_when_participationIsNotViaGroup() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC, UserRole.GUEST, ParticipationStatus.APPROVED);

            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(ap));

            UserIdentity identity = buildUserIdentity(TARGET_USER_OIDC);
            when(identityPort.findByIds(any(Collection.class)))
                    .thenReturn(Map.of(TARGET_USER_OIDC, identity));

            List<UserParticipantDto> result = service.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC);

            assertThat(result).hasSize(1);
            UserParticipantDto dto = result.get(0);
            assertThat(dto.getUser_id()).isEqualTo(TARGET_USER_OIDC);
            assertThat(dto.getRole()).isEqualTo(UserRole.GUEST);
            assertThat(dto.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
            assertThat(dto.getVia_group_id()).isNull();
            assertThat(dto.getVia_group_name()).isNull();
        }

        @Test
        void should_returnDtosWithGroupInfo_when_participationIsViaGroup() {
            AppointmentParticipation ap = buildParticipation(
                    buildAppointment(), TARGET_USER_OIDC, UserRole.ATTENDANT, ParticipationStatus.PENDING);
            ap.setGroupParticipationId(GROUP_ID);

            Group group = buildGroup(GROUP_ID);
            when(Group.<Group>findById(GROUP_ID)).thenReturn(group);
            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(ap));

            UserIdentity identity = buildUserIdentity(TARGET_USER_OIDC);
            when(identityPort.findByIds(any(Collection.class)))
                    .thenReturn(Map.of(TARGET_USER_OIDC, identity));

            List<UserParticipantDto> result = service.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC);

            assertThat(result).hasSize(1);
            UserParticipantDto dto = result.get(0);
            assertThat(dto.getVia_group_id()).isEqualTo(GROUP_ID);
            assertThat(dto.getVia_group_name()).isEqualTo("Test Group");
        }
    }

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

            List<GroupDto> result = service.getGroupParticipants(APPOINTMENT_ID, ACTING_USER_OIDC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(GROUP_ID);
            assertThat(result.get(0).getName()).isEqualTo("Test Group");
        }
    }
}
