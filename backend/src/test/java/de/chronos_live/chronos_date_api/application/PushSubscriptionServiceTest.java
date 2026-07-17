package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.PushSubscription;
import de.chronos_live.chronos_date_api.dto.PushSubscriptionDto;
import de.chronos_live.chronos_date_api.infrastructure.PushSubscriptionRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@QuarkusTest
class PushSubscriptionServiceTest {

    private static final String USER_OIDC  = "oidc-user-1";
    private static final String ENDPOINT   = "https://push.example.com/sub/abc";
    private static final String P256DH_KEY = "p256dhKeyValue";
    private static final String AUTH_KEY   = "authKeyValue";

    @Inject
    PushSubscriptionService service;

    @InjectMock
    PushSubscriptionRepository repo;

    private static PushSubscriptionDto buildDto() {
        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys(P256DH_KEY, AUTH_KEY);
        return new PushSubscriptionDto(ENDPOINT, keys);
    }

    private static PushSubscription buildSubscription() {
        PushSubscription sub = new PushSubscription();
        sub.setEndpoint(ENDPOINT);
        sub.setP256dh(P256DH_KEY);
        sub.setAuth(AUTH_KEY);
        sub.setUserOidcId(USER_OIDC);
        return sub;
    }

    @Nested
    class SaveSubscription {

        @Test
        void should_deleteExistingAndPersistNew_when_endpointAlreadyRegistered() {
            PushSubscription existing = buildSubscription();
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(existing);

            service.saveSubscription(USER_OIDC, buildDto());

            verify(repo).delete(existing);
            verify(repo).persist(any(PushSubscription.class));
        }

        @Test
        void should_persistNew_when_endpointIsNotYetRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);

            service.saveSubscription(USER_OIDC, buildDto());

            verify(repo, never()).delete(any(PushSubscription.class));
            verify(repo).persist(any(PushSubscription.class));
        }
    }

    @Nested
    class DeleteByEndpoint {

        @Test
        void should_deleteSubscription_when_endpointIsKnown() {
            PushSubscription existing = buildSubscription();
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(existing);

            service.deleteByEndpoint(ENDPOINT);

            verify(repo).delete(existing);
        }

        @Test
        void should_doNothing_when_endpointIsUnknown() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);

            service.deleteByEndpoint(ENDPOINT);

            verify(repo, never()).delete(any(PushSubscription.class));
        }
    }

    @Nested
    class IsSubscriptionKnown {

        @Test
        void should_returnTrue_when_endpointIsRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(buildSubscription());

            assertThat(service.isSubscriptionKnown(ENDPOINT)).isTrue();
        }

        @Test
        void should_returnFalse_when_endpointIsNotRegistered() {
            when(repo.findByEndpoint(ENDPOINT)).thenReturn(null);

            assertThat(service.isSubscriptionKnown(ENDPOINT)).isFalse();
        }
    }

    @Nested
    class GetAllForUser {

        @Test
        void should_delegateToRepository_when_called() {
            List<PushSubscription> expected = List.of(buildSubscription());
            when(repo.findByUserOidcId(USER_OIDC)).thenReturn(expected);

            List<PushSubscription> result = service.getAllForUser(USER_OIDC);

            assertThat(result).isSameAs(expected);
            verify(repo, times(1)).findByUserOidcId(USER_OIDC);
            verifyNoMoreInteractions(repo);
        }
    }
}
