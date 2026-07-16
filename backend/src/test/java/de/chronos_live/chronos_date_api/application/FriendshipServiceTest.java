package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.FriendshipAcceptedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipDeclinedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRemovedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRequestSentEvent;
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.FriendDto;
import de.chronos_live.chronos_date_api.dto.FriendshipRequestDto;
import de.chronos_live.chronos_date_api.dto.UserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FriendshipService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all CDI
 * dependencies with Mockito mocks. All user lookups are now routed through
 * {@link IdentityPort}; user IDs are OIDC strings.
 *
 * <p><b>Untestable branch:</b><br>
 * {@code FriendshipService#removeFriendship}: The guard checking membership
 * can never evaluate to {@code true} in practice — the only way to reach it
 * is after {@code findFriendship(oidcId1, oidcId2)} returned a record whose
 * requesterId or addresseeId equals oidcId1 (by definition of the query).
 * The dead branch is noted here but not tested.
 */
@QuarkusTest
class FriendshipServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String REQUESTER_OIDC_ID  = "oidc-requester-1";
    private static final String ADDRESSEE_OIDC_ID  = "oidc-addressee-2";
    private static final Long   REQUEST_ID         = 10L;
    private static final String OTHER_OIDC_ID      = "oidc-other-99";
    private static final String EMAIL              = "bob@example.com";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    FriendshipService service;

    @InjectMock
    FriendshipRepository friendshipRepo;

    @InjectMock
    IdentityPort identityPort;

    @InjectMock
    Event<FriendshipRequestSentEvent> friendshipRequestEvent;

    @InjectMock
    Event<FriendshipAcceptedEvent> friendshipAcceptedEvent;

    @InjectMock
    Event<FriendshipDeclinedEvent> friendshipDeclinedEvent;

    @InjectMock
    Event<FriendshipRemovedEvent> friendshipRemovedEvent;

    // ── Test-object builders ──────────────────────────────────────────────────
    private static UserIdentity buildUserIdentity(String oidcId, String firstName, String lastName) {
        return new UserIdentity(oidcId, firstName, lastName, EMAIL, null);
    }

    private static FriendshipRequest buildRequest(Long id, String requesterId, String addresseeId,
                                                   FriendshipStatus status) {
        FriendshipRequest r = new FriendshipRequest();
        r.id = id;
        r.setRequesterId(requesterId);
        r.setAddresseeId(addresseeId);
        r.setStatus(status);
        r.setCreatedAt(Instant.parse("2024-01-01T10:00:00Z"));
        r.setRespondedAt(Instant.parse("2024-01-02T10:00:00Z"));
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendFriendshipRequest(String requesterId, String addresseeId, String email)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendFriendshipRequest(requesterId, addresseeId, email):
     *   B1  addresseeId == null AND email == null → throw BadRequestException
     *   B2  addresseeId == null AND email != null AND user not found → throw ResourceNotFoundException
     *   B3  addresseeId == null AND email != null AND user found → delegate to two-arg overload
     *   B4  addresseeId != null → delegate directly to two-arg overload
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class SendFriendshipRequestThreeArg {

        // B1
        @Test
        void should_throwBadRequestException_when_bothAddresseeIdAndEmailAreNull() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("addressee_id");
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_emailGivenButNoUserFound() {
            when(identityPort.search(EMAIL, 1)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, null, EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(EMAIL);
        }

        // B3 – email given, user found → two-arg overload is called
        @Test
        void should_resolveAddresseeByEmail_when_addresseeIdNullAndEmailFound() {
            UserIdentity addressee = buildUserIdentity(ADDRESSEE_OIDC_ID, "Bob", "Smith");
            when(identityPort.search(EMAIL, 1)).thenReturn(List.of(addressee));

            // No existing request
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.empty());
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);

            // Stub identityPort.findById used to load requester for the event
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC_ID, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC_ID, null, EMAIL);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }

        // B4 – addresseeId given directly
        @Test
        void should_delegateDirectly_when_addresseeIdIsProvided() {
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.empty());
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC_ID, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, null);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendFriendshipRequest(String requesterId, String addresseeId)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendFriendshipRequest(requesterId, addresseeId):
     *   B1  requesterId.equals(addresseeId) → throw BadRequestException
     *   B2  addressee not found in identity provider → throw ResourceNotFoundException
     *   B3  existing request absent → create new request + fire event
     *   B4  existing ACCEPTED → throw ValidationException ("bereits befreundet")
     *   B5  existing PENDING, direction = requester sent → throw ValidationException ("bereits gesendet")
     *   B6  existing PENDING, direction = addressee sent → throw ValidationException ("hat dir bereits gesendet")
     *   B7  existing DECLINED → delete old request then create new one
     *
     * Total branches: 7  |  Tests: 7
     */
    @Nested
    class SendFriendshipRequestTwoArg {

        // B1
        @Test
        void should_throwBadRequestException_when_requesterEqualsAddressee() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, REQUESTER_OIDC_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("selbst");
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_addresseeDoesNotExist() {
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ADDRESSEE_OIDC_ID);
        }

        // B3
        @Test
        void should_createRequestAndFireEvent_when_noExistingRequest() {
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.empty());
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC_ID, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC_ID)).thenReturn(requester);

            ArgumentCaptor<FriendshipRequestSentEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRequestSentEvent.class);

            service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(captor.capture());
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC_ID);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC_ID);
            assertThat(captor.getValue().requesterName()).isEqualTo("Alice Jones");
        }

        // B3
        @Test
        void should_throwValidationException_when_alreadyFriends() {
            FriendshipRequest accepted = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(accepted));
            when(identityPort.existsById(REQUESTER_OIDC_ID)).thenReturn(true);
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("befreundet");
        }

        // B4 – existing PENDING, requester is the one who already sent
        @Test
        void should_throwValidationException_when_pendingRequestAlreadySentByRequester() {
            FriendshipRequest pending = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(pending));
            when(identityPort.existsById(REQUESTER_OIDC_ID)).thenReturn(true);
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits eine Freundschaftsanfrage gesendet");
        }

        // B5 – existing PENDING, addressee is the original requester (reverse direction)
        @Test
        void should_throwValidationException_when_pendingRequestSentByAddressee() {
            // Swap: addressee sent the request, requester is addressee in this record
            FriendshipRequest pending = buildRequest(REQUEST_ID, ADDRESSEE_OIDC_ID, REQUESTER_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(pending));
            when(identityPort.existsById(REQUESTER_OIDC_ID)).thenReturn(true);
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("hat dir bereits eine Freundschaftsanfrage gesendet");
        }

        // B7 – existing DECLINED → delete old, create new
        @Test
        void should_deleteOldDeclinedRequestAndCreateNew_when_previouslyDeclined() {
            when(identityPort.existsById(ADDRESSEE_OIDC_ID)).thenReturn(true);
            FriendshipRequest declined = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.DECLINED);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(declined));
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC_ID, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID);

            verify(friendshipRepo).delete(declined);
            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // acceptFriendshipRequest
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – acceptFriendshipRequest:
     *   B1  request not found → throw ResourceNotFoundException
     *   B2  acceptingUserId != addresseeId → throw ForbiddenException
     *   B3  status != PENDING → throw ValidationException
     *   B4  happy path → set ACCEPTED + fire event
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class AcceptFriendshipRequest {

        // B1
        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_acceptingUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, OTHER_OIDC_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_setAcceptedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipAcceptedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipAcceptedEvent.class);

            service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipAcceptedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC_ID);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // declineFriendshipRequest
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – declineFriendshipRequest:
     *   B1  request not found → throw ResourceNotFoundException
     *   B2  decliningUserId != addresseeId → throw ForbiddenException
     *   B3  status != PENDING → throw ValidationException
     *   B4  happy path → set DECLINED + fire event
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class DeclineFriendshipRequest {

        // B1
        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_decliningUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, OTHER_OIDC_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.DECLINED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_setDeclinedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipDeclinedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipDeclinedEvent.class);

            service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC_ID);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipDeclinedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC_ID);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // cancelFriendshipRequest
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – cancelFriendshipRequest:
     *   B1  request not found → throw ResourceNotFoundException
     *   B2  cancellingUserId != requesterId → throw ForbiddenException
     *   B3  status != PENDING → throw ValidationException
     *   B4  happy path → delete request
     *
     * Total branches: 4  |  Tests: 4
     */
    @Nested
    class CancelFriendshipRequest {

        // B1
        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_cancellingUserIsNotRequester() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, OTHER_OIDC_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("zurückziehen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_deleteRequest_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC_ID);

            verify(friendshipRepo).delete(request);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeFriendship
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – removeFriendship:
     *   B1  friendship not found → throw ResourceNotFoundException
     *   B2  happy path → delete + fire event
     *
     * Note: The guard checking party membership is dead code (see class-level Javadoc).
     *
     * Total testable branches: 2  |  Tests: 2
     */
    @Nested
    class RemoveFriendship {

        // B1
        @Test
        void should_throwResourceNotFoundException_when_friendshipNotFound() {
            when(friendshipRepo.findFriendship(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeFriendship(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("nicht befreundet");
        }

        // B2
        @Test
        void should_deleteFriendshipAndFireEvent_when_friendshipExists() {
            FriendshipRequest friendship = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findFriendship(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(friendship));
            ArgumentCaptor<FriendshipRemovedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRemovedEvent.class);

            service.removeFriendship(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID);

            verify(friendshipRepo).delete(friendship);
            verify(friendshipRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(REQUESTER_OIDC_ID);
            assertThat(captor.getValue().friendOidcId()).isEqualTo(ADDRESSEE_OIDC_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getFriends:
     *   B1  friendIds.isEmpty() → true (return empty list) / false (load users + build DTOs)
     *   B2  friend == null inside mapping stream → skipped via filter(Objects::nonNull)
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class GetFriends {

        // B1=true
        @Test
        void should_returnEmptyList_when_noFriendships() {
            when(friendshipRepo.getFriendships(REQUESTER_OIDC_ID)).thenReturn(List.of());

            List<FriendDto> result = service.getFriends(REQUESTER_OIDC_ID);

            assertThat(result).isEmpty();
        }

        // B1=false
        @Test
        void should_returnFriendDtos_when_friendshipsExist() {
            FriendshipRequest fs = buildRequest(REQUEST_ID, ADDRESSEE_OIDC_ID, REQUESTER_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_OIDC_ID)).thenReturn(List.of(fs));

            UserIdentity friend = buildUserIdentity(ADDRESSEE_OIDC_ID, "Bob", "Smith");
            when(identityPort.findByIds(anyList())).thenReturn(Map.of(ADDRESSEE_OIDC_ID, friend));

            List<FriendDto> result = service.getFriends(REQUESTER_OIDC_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser_id()).isEqualTo(ADDRESSEE_OIDC_ID);
            assertThat(result.get(0).getName()).isEqualTo("Bob Smith");
        }

        // B2 – user data missing in map (null friend filtered out)
        @Test
        void should_filterOutFriend_when_userDataIsMissing() {
            FriendshipRequest fs = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_OIDC_ID)).thenReturn(List.of(fs));

            // Return empty map → friend will be null
            when(identityPort.findByIds(anyList())).thenReturn(Map.of());

            List<FriendDto> result = service.getFriends(REQUESTER_OIDC_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getIncomingRequests
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getIncomingRequests:
     *   B1  requests.isEmpty() → true (return empty list) / false (build DTOs)
     *   B2  otherUser == null → skipped via filter
     *   B3  respondedAt == null → null string / not-null string
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class GetIncomingRequests {

        // B1=true
        @Test
        void should_returnEmptyList_when_noIncomingRequests() {
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC_ID)).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_OIDC_ID);

            assertThat(result).isEmpty();
        }

        // B1=false, B3=not-null
        @Test
        void should_returnDtos_when_incomingRequestsExist() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC_ID)).thenReturn(List.of(request));
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC_ID, "Alice", "Jones");
            when(identityPort.findByIds(anyList())).thenReturn(Map.of(REQUESTER_OIDC_ID, requester));

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_OIDC_ID);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.getUserId()).isEqualTo(REQUESTER_OIDC_ID);
            assertThat(dto.isIncoming()).isTrue();
            assertThat(dto.getRespondedAt()).isNotNull();
        }

        // B2 – user data missing → filtered out
        @Test
        void should_filterOutRequest_when_requesterUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC_ID)).thenReturn(List.of(request));
            when(identityPort.findByIds(anyList())).thenReturn(Map.of());

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_OIDC_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getOutgoingRequests
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getOutgoingRequests:
     *   B1  requests.isEmpty() → true / false (mirrors incoming)
     *   B2  respondedAt == null → null string
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class GetOutgoingRequests {

        // B1=true
        @Test
        void should_returnEmptyList_when_noOutgoingRequests() {
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC_ID)).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_OIDC_ID);

            assertThat(result).isEmpty();
        }

        // B1=false, respondedAt=null
        @Test
        void should_returnDtos_when_outgoingRequestsExist_withNullRespondedAt() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            request.setRespondedAt(null);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC_ID)).thenReturn(List.of(request));
            UserIdentity addressee = buildUserIdentity(ADDRESSEE_OIDC_ID, "Bob", "Smith");
            when(identityPort.findByIds(anyList())).thenReturn(Map.of(ADDRESSEE_OIDC_ID, addressee));

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_OIDC_ID);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.isIncoming()).isFalse();
            assertThat(dto.getRespondedAt()).isNull();
        }

        // B2 – outgoing with user data missing
        @Test
        void should_filterOutRequest_when_addresseeUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC_ID)).thenReturn(List.of(request));
            when(identityPort.findByIds(anyList())).thenReturn(Map.of());

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_OIDC_ID);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getFriendshipStatus
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getFriendshipStatus:
     *   B1  request found → return status
     *   B2  request not found → return null
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetFriendshipStatus {

        @Test
        void should_returnStatus_when_requestExists() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.of(request));

            FriendshipStatus result = service.getFriendshipStatus(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID);

            assertThat(result).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        void should_returnNull_when_noRequestExists() {
            when(friendshipRepo.findRequest(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID)).thenReturn(Optional.empty());

            FriendshipStatus result = service.getFriendshipStatus(REQUESTER_OIDC_ID, ADDRESSEE_OIDC_ID);

            assertThat(result).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findNonFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findNonFriends:
     *   No conditional branches beyond stream filtering.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindNonFriends {

        @Test
        void should_delegateToIdentityPortAndMapToDto_when_called() {
            UserIdentity u = buildUserIdentity(ADDRESSEE_OIDC_ID, "Bob", "Smith");
            when(friendshipRepo.getFriendOidcIds(REQUESTER_OIDC_ID)).thenReturn(Set.of());
            when(identityPort.search("bob", 15)).thenReturn(List.of(u));

            List<UserDto> result = service.findNonFriends("bob", REQUESTER_OIDC_ID, 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_OIDC_ID);
            assertThat(result.get(0).first_name()).isEqualTo("Bob");
            assertThat(result.get(0).last_name()).isEqualTo("Smith");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findRecentNonFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findRecentNonFriends:
     *   No conditional branches beyond stream filtering.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindRecentNonFriends {

        @Test
        void should_delegateToIdentityPortAndMapToDto_when_called() {
            UserIdentity u = buildUserIdentity(ADDRESSEE_OIDC_ID, "Carol", "Doe");
            when(friendshipRepo.getFriendOidcIds(REQUESTER_OIDC_ID)).thenReturn(Set.of());
            when(identityPort.search("", 9)).thenReturn(List.of(u));

            List<UserDto> result = service.findRecentNonFriends(REQUESTER_OIDC_ID, 3);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_OIDC_ID);
            assertThat(result.get(0).first_name()).isEqualTo("Carol");
        }
    }
}
