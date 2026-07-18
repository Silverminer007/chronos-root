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
 * <p>User identity is now represented by OIDC ID strings instead of Long user IDs.
 *
 * <p><b>Coverage plan</b>
 * <pre>
 * areFriends(oidcId1, oidcId2)
 *   — no conditional branches; pure delegation, returns boolean.
 *   Tests: 2 (true / false)
 *
 * getFriends(oidcId)
 *   — no conditional branches; pure delegation, returns Set.
 *   Tests: 1
 *
 * findFriendship(oidcId1, oidcId2)
 *   — no conditional branches; pure delegation, returns Optional.
 *   Tests: 2 (present / empty)
 *
 * getFriendshipStatus(requesterOidcId, addresseeOidcId)
 *   B1  Optional.isPresent() → true  (returns status) / false (returns null)
 *   Tests: 2
 * </pre>
 *
 * <p><b>Untestable branches:</b> none.
 */
@QuarkusTest
class FriendshipQueryServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String USER_1_OIDC = "oidc-user-1";
    private static final String USER_2_OIDC = "oidc-user-2";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    FriendshipQueryService service;

    @InjectMock
    FriendshipRepository friendshipRepo;

    // ── Test-object builder ───────────────────────────────────────────────────
    private static FriendshipRequest buildAcceptedFriendship() {
        FriendshipRequest fr = new FriendshipRequest();
        fr.id = 100L;
        fr.setRequesterId(USER_1_OIDC);
        fr.setAddresseeId(USER_2_OIDC);
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
            when(friendshipRepo.areFriends(USER_1_OIDC, USER_2_OIDC)).thenReturn(true);

            boolean result = service.areFriends(USER_1_OIDC, USER_2_OIDC);

            assertThat(result).isTrue();
            verify(friendshipRepo, times(1)).areFriends(USER_1_OIDC, USER_2_OIDC);
        }

        @Test
        void should_returnFalse_when_repositoryDeniesFriendship() {
            when(friendshipRepo.areFriends(USER_1_OIDC, USER_2_OIDC)).thenReturn(false);

            boolean result = service.areFriends(USER_1_OIDC, USER_2_OIDC);

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
            Set<String> expected = Set.of(USER_2_OIDC, "oidc-user-3");
            when(friendshipRepo.getFriendOidcIds(USER_1_OIDC)).thenReturn(expected);

            Set<String> result = service.getFriends(USER_1_OIDC);

            assertThat(result).isSameAs(expected);
            verify(friendshipRepo, times(1)).getFriendOidcIds(USER_1_OIDC);
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
            when(friendshipRepo.findFriendship(USER_1_OIDC, USER_2_OIDC)).thenReturn(Optional.of(fr));

            Optional<FriendshipRequest> result = service.findFriendship(USER_1_OIDC, USER_2_OIDC);

            assertThat(result).isPresent().contains(fr);
        }

        @Test
        void should_returnEmptyOptional_when_friendshipDoesNotExist() {
            when(friendshipRepo.findFriendship(USER_1_OIDC, USER_2_OIDC)).thenReturn(Optional.empty());

            Optional<FriendshipRequest> result = service.findFriendship(USER_1_OIDC, USER_2_OIDC);

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
            when(friendshipRepo.findRequest(USER_1_OIDC, USER_2_OIDC)).thenReturn(Optional.of(fr));

            FriendshipStatus result = service.getFriendshipStatus(USER_1_OIDC, USER_2_OIDC);

            assertThat(result).isEqualTo(FriendshipStatus.ACCEPTED);
        }

        // B1=false — no request found → returns null
        @Test
        void should_returnNull_when_noRequestExists() {
            when(friendshipRepo.findRequest(USER_1_OIDC, USER_2_OIDC)).thenReturn(Optional.empty());

            FriendshipStatus result = service.getFriendshipStatus(USER_1_OIDC, USER_2_OIDC);

            assertThat(result).isNull();
        }
    }
}
