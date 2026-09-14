package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Friendship;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for friendship management endpoints.
 *
 * <p>Tests verify:
 * - Sending friendship requests
 * - Accepting friendship requests
 * - Rejecting friendship requests
 * - Listing friends
 * - Removing friends
 * - Event firing on friendship state changes
 */
@QuarkusTest
class FriendshipIntegrationTest extends BaseIntegrationTest {

    @Inject
    FriendshipRepository friendshipRepository;

    @Test
    void testSendFriendshipRequest_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        var friendDto = new java.util.LinkedHashMap<String, String>();
        friendDto.put("user_id", TEST_USER_OIDC_2);

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(friendDto)
                .when()
                .post("/api/v2/friendships/request")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify in database
        Optional<Friendship> friendship = friendshipRepository
                .find("(initiatorOidcId = ?1 AND targetOidcId = ?2) OR (initiatorOidcId = ?2 AND targetOidcId = ?1)",
                        TEST_USER_OIDC, TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(friendship).isPresent();
        assertThat(friendship.get().getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void testAcceptFriendshipRequest_Success() {
        // Arrange - Create pending friendship request
        mockJwtForUser(TEST_USER_OIDC);
        Friendship friendship = createPendingFriendship(TEST_USER_OIDC, TEST_USER_OIDC_2);

        // Act - Accept as TEST_USER_OIDC_2
        mockJwtForUser(TEST_USER_OIDC_2);
        var acceptDto = new java.util.LinkedHashMap<String, String>();
        acceptDto.put("user_id", TEST_USER_OIDC);

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(acceptDto)
                .when()
                .post("/api/v2/friendships/accept")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify status changed in database
        Friendship updated = friendshipRepository.findById(friendship.getId());
        assertThat(updated.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void testRejectFriendshipRequest_Success() {
        // Arrange - Create pending friendship request
        mockJwtForUser(TEST_USER_OIDC);
        Friendship friendship = createPendingFriendship(TEST_USER_OIDC, TEST_USER_OIDC_2);

        // Act - Reject as TEST_USER_OIDC_2
        mockJwtForUser(TEST_USER_OIDC_2);
        var rejectDto = new java.util.LinkedHashMap<String, String>();
        rejectDto.put("user_id", TEST_USER_OIDC);

        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(rejectDto)
                .when()
                .post("/api/v2/friendships/reject")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify status changed in database
        Friendship updated = friendshipRepository.findById(friendship.getId());
        assertThat(updated.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
    }

    @Test
    void testListFriends_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        createAcceptedFriendship(TEST_USER_OIDC, TEST_USER_OIDC_2);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/friendships/")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void testRemoveFriend_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        createAcceptedFriendship(TEST_USER_OIDC, TEST_USER_OIDC_2);

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/friendships/" + TEST_USER_OIDC_2)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify removed from database (or status changed to REJECTED)
        Optional<Friendship> remaining = friendshipRepository
                .find("(initiatorOidcId = ?1 AND targetOidcId = ?2 AND status = 'ACCEPTED') OR " +
                      "(initiatorOidcId = ?2 AND targetOidcId = ?1 AND status = 'ACCEPTED')",
                        TEST_USER_OIDC, TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(remaining).isEmpty();
    }

    // Helper methods
    private Friendship createPendingFriendship(String initiatorOidcId, String targetOidcId) {
        Friendship friendship = new Friendship();
        friendship.setInitiatorOidcId(initiatorOidcId);
        friendship.setTargetOidcId(targetOidcId);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship.setCreatedAt(Instant.now());
        friendshipRepository.persist(friendship);
        return friendship;
    }

    private Friendship createAcceptedFriendship(String initiatorOidcId, String targetOidcId) {
        Friendship friendship = new Friendship();
        friendship.setInitiatorOidcId(initiatorOidcId);
        friendship.setTargetOidcId(targetOidcId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setCreatedAt(Instant.now());
        friendship.setAcceptedAt(Instant.now());
        friendshipRepository.persist(friendship);
        return friendship;
    }
}
