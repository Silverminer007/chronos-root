package de.chronos_live.chronos_date_api.presentation;

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
 * - JWT mock configuration for each test
 * - Common assertion helpers
 */
@QuarkusTest
public abstract class BaseIntegrationTest {

    protected static final String TEST_USER_OIDC = "test-user-oidc-123";
    protected static final String TEST_USER_OIDC_2 = "test-user-oidc-456";
    protected static final String ADMIN_USER_OIDC = "admin-oidc-789";

    @Inject
    JsonWebToken jwt;

    @BeforeEach
    void setUp() {
        RestAssured.basePath = "";
        RestAssured.port = 8081;
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
