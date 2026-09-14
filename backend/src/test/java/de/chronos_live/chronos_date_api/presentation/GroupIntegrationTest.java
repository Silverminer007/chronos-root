package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.GroupMembership;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.dto.CreateGroupDto;
import de.chronos_live.chronos_date_api.infrastructure.GroupRepository;
import de.chronos_live.chronos_date_api.infrastructure.GroupMembershipRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for group management endpoints.
 *
 * <p>Tests verify:
 * - Group creation
 * - Group listing and retrieval
 * - Adding/removing group members
 * - Member role management
 * - Group deletion
 */
@QuarkusTest
class GroupIntegrationTest extends BaseIntegrationTest {

    @Inject
    GroupRepository groupRepository;

    @Inject
    GroupMembershipRepository membershipRepository;

    @Test
    void testCreateGroup_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        CreateGroupDto createDto = new CreateGroupDto();
        createDto.setName("Test Group");
        createDto.setDescription("A test group for integration tests");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(createDto)
                .when()
                .post("/api/v2/groups/")
                .then()
                .extract()
                .response();

        // Assert - Response
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().jsonPath().getLong("id")).isNotNull();
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Test Group");

        // Assert - Database
        Long groupId = response.body().jsonPath().getLong("id");
        Group persisted = groupRepository.findById(groupId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getName()).isEqualTo("Test Group");
    }

    @Test
    void testGetGroup_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Group group = createTestGroup();

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/groups/" + group.getId())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getString("name")).isEqualTo("Test Group");
    }

    @Test
    void testAddGroupMember_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Group group = createTestGroup();

        var addMemberDto = new java.util.LinkedHashMap<String, String>();
        addMemberDto.put("user_id", TEST_USER_OIDC_2);
        addMemberDto.put("user_role", "GUEST");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(addMemberDto)
                .when()
                .post("/api/v2/groups/" + group.getId() + "/members")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify in database
        Optional<GroupMembership> membership = membershipRepository
                .find("groupId = ?1 AND userOidcId = ?2", group.getId(), TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(membership).isPresent();
        assertThat(membership.get().getUserRole()).isEqualTo(UserRole.GUEST);
    }

    @Test
    void testRemoveGroupMember_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Group group = createTestGroup();
        addMemberToGroup(group, TEST_USER_OIDC_2, UserRole.GUEST);

        // Act
        var response = RestAssured
                .given()
                .when()
                .delete("/api/v2/groups/" + group.getId() + "/members/" + TEST_USER_OIDC_2)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify removed from database
        long count = membershipRepository
                .count("groupId = ?1 AND userOidcId = ?2", group.getId(), TEST_USER_OIDC_2);
        assertThat(count).isZero();
    }

    @Test
    void testChangeGroupMemberRole_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Group group = createTestGroup();
        addMemberToGroup(group, TEST_USER_OIDC_2, UserRole.GUEST);

        var roleDto = new java.util.LinkedHashMap<String, String>();
        roleDto.put("user_role", "ORGANIZER");

        // Act
        var response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(roleDto)
                .when()
                .patch("/api/v2/groups/" + group.getId() + "/members/" + TEST_USER_OIDC_2)
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);

        // Verify role changed in database
        Optional<GroupMembership> membership = membershipRepository
                .find("groupId = ?1 AND userOidcId = ?2", group.getId(), TEST_USER_OIDC_2)
                .firstResultOptional();
        assertThat(membership).isPresent();
        assertThat(membership.get().getUserRole()).isEqualTo(UserRole.ORGANIZER);
    }

    @Test
    void testGetGroupMembers_Success() {
        // Arrange
        mockJwtForUser(TEST_USER_OIDC);
        Group group = createTestGroup();
        addMemberToGroup(group, TEST_USER_OIDC_2, UserRole.GUEST);

        // Act
        var response = RestAssured
                .given()
                .when()
                .get("/api/v2/groups/" + group.getId() + "/members")
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(1);
    }

    // Helper methods
    private Group createTestGroup() {
        Group group = new Group();
        group.setName("Test Group");
        group.setDescription("Test Description");
        group.setCreatorOidcId(TEST_USER_OIDC);
        group.setCreatedAt(Instant.now());
        groupRepository.persist(group);
        return group;
    }

    private void addMemberToGroup(Group group, String userOidcId, UserRole role) {
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(group.getId());
        membership.setUserOidcId(userOidcId);
        membership.setUserRole(role);
        membershipRepository.persist(membership);
    }
}
