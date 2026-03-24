package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.FriendshipAcceptedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipDeclinedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRemovedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRequestSentEvent;
import de.chronos_live.chronos_date_api.domain.FriendshipRequest;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.FriendDto;
import de.chronos_live.chronos_date_api.dto.FriendshipRequestDto;
import de.chronos_live.chronos_date_api.dto.UserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.exception.ForbiddenException;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FriendshipService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all CDI
 * dependencies with Mockito mocks. {@link PanacheMock} intercepts static
 * Panache calls on {@link User} so no real database is touched.
 *
 * <p><b>Untestable branch:</b><br>
 * {@code FriendshipService#removeFriendship} lines 234-237:
 * The guard {@code if (!friendship.getRequesterId().equals(userId1) && !friendship.getAddresseeId().equals(userId1))}
 * can never evaluate to {@code true} in practice. The only way to reach it is
 * after {@code findFriendship(userId1, userId2)} already returned a record
 * whose {@code requesterId} or {@code addresseeId} equals {@code userId1} (by
 * definition of the query). The dead branch is unreachable without a corrupt
 * database. It is noted here but not tested.
 */
@QuarkusTest
class FriendshipServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long REQUESTER_ID  = 1L;
    private static final Long ADDRESSEE_ID  = 2L;
    private static final Long REQUEST_ID    = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final String EMAIL        = "bob@example.com";

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    FriendshipService service;

    @InjectMock
    FriendshipRepository friendshipRepo;

    @InjectMock
    Event<FriendshipRequestSentEvent> friendshipRequestEvent;

    @InjectMock
    Event<FriendshipAcceptedEvent> friendshipAcceptedEvent;

    @InjectMock
    Event<FriendshipDeclinedEvent> friendshipDeclinedEvent;

    @InjectMock
    Event<FriendshipRemovedEvent> friendshipRemovedEvent;

    // ── Test-object builders ──────────────────────────────────────────────────
    private static User buildUser(Long id, String firstName, String lastName) {
        User u = new User();
        u.id = id;
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(EMAIL);
        u.setProfilePictureUrl(null);
        return u;
    }

    private static FriendshipRequest buildRequest(Long id, Long requesterId, Long addresseeId,
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
    // sendFriendshipRequest(Long requesterId, Long addresseeId, String email)
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

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1
        @Test
        void should_throwBadRequestException_when_bothAddresseeIdAndEmailAreNull() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("addressee_id");
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_emailGivenButNoUserFound() {
            @SuppressWarnings("unchecked") PanacheQuery<User> q = mock(PanacheQuery.class);
            when(User.<User>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, null, EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(EMAIL);
        }

        // B3 – email given, user found → two-arg overload is called
        // The two-arg overload will call User.findByIdOptional; stub it to avoid a secondary exception.
        @Test
        void should_resolveAddresseeByEmail_when_addresseeIdNullAndEmailFound() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");

            @SuppressWarnings("unchecked") PanacheQuery<User> findByEmailQuery = mock(PanacheQuery.class);
            when(User.<User>find(anyString(), any(Object[].class))).thenReturn(findByEmailQuery);
            when(findByEmailQuery.firstResultOptional()).thenReturn(Optional.of(addressee));

            // Stub User.findByIdOptional used inside two-arg overload for addressee existence check
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));

            // No existing request
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.empty());

            // Stub User.findById used to load requester for the event
            User requester = buildUser(REQUESTER_ID, "Alice", "Jones");
            when(User.<User>findById(REQUESTER_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_ID, null, EMAIL);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }

        // B4 – addresseeId given directly
        @Test
        void should_delegateDirectly_when_addresseeIdIsProvided() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.empty());
            User requester = buildUser(REQUESTER_ID, "Alice", "Jones");
            when(User.<User>findById(REQUESTER_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID, null);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendFriendshipRequest(Long requesterId, Long addresseeId)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – sendFriendshipRequest(requesterId, addresseeId):
     *   B1  requesterId.equals(addresseeId) → throw BadRequestException
     *   B2  addressee not found → throw ResourceNotFoundException
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

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1
        @Test
        void should_throwBadRequestException_when_requesterEqualsAddressee() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, REQUESTER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("selbst");
        }

        // B2
        @Test
        void should_throwResourceNotFoundException_when_addresseeDoesNotExist() {
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B3
        @Test
        void should_createRequestAndFireEvent_when_noExistingRequest() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.empty());
            User requester = buildUser(REQUESTER_ID, "Alice", "Jones");
            when(User.<User>findById(REQUESTER_ID)).thenReturn(requester);

            ArgumentCaptor<FriendshipRequestSentEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRequestSentEvent.class);

            service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(captor.capture());
            assertThat(captor.getValue().requesterId()).isEqualTo(REQUESTER_ID);
            assertThat(captor.getValue().addresseeId()).isEqualTo(ADDRESSEE_ID);
            assertThat(captor.getValue().requesterName()).isEqualTo("Alice Jones");
        }

        // B4
        @Test
        void should_throwValidationException_when_alreadyFriends() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            FriendshipRequest accepted = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(accepted));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("befreundet");
        }

        // B5 – existing PENDING, requester is the one who already sent
        @Test
        void should_throwValidationException_when_pendingRequestAlreadySentByRequester() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            FriendshipRequest pending = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits eine Freundschaftsanfrage gesendet");
        }

        // B6 – existing PENDING, addressee is the original requester (reverse direction)
        @Test
        void should_throwValidationException_when_pendingRequestSentByAddressee() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            // Swap: addressee sent the request, requester is addressee in this record
            FriendshipRequest pending = buildRequest(REQUEST_ID, ADDRESSEE_ID, REQUESTER_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("hat dir bereits eine Freundschaftsanfrage gesendet");
        }

        // B7 – existing DECLINED → delete old, create new
        @Test
        void should_deleteOldDeclinedRequestAndCreateNew_when_previouslyDeclined() {
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIdOptional(ADDRESSEE_ID)).thenReturn(Optional.of(addressee));
            FriendshipRequest declined = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.DECLINED);
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(declined));
            User requester = buildUser(REQUESTER_ID, "Alice", "Jones");
            when(User.<User>findById(REQUESTER_ID)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_ID, ADDRESSEE_ID);

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

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_acceptingUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, OTHER_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_setAcceptedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipAcceptedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipAcceptedEvent.class);

            service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_ID);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipAcceptedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterId()).isEqualTo(REQUESTER_ID);
            assertThat(captor.getValue().addresseeId()).isEqualTo(ADDRESSEE_ID);
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

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_decliningUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, OTHER_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.DECLINED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_setDeclinedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipDeclinedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipDeclinedEvent.class);

            service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_ID);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipDeclinedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterId()).isEqualTo(REQUESTER_ID);
            assertThat(captor.getValue().addresseeId()).isEqualTo(ADDRESSEE_ID);
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

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // B2
        @Test
        void should_throwForbiddenException_when_cancellingUserIsNotRequester() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, OTHER_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("zurückziehen");
        }

        // B3
        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        // B4
        @Test
        void should_deleteRequest_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_ID);

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
     * Note: The guard at lines 234-237 is dead code (see class-level Javadoc).
     *
     * Total testable branches: 2  |  Tests: 2
     */
    @Nested
    class RemoveFriendship {

        // B1
        @Test
        void should_throwResourceNotFoundException_when_friendshipNotFound() {
            when(friendshipRepo.findFriendship(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeFriendship(REQUESTER_ID, ADDRESSEE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("nicht befreundet");
        }

        // B2
        @Test
        void should_deleteFriendshipAndFireEvent_when_friendshipExists() {
            FriendshipRequest friendship = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findFriendship(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(friendship));
            ArgumentCaptor<FriendshipRemovedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRemovedEvent.class);

            service.removeFriendship(REQUESTER_ID, ADDRESSEE_ID);

            verify(friendshipRepo).delete(friendship);
            verify(friendshipRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserId()).isEqualTo(REQUESTER_ID);
            assertThat(captor.getValue().friendId()).isEqualTo(ADDRESSEE_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getFriends:
     *   B1  friendIds.isEmpty() → true (return empty list) / false (load users + build DTOs)
     *   B2  friend == null inside mapping stream → skipped via filter(Objects::nonNull)
     *   B3  requesterId.equals(userId) (ternary in friendIds extraction) → true / false
     *
     * Total branches: 4  |  Tests: 3
     */
    @Nested
    class GetFriends {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEmptyList_when_noFriendships() {
            when(friendshipRepo.getFriendships(REQUESTER_ID)).thenReturn(List.of());

            List<FriendDto> result = service.getFriends(REQUESTER_ID);

            assertThat(result).isEmpty();
        }

        // B1=false, B3=false (userId is addressee in stored record)
        @Test
        void should_returnFriendDtos_when_friendshipsExist() {
            // REQUESTER_ID is the addressee, so friend ID is the requesterId
            FriendshipRequest fs = buildRequest(REQUEST_ID, ADDRESSEE_ID, REQUESTER_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_ID)).thenReturn(List.of(fs));

            User friend = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIds(List.of(ADDRESSEE_ID))).thenReturn(List.of(friend));

            List<FriendDto> result = service.getFriends(REQUESTER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser_id()).isEqualTo(ADDRESSEE_ID);
            assertThat(result.get(0).getName()).isEqualTo("Bob Smith");
        }

        // B2 – user data missing in map (null friend filtered out)
        @Test
        void should_filterOutFriend_when_userDataIsMissing() {
            FriendshipRequest fs = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_ID)).thenReturn(List.of(fs));

            // Return empty list → friend will be null in the map
            when(User.findByIds(anyList())).thenReturn(List.of());

            List<FriendDto> result = service.getFriends(REQUESTER_ID);

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

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEmptyList_when_noIncomingRequests() {
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_ID)).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_ID);

            assertThat(result).isEmpty();
        }

        // B1=false, B3=not-null
        @Test
        void should_returnDtos_when_incomingRequestsExist() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_ID)).thenReturn(List.of(request));
            User requester = buildUser(REQUESTER_ID, "Alice", "Jones");
            when(User.findByIds(List.of(REQUESTER_ID))).thenReturn(List.of(requester));

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_ID);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.getUserId()).isEqualTo(REQUESTER_ID);
            assertThat(dto.isIncoming()).isTrue();
            assertThat(dto.getRespondedAt()).isNotNull();
        }

        // B2 – user data missing → filtered out
        @Test
        void should_filterOutRequest_when_requesterUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_ID)).thenReturn(List.of(request));
            when(User.findByIds(anyList())).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_ID);

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

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEmptyList_when_noOutgoingRequests() {
            when(friendshipRepo.getOutgoingRequests(REQUESTER_ID)).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_ID);

            assertThat(result).isEmpty();
        }

        // B1=false, respondedAt=null (B3=true)
        @Test
        void should_returnDtos_when_outgoingRequestsExist_withNullRespondedAt() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            request.setRespondedAt(null);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_ID)).thenReturn(List.of(request));
            User addressee = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(User.findByIds(List.of(ADDRESSEE_ID))).thenReturn(List.of(addressee));

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_ID);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.isIncoming()).isFalse();
            assertThat(dto.getRespondedAt()).isNull();
        }

        // B2 – outgoing with user data missing
        @Test
        void should_filterOutRequest_when_addresseeUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_ID)).thenReturn(List.of(request));
            when(User.findByIds(anyList())).thenReturn(List.of());

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_ID);

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
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_ID, ADDRESSEE_ID, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.of(request));

            FriendshipStatus result = service.getFriendshipStatus(REQUESTER_ID, ADDRESSEE_ID);

            assertThat(result).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        void should_returnNull_when_noRequestExists() {
            when(friendshipRepo.findRequest(REQUESTER_ID, ADDRESSEE_ID)).thenReturn(Optional.empty());

            FriendshipStatus result = service.getFriendshipStatus(REQUESTER_ID, ADDRESSEE_ID);

            assertThat(result).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findNonFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findNonFriends:
     *   No conditional branches. Pure delegation to repository + DTO mapping.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindNonFriends {

        @Test
        void should_delegateToRepositoryAndMapToDto_when_called() {
            User u = buildUser(ADDRESSEE_ID, "Bob", "Smith");
            when(friendshipRepo.findNonFriends("bob", REQUESTER_ID, 5)).thenReturn(List.of(u));

            List<UserDto> result = service.findNonFriends("bob", REQUESTER_ID, 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_ID);
            assertThat(result.get(0).first_name()).isEqualTo("Bob");
            assertThat(result.get(0).last_name()).isEqualTo("Smith");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findRecentNonFriends
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findRecentNonFriends:
     *   No conditional branches. Pure delegation to repository + DTO mapping.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class FindRecentNonFriends {

        @Test
        void should_delegateToRepositoryAndMapToDto_when_called() {
            User u = buildUser(ADDRESSEE_ID, "Carol", "Doe");
            when(friendshipRepo.findRecentNonFriends(REQUESTER_ID, 3)).thenReturn(List.of(u));

            List<UserDto> result = service.findRecentNonFriends(REQUESTER_ID, 3);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_ID);
            assertThat(result.get(0).first_name()).isEqualTo("Carol");
        }
    }
}
