package de.chronos_live.chronos_date_api.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityTest {

    // ══════════════════════════════════════════════════════════════════════════
    // deleted() factory
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – deleted():
     *   B1  oidcId is preserved
     *   B2  all content fields are null
     *
     * Total branches: 2  |  Tests: 1 (both covered in one)
     */
    @Nested
    class DeletedFactory {

        @Test
        void should_setOidcIdAndNullifyAllContentFields() {
            UserIdentity sentinel = UserIdentity.deleted("oidc-abc");

            assertThat(sentinel.oidcId()).isEqualTo("oidc-abc");
            assertThat(sentinel.firstName()).isNull();
            assertThat(sentinel.lastName()).isNull();
            assertThat(sentinel.email()).isNull();
            assertThat(sentinel.profilePictureUrl()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isDeleted()
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isDeleted():
     *   B1  all three content fields null → true
     *   B2  firstName present → false
     *   B3  lastName present → false
     *   B4  email present → false
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class IsDeleted {

        // B1
        @Test
        void should_returnTrue_forDeletedSentinel() {
            assertThat(UserIdentity.deleted("oidc-abc").isDeleted()).isTrue();
        }

        // B2
        @Test
        void should_returnFalse_whenFirstNameIsPresent() {
            assertThat(new UserIdentity("id", "Anna", null, null, null).isDeleted()).isFalse();
        }

        // B3
        @Test
        void should_returnFalse_whenLastNameIsPresent() {
            assertThat(new UserIdentity("id", null, "Müller", null, null).isDeleted()).isFalse();
        }

        // B4
        @Test
        void should_returnFalse_whenEmailIsPresent() {
            assertThat(new UserIdentity("id", null, null, "a@b.com", null).isDeleted()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getName()
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getName():
     *   B1  isDeleted() → "Gelöschter Benutzer"
     *   B2  both names present → "Vorname Nachname"
     *   B3  only firstName → firstName
     *   B4  only lastName → lastName
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class GetName {

        // B1
        @Test
        void should_returnGelöschterBenutzer_forDeletedSentinel() {
            assertThat(UserIdentity.deleted("oidc-abc").getName()).isEqualTo("Gelöschter Benutzer");
        }

        // B2
        @Test
        void should_returnFullName_whenBothNamesPresent() {
            assertThat(new UserIdentity("id", "Anna", "Müller", "a@b.com", null).getName())
                    .isEqualTo("Anna Müller");
        }

        // B3
        @Test
        void should_returnFirstNameOnly_whenLastNameIsNull() {
            assertThat(new UserIdentity("id", "Anna", null, null, null).getName()).isEqualTo("Anna");
        }

        // B4
        @Test
        void should_returnLastNameOnly_whenFirstNameIsNull() {
            assertThat(new UserIdentity("id", null, "Müller", null, null).getName()).isEqualTo("Müller");
        }
    }
}