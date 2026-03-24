package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FriendshipQueryService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces the
 * {@link FriendshipRepository} CDI bean with a Mockito mock. No Panache
 * static mocking is needed because the service only calls repository
 * methods (no direct Panache static calls in the service layer itself).
 *
 * <p><b>Coverage plan</b>
 * <pre>
 * areFriends(userId1, userId2)
 *   — no conditional branches; pure delegation, returns boolean.
 *   Tests: 2 (true / false)
 *
 * getFriends(userId)
 *   — no conditional branches; pure delegation, returns Set.
 *   Tests: 1
 *
 * findFriendship(userId1, userId2)
 *   — no conditional branches; pure delegation, returns Optional.
 *   Tests: 2 (present / empty)
 *
 * getFriendshipStatus(requesterId, addresseeId)
 *   B1  Optional.isPresent() → true  (returns status) / false (returns null)
 *   Tests: 2
 * </pre>
 *
 * <p><b>Untestable branches:</b> none.
 */
@QuarkusTest
class FriendshipQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long USER_1_ID = 1L;
    private static final Long USER_2_ID = 2L;

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    FriendshipQueryService service;

    @InjectMock
    FriendshipRepository friendshipRepo;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static FriendshipRequest buildAcceptedFriendship() {
        FriendshipRequest fr = new FriendshipRequest();
        fr.id = 100L;
        fr.setRequesterId(USER_1_ID);
        fr.setAddresseeId(USER_2_ID);
        fr.setStatus(FriendshipStatus.ACCEPTED);
        fr.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return fr;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // areFriends
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class AreFriends {

        @Test
        void should_returnTrue_when_repositoryConfirmsFriendship() {
            when(friendshipRepo.areFriends(USER_1_ID, USER_2_ID)).thenReturn(true);

            boolean result = service.areFriends(USER_1_ID, USER_2_ID);

            assertThat(result).isTrue();
            verify(friendshipRepo, times(1)).areFriends(USER_1_ID, USER_2_ID);
        }

        @Test
        void should_returnFalse_when_repositoryDeniesFriendship() {
            when(friendshipRepo.areFriends(USER_1_ID, USER_2_ID)).thenReturn(false);

            boolean result = service.areFriends(USER_1_ID, USER_2_ID);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getFriends
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetFriends {

        @Test
        void should_returnFriendIdSet_when_delegatingToRepository() {
            Set<Long> expected = Set.of(USER_2_ID, 3L);
            when(friendshipRepo.getFriendIds(USER_1_ID)).thenReturn(expected);

            Set<Long> result = service.getFriends(USER_1_ID);

            assertThat(result).isSameAs(expected);
            verify(friendshipRepo, times(1)).getFriendIds(USER_1_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findFriendship
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class FindFriendship {

        @Test
        void should_returnPresentOptional_when_friendshipExists() {
            FriendshipRequest fr = buildAcceptedFriendship();
            when(friendshipRepo.findFriendship(USER_1_ID, USER_2_ID)).thenReturn(Optional.of(fr));

            Optional<FriendshipRequest> result = service.findFriendship(USER_1_ID, USER_2_ID);

            assertThat(result).isPresent().contains(fr);
        }

        @Test
        void should_returnEmptyOptional_when_friendshipDoesNotExist() {
            when(friendshipRepo.findFriendship(USER_1_ID, USER_2_ID)).thenReturn(Optional.empty());

            Optional<FriendshipRequest> result = service.findFriendship(USER_1_ID, USER_2_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getFriendshipStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetFriendshipStatus {

        // B1=true — request found → returns its status
        @Test
        void should_returnStatus_when_requestExists() {
            FriendshipRequest fr = buildAcceptedFriendship();
            when(friendshipRepo.findRequest(USER_1_ID, USER_2_ID)).thenReturn(Optional.of(fr));

            FriendshipStatus result = service.getFriendshipStatus(USER_1_ID, USER_2_ID);

            assertThat(result).isEqualTo(FriendshipStatus.ACCEPTED);
        }

        // B1=false — no request found → returns null
        @Test
        void should_returnNull_when_noRequestExists() {
            when(friendshipRepo.findRequest(USER_1_ID, USER_2_ID)).thenReturn(Optional.empty());

            FriendshipStatus result = service.getFriendshipStatus(USER_1_ID, USER_2_ID);

            assertThat(result).isNull();
        }
    }
}
