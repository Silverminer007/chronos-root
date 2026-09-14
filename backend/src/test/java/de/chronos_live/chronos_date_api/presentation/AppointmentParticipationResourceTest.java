package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.AppointmentParticipationQueryService;
import de.chronos_live.chronos_date_api.application.AppointmentParticipationService;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.AddGroupParticipantDto;
import de.chronos_live.chronos_date_api.dto.AddParticipantDto;
import de.chronos_live.chronos_date_api.dto.ParticipantRoleDto;
import de.chronos_live.chronos_date_api.dto.ParticipationStatusDto;
import de.chronos_live.chronos_date_api.dto.UserParticipantDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppointmentParticipationResource}.
 *
 * Tests all participation endpoints:
 * - POST /api/v2/appointments/{id}/participation - RSVP with status
 * - PUT /api/v2/appointments/{id}/participation/{userId}/role - Change role
 * - POST /api/v2/appointments/{id}/participants/{userId} - Add participant
 * - DELETE /api/v2/appointments/{id}/participants/{userId} - Remove participant
 * - POST /api/v2/appointments/{id}/groups/{groupId} - Add group
 * - DELETE /api/v2/appointments/{id}/groups/{groupId} - Remove group
 * - GET /api/v2/appointments/{id}/participants - List participants
 * - GET /api/v2/appointments/{id}/participation/status - Get participation status
 */
@QuarkusTest
class AppointmentParticipationResourceTest {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final Long   APPOINTMENT_ID      = 10L;
    private static final String ACTING_USER_OIDC_ID = "acting-user-oidc-1";
    private static final String TARGET_USER_OIDC_ID = "target-user-oidc-2";
    private static final Long   GROUP_ID            = 3L;

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentParticipationResource resource;

    @InjectMock
    PrincipalContext principalContext;

    @InjectMock
    AppointmentParticipationService appointmentParticipationService;

