package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

/**
 * Base class for integration tests that need to access REST endpoints with authentication.
 *
 * <p>Provides:
 * - RestAssured configuration for HTTP requests
 * - Test user setup and OIDC ID injection
 * - JWT mock configuration for each test
 * - Common assertion helpers
 */
@QuarkusTest
public abstract class BaseIntegrationTest {

    protected static final String TEST_USER_OIDC = "test-user-oidc-123";
    protected static final String TEST_USER_OIDC_2 = "test-user-oidc-456";
    protected static final String ADMIN_USER_OIDC = "admin-oidc-789";

    @Inject
    IdentityPort identityPort;

    @InjectMock
    JsonWebToken jwt;

    @BeforeEach
    void setUp() {
        RestAssured.basePath = "";
        RestAssured.port = 8081;
        createTestUser(TEST_USER_OIDC, "Test User");
        createTestUser(TEST_USER_OIDC_2, "Test User 2");
        createTestUser(ADMIN_USER_OIDC, "Admin User");
    }

    protected void createTestUser(String oidcId, String name) {
        identityPort.upsert(new UserIdentity(oidcId, name, null, oidcId + "@test.local", null));
    }

    protected void mockJwtForUser(String oidcId) {
        Mockito.reset(jwt);
        when(jwt.getSubject()).thenReturn(oidcId);
        when(jwt.getClaim("given_name")).thenReturn("Test");
        when(jwt.getClaim("family_name")).thenReturn("User");
        when(jwt.getClaim("email")).thenReturn(oidcId + "@test.local");
        when(jwt.getClaim("picture")).thenReturn(null);
    }

    protected io.restassured.response.Response createAppointmentWithAuth(Object body, String oidcId) {
        mockJwtForUser(oidcId);
        return RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v2/appointments/")
                .then()
                .extract()
                .response();
    }

    protected io.restassured.response.Response getAppointmentWithAuth(Long appointmentId, String oidcId) {
        mockJwtForUser(oidcId);
        return RestAssured
                .given()
                .when()
                .get("/api/v2/appointments/" + appointmentId)
                .then()
                .extract()
                .response();
    }
}
