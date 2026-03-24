package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.PushSubscription;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionDto;
import de.chronos_live.chronos_date_api.infrastructure.PushSubscriptionRepository;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PushSubscriptionService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces the
 * {@link PushSubscriptionRepository} CDI bean with a Mockito mock, and
 * {@link PanacheMock} intercepts the static {@code User.findById} call.
 *
 * <p><b>Coverage plan</b>
 * <pre>
 * saveSubscription(userId, dto)
 *   B1  existing != null → delete + replace  / null → skip delete, just persist
 *   Tests: 2
 *
 * deleteByEndpoint(endpoint)
 *   B1  existing != null → delete  / null → no-op
 *   Tests: 2
 *
 * isSubscriptionKnown(endpoint)
 *   B1  existing != null → true  / null → false
 *   Tests: 2
 *
 * getAllForUser(userId)
 *   — no conditional branches; pure delegation to repository.
 *   Tests: 1
 * </pre>
 *
 * <p><b>Untestable branches:</b> none.
 */
@QuarkusTest
class PushSubscriptionServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   USER_ID      = 1L;
    private static final String ENDPOINT     = "https://push.example.com/sub/abc";
    private static final String P256DH_KEY   = "p256dhKeyValue";
    private static final String AUTH_KEY     = "authKeyValue";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    PushSubscriptionService service;

    @InjectMock
    PushSubscriptionRepository repo;

    // ── Test-object builders ───────────────────────────────────────────────────
    private static PushSubscriptionDto buildDto() {
        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys(P256DH_KEY, AUTH_KEY);
        return new PushSubscriptionDto(ENDPOINT, keys);
    }

    private static PushSubscription buildSubscription() {
        PushSubscription sub = new PushSubscription();
        sub.setEndpoint(ENDPOINT);
        sub.setP256dh(P256DH_KEY);
        sub.setAuth(AUTH_KEY);
        return sub;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // saveSubscription
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class SaveSubscription {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true — existing subscription present → delete it first, then persist new one
        @Test
        void should_deleteExistingAndPersistNew_when_endpointAlreadyRegistered() {
            PushSubscription existing = buildSubscription();
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(existing);
            when(User.<User>findById(USER_ID)).thenReturn(new User());

            service.saveSubscription(USER_ID, buildDto());

            verify(repo).delete(existing);
            verify(repo).persist(any(PushSubscription.class));
        }

        // B1=false — no existing subscription → skip delete, just persist
        @Test
        void should_persistNew_when_endpointIsNotYetRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);
            when(User.<User>findById(USER_ID)).thenReturn(new User());

            service.saveSubscription(USER_ID, buildDto());

            verify(repo, never()).delete(any(PushSubscription.class));
            verify(repo).persist(any(PushSubscription.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteByEndpoint
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class DeleteByEndpoint {

        // B1=true — subscription found → deleted
        @Test
        void should_deleteSubscription_when_endpointIsKnown() {
            PushSubscription existing = buildSubscription();
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(existing);

            service.deleteByEndpoint(ENDPOINT);

            verify(repo).delete(existing);
        }

        // B1=false — subscription not found → no-op
        @Test
        void should_doNothing_when_endpointIsUnknown() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);

            service.deleteByEndpoint(ENDPOINT);

            verify(repo, never()).delete(any(PushSubscription.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isSubscriptionKnown
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class IsSubscriptionKnown {

        // B1=true — subscription found → returns true
        @Test
        void should_returnTrue_when_endpointIsRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(buildSubscription());

            boolean result = service.isSubscriptionKnown(ENDPOINT);

            assertThat(result).isTrue();
        }

        // B1=false — subscription not found → returns false
        @Test
        void should_returnFalse_when_endpointIsNotRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);

            boolean result = service.isSubscriptionKnown(ENDPOINT);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllForUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    class GetAllForUser {

        @Test
        void should_delegateToRepository_when_called() {
            List<PushSubscription> expected = List.of(buildSubscription());
            when(repo.findByUserId(USER_ID)).thenReturn(expected);

            List<PushSubscription> result = service.getAllForUser(USER_ID);

            assertThat(result).isSameAs(expected);
            verify(repo, times(1)).findByUserId(USER_ID);
            verifyNoMoreInteractions(repo);
        }
    }
}
