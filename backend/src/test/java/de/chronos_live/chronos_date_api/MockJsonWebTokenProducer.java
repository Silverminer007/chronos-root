package de.chronos_live.chronos_date_api;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.mockito.Mockito;

/**
 * Provides a test-only CDI bean for {@link JsonWebToken}.
 *
 * <p>When {@code quarkus.oidc.enabled=false} is set in test properties, the OIDC
 * extension does not register its {@code JsonWebToken} producer.  Any resource bean
 * that injects {@code JsonWebToken} therefore causes an
 * {@code UnsatisfiedResolutionException} at CDI deployment time.
 *
 * <p>{@code @Mock} marks this class as {@code @Alternative @Priority(1)}, so it is
 * active only during {@code @QuarkusTest} runs and takes precedence over any
 * production producer if OIDC is ever re-enabled in tests.
 */
@Mock
public class MockJsonWebTokenProducer {

    @Produces
    @RequestScoped
    public JsonWebToken produceJsonWebToken() {
        return Mockito.mock(JsonWebToken.class);
    }
}