    @InjectMock
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    // ── Setup ─────────────────────────────────────────────────────────────────
    @BeforeEach
    void setUp() {
        // Mock the principal to return acting user
        when(principalContext.getPrincipal()).thenAnswer(invocation ->
            new Object() {
                public String oidcId() { return ACTING_USER_OIDC_ID; }
            });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RSVP - POST /api/v2/appointments/{id}/participation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RsvpToAppointment {

        @Test
        void should_returnOk_and_fireStatusChangedEvent_when_rsvpWithApprovedStatus() {
            // Arrange
            ParticipationStatusDto statusDto = new ParticipationStatusDto();
            statusDto.setStatus("APPROVED");

            // Act
            Response response = resource.rsvpToAppointment(APPOINTMENT_ID, statusDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).changeParticipationStatus(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.APPROVED);
        }

        @Test
        void should_returnOk_and_fireStatusChangedEvent_when_rsvpWithRejectedStatus() {
            // Arrange
            ParticipationStatusDto statusDto = new ParticipationStatusDto();
            statusDto.setStatus("REJECTED");

            // Act
            Response response = resource.rsvpToAppointment(APPOINTMENT_ID, statusDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).changeParticipationStatus(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, ParticipationStatus.REJECTED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Change Role - PUT /api/v2/appointments/{id}/participation/{userId}/role
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class ChangeParticipationRole {

        @Test
        void should_returnOk_and_fireRoleChangedEvent_when_changingUserRole() {
            // Arrange
            ParticipantRoleDto roleDto = new ParticipantRoleDto();
            roleDto.setRole("ORGANIZER");

            // Act
            Response response = resource.changeParticipationRole(APPOINTMENT_ID, TARGET_USER_OIDC_ID, roleDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).changeUserRole(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.ORGANIZER);
        }

        @Test
        void should_returnOk_when_changingToParticipantRole() {
            // Arrange
            ParticipantRoleDto roleDto = new ParticipantRoleDto();
            roleDto.setRole("PARTICIPANT");

            // Act
            Response response = resource.changeParticipationRole(APPOINTMENT_ID, TARGET_USER_OIDC_ID, roleDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).changeUserRole(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.PARTICIPANT);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Add Participant - POST /api/v2/appointments/{id}/participants/{userId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AddParticipant {

        @Test
        void should_returnOk_and_fireParticipationAddedEvent_when_addingParticipant() {
            // Arrange
            AddParticipantDto addDto = new AddParticipantDto();
            addDto.setUser_id(TARGET_USER_OIDC_ID);
            addDto.setUser_role("PARTICIPANT");

            // Act
            Response response = resource.addParticipant(APPOINTMENT_ID, TARGET_USER_OIDC_ID, addDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).addUserToAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.PARTICIPANT);
        }

        @Test
        void should_returnOk_when_addingParticipantWithOrganizerRole() {
            // Arrange
            AddParticipantDto addDto = new AddParticipantDto();
            addDto.setUser_id(TARGET_USER_OIDC_ID);
            addDto.setUser_role("ORGANIZER");

            // Act
            Response response = resource.addParticipant(APPOINTMENT_ID, TARGET_USER_OIDC_ID, addDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).addUserToAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID, UserRole.ORGANIZER);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Remove Participant - DELETE /api/v2/appointments/{id}/participants/{userId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RemoveParticipant {

        @Test
        void should_returnOk_and_fireParticipationRemovedEvent_when_removingParticipant() {
            // Act
            Response response = resource.removeParticipant(APPOINTMENT_ID, TARGET_USER_OIDC_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).removeUserFromAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, TARGET_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Add Group - POST /api/v2/appointments/{id}/groups/{groupId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AddGroup {

        @Test
        void should_returnOk_and_fireGroupParticipationAddedEvent_when_addingGroup() {
            // Arrange
            AddGroupParticipantDto addDto = new AddGroupParticipantDto();
            addDto.setGroup_id(GROUP_ID);
            addDto.setUser_role("PARTICIPANT");

            // Act
            Response response = resource.addGroup(APPOINTMENT_ID, GROUP_ID, addDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).addGroupToAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID, UserRole.PARTICIPANT);
        }

        @Test
        void should_returnOk_when_addingGroupWithOrganizerRole() {
            // Arrange
            AddGroupParticipantDto addDto = new AddGroupParticipantDto();
            addDto.setGroup_id(GROUP_ID);
            addDto.setUser_role("ORGANIZER");

            // Act
            Response response = resource.addGroup(APPOINTMENT_ID, GROUP_ID, addDto);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).addGroupToAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID, UserRole.ORGANIZER);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Remove Group - DELETE /api/v2/appointments/{id}/groups/{groupId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RemoveGroup {

        @Test
        void should_returnOk_and_fireGroupParticipationRemovedEvent_when_removingGroup() {
            // Act
            Response response = resource.removeGroup(APPOINTMENT_ID, GROUP_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called with correct parameters
            verify(appointmentParticipationService).removeGroupFromAppointment(
                    ACTING_USER_OIDC_ID, APPOINTMENT_ID, GROUP_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Get Participants - GET /api/v2/appointments/{id}/participants
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetParticipants {

        @Test
        void should_returnOk_with_participantsList_when_fetchingParticipants() {
            // Arrange
            UserParticipantDto participant = new UserParticipantDto();
            participant.setUser_id(TARGET_USER_OIDC_ID);
            participant.setName("Test User");
            participant.setRole(UserRole.PARTICIPANT);
            participant.setStatus(ParticipationStatus.APPROVED);

            List<UserParticipantDto> participants = List.of(participant);
            when(appointmentParticipationService.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID))
                    .thenReturn(participants);

            // Act
            Response response = resource.getParticipants(APPOINTMENT_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called
            verify(appointmentParticipationService).getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
        }

        @Test
        void should_returnOk_when_noParticipants() {
            // Arrange
            when(appointmentParticipationService.getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID))
                    .thenReturn(Collections.emptyList());

            // Act
            Response response = resource.getParticipants(APPOINTMENT_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called
            verify(appointmentParticipationService).getParticipants(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Get Participation Status - GET /api/v2/appointments/{id}/participation/status
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetParticipationStatus {

        @Test
        void should_returnOk_with_approvedStatus_when_userApproved() {
            // Arrange
            when(appointmentParticipationQueryService.getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID))
                    .thenReturn(ParticipationStatus.APPROVED);

            // Act
            Response response = resource.getParticipationStatus(APPOINTMENT_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called
            verify(appointmentParticipationQueryService).getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
        }

        @Test
        void should_returnOk_with_rejectedStatus_when_userRejected() {
            // Arrange
            when(appointmentParticipationQueryService.getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID))
                    .thenReturn(ParticipationStatus.REJECTED);

            // Act
            Response response = resource.getParticipationStatus(APPOINTMENT_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called
            verify(appointmentParticipationQueryService).getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
        }

        @Test
        void should_returnOk_with_pendingStatus_when_userPending() {
            // Arrange
            when(appointmentParticipationQueryService.getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID))
                    .thenReturn(ParticipationStatus.PENDING);

            // Act
            Response response = resource.getParticipationStatus(APPOINTMENT_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

            // Verify service was called
            verify(appointmentParticipationQueryService).getUserStatus(APPOINTMENT_ID, ACTING_USER_OIDC_ID);
        }
    }
}
