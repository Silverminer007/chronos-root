package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppointmentParticipationQueryService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@link PanacheMock} intercepts all static
 * Panache calls on {@link AppointmentParticipation}. No real database is touched.
 *
 * <p><b>Untestable branches:</b><br>
 * None. Every reachable branch is exercised.
 */
@QuarkusTest
class AppointmentParticipationQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long APPOINTMENT_ID = 10L;
    private static final Long USER_ID        = 1L;

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    AppointmentParticipationQueryService service;

    // ── Builder ───────────────────────────────────────────────────────────────
    private static AppointmentParticipation buildParticipation(UserRole role, ParticipationStatus status) {
        AppointmentParticipation ap = new AppointmentParticipation();
        ap.setRole(role);
        ap.setStatus(status);
        return ap;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUserRole
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getUserRole:
     *   B1  participation found    → true (return role)
     *   B2  participation not found → true (return NONE)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetUserRole {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_returnActualRole_when_participationExists() {
            AppointmentParticipation ap = buildParticipation(UserRole.RESPONSIBLE, ParticipationStatus.APPROVED);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            UserRole result = service.getUserRole(APPOINTMENT_ID, USER_ID);

            assertThat(result).isEqualTo(UserRole.RESPONSIBLE);
        }

        // B2=true — returns NONE as default
        @Test
        void should_returnNone_when_noParticipationFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            UserRole result = service.getUserRole(APPOINTMENT_ID, USER_ID);

            assertThat(result).isEqualTo(UserRole.NONE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUserStatus
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getUserStatus:
     *   B1  participation found    → true (return status)
     *   B2  participation not found → true (return PENDING)
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetUserStatus {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        // B1=true
        @Test
        void should_returnActualStatus_when_participationExists() {
            AppointmentParticipation ap = buildParticipation(UserRole.GUEST, ParticipationStatus.APPROVED);
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(ap));

            ParticipationStatus result = service.getUserStatus(APPOINTMENT_ID, USER_ID);

            assertThat(result).isEqualTo(ParticipationStatus.APPROVED);
        }

        // B2=true — returns PENDING as default
        @Test
        void should_returnPending_when_noParticipationFound() {
            @SuppressWarnings("unchecked") PanacheQuery<AppointmentParticipation> q = mock(PanacheQuery.class);
            when(AppointmentParticipation.<AppointmentParticipation>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            ParticipationStatus result = service.getUserStatus(APPOINTMENT_ID, USER_ID);

            assertThat(result).isEqualTo(ParticipationStatus.PENDING);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getParticipants
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getParticipants:
     *   No conditional branches – direct Panache list delegation.
     *   Two result variants for line coverage completeness.
     *
     * Total branches: 0  |  Tests: 2
     */
    @Nested
    class GetParticipants {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_returnParticipantList_when_participantsExist() {
            AppointmentParticipation ap = buildParticipation(UserRole.ATTENDANT, ParticipationStatus.PENDING);
            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(ap));

            List<AppointmentParticipation> result = service.getParticipants(APPOINTMENT_ID);

            assertThat(result).containsExactly(ap);
        }

        @Test
        void should_returnEmptyList_when_noParticipantsExist() {
            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            List<AppointmentParticipation> result = service.getParticipants(APPOINTMENT_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getParticipationStatistik
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getParticipationStatistik:
     *   The method filters the list twice (APPROVED + REJECTED). The stream
     *   predicates create implicit branches for each element, covered by:
     *   B1  all statuses present (PENDING + APPROVED + REJECTED)
     *   B2  empty list             → all counts are 0
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetParticipationStatistik {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(AppointmentParticipation.class);
        }

        @Test
        void should_countCorrectly_when_mixedStatusesPresent() {
            AppointmentParticipation pending  = buildParticipation(UserRole.GUEST, ParticipationStatus.PENDING);
            AppointmentParticipation approved = buildParticipation(UserRole.GUEST, ParticipationStatus.APPROVED);
            AppointmentParticipation rejected = buildParticipation(UserRole.GUEST, ParticipationStatus.REJECTED);

            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(pending, approved, rejected));

            ParticipationStatistik result = service.getParticipationStatistik(APPOINTMENT_ID);

            assertThat(result.participantCount()).isEqualTo(3L);
            assertThat(result.approvedCount()).isEqualTo(1L);
            assertThat(result.rejectedCount()).isEqualTo(1L);
        }

        @Test
        void should_returnZeroCounts_when_noParticipantsExist() {
            when(AppointmentParticipation.<AppointmentParticipation>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of());

            ParticipationStatistik result = service.getParticipationStatistik(APPOINTMENT_ID);

            assertThat(result.participantCount()).isZero();
            assertThat(result.approvedCount()).isZero();
            assertThat(result.rejectedCount()).isZero();
        }
    }
}
