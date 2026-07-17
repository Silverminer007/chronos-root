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
import java.util.Collection;
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
 * <p>User IDs are now OIDC strings. Identity lookups go through {@link IdentityPort}
 * (mocked). The deleted {@code User} entity is no longer referenced.
 */
@QuarkusTest
class FriendshipServiceTest {

    private static final String REQUESTER_OIDC = "oidc-requester";
    private static final String ADDRESSEE_OIDC = "oidc-addressee";
    private static final Long   REQUEST_ID     = 10L;
    private static final String OTHER_OIDC     = "oidc-other-user";
    private static final String EMAIL          = "bob@example.com";

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

    @Nested
    class SendFriendshipRequestThreeArg {

        @Test
        void should_throwBadRequestException_when_bothAddresseeIdAndEmailAreNull() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("addressee_id");
        }

        @Test
        void should_throwResourceNotFoundException_when_emailGivenButNoUserFound() {
            when(identityPort.search(EMAIL, 1)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, null, EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(EMAIL);
        }

        @Test
        void should_resolveAddresseeByEmail_when_addresseeIdNullAndEmailFound() {
            UserIdentity addressee = buildUserIdentity(ADDRESSEE_OIDC, "Bob", "Smith");
            when(identityPort.search(EMAIL, 1)).thenReturn(List.of(addressee));
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.empty());
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC, null, EMAIL);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }

        @Test
        void should_delegateDirectly_when_addresseeIdIsProvided() {
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.empty());
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC, null);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }
    }

    @Nested
    class SendFriendshipRequestTwoArg {

        @Test
        void should_throwBadRequestException_when_requesterEqualsAddressee() {
            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, REQUESTER_OIDC))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("selbst");
        }

        @Test
        void should_createRequestAndFireEvent_when_noExistingRequest() {
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.empty());
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC)).thenReturn(requester);

            ArgumentCaptor<FriendshipRequestSentEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRequestSentEvent.class);

            service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC);

            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(captor.capture());
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC);
            assertThat(captor.getValue().requesterName()).isEqualTo("Alice Jones");
        }

        @Test
        void should_throwValidationException_when_alreadyFriends() {
            FriendshipRequest accepted = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(accepted));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("befreundet");
        }

        @Test
        void should_throwValidationException_when_pendingRequestAlreadySentByRequester() {
            FriendshipRequest pending = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits eine Freundschaftsanfrage gesendet");
        }

        @Test
        void should_throwValidationException_when_pendingRequestSentByAddressee() {
            FriendshipRequest pending = buildRequest(REQUEST_ID, ADDRESSEE_OIDC, REQUESTER_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("hat dir bereits eine Freundschaftsanfrage gesendet");
        }

        @Test
        void should_deleteOldDeclinedRequestAndCreateNew_when_previouslyDeclined() {
            FriendshipRequest declined = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.DECLINED);
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(declined));
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC, "Alice", "Jones");
            when(identityPort.findById(REQUESTER_OIDC)).thenReturn(requester);

            service.sendFriendshipRequest(REQUESTER_OIDC, ADDRESSEE_OIDC);

            verify(friendshipRepo).delete(declined);
            verify(friendshipRepo).persist(any(FriendshipRequest.class));
            verify(friendshipRequestEvent).fire(any(FriendshipRequestSentEvent.class));
        }
    }

    @Nested
    class AcceptFriendshipRequest {

        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void should_throwForbiddenException_when_acceptingUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, OTHER_OIDC))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        @Test
        void should_setAcceptedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipAcceptedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipAcceptedEvent.class);

            service.acceptFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipAcceptedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC);
        }
    }

    @Nested
    class DeclineFriendshipRequest {

        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void should_throwForbiddenException_when_decliningUserIsNotAddressee() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, OTHER_OIDC))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("eigenen");
        }

        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.DECLINED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        @Test
        void should_setDeclinedAndFireEvent_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));
            ArgumentCaptor<FriendshipDeclinedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipDeclinedEvent.class);

            service.declineFriendshipRequest(REQUEST_ID, ADDRESSEE_OIDC);

            assertThat(request.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(request.getRespondedAt()).isNotNull();
            verify(friendshipDeclinedEvent).fire(captor.capture());
            assertThat(captor.getValue().requestId()).isEqualTo(REQUEST_ID);
            assertThat(captor.getValue().requesterOidcId()).isEqualTo(REQUESTER_OIDC);
            assertThat(captor.getValue().addresseeOidcId()).isEqualTo(ADDRESSEE_OIDC);
        }
    }

    @Nested
    class CancelFriendshipRequest {

        @Test
        void should_throwResourceNotFoundException_when_requestNotFound() {
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void should_throwForbiddenException_when_cancellingUserIsNotRequester() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, OTHER_OIDC))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("zurückziehen");
        }

        @Test
        void should_throwValidationException_when_requestNotPending() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bereits bearbeitet");
        }

        @Test
        void should_deleteRequest_when_valid() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findByIdOptional(REQUEST_ID)).thenReturn(Optional.of(request));

            service.cancelFriendshipRequest(REQUEST_ID, REQUESTER_OIDC);

            verify(friendshipRepo).delete(request);
        }
    }

    @Nested
    class RemoveFriendship {

        @Test
        void should_throwResourceNotFoundException_when_friendshipNotFound() {
            when(friendshipRepo.findFriendship(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeFriendship(REQUESTER_OIDC, ADDRESSEE_OIDC))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("nicht befreundet");
        }

        @Test
        void should_deleteFriendshipAndFireEvent_when_friendshipExists() {
            FriendshipRequest friendship = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findFriendship(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(friendship));
            ArgumentCaptor<FriendshipRemovedEvent> captor =
                    ArgumentCaptor.forClass(FriendshipRemovedEvent.class);

            service.removeFriendship(REQUESTER_OIDC, ADDRESSEE_OIDC);

            verify(friendshipRepo).delete(friendship);
            verify(friendshipRemovedEvent).fire(captor.capture());
            assertThat(captor.getValue().actingUserOidcId()).isEqualTo(REQUESTER_OIDC);
            assertThat(captor.getValue().friendOidcId()).isEqualTo(ADDRESSEE_OIDC);
        }
    }

    @Nested
    class GetFriends {

        @Test
        void should_returnEmptyList_when_noFriendships() {
            when(friendshipRepo.getFriendships(REQUESTER_OIDC)).thenReturn(List.of());

            assertThat(service.getFriends(REQUESTER_OIDC)).isEmpty();
        }

        @Test
        void should_returnFriendDtos_when_friendshipsExist() {
            FriendshipRequest fs = buildRequest(REQUEST_ID, ADDRESSEE_OIDC, REQUESTER_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_OIDC)).thenReturn(List.of(fs));

            UserIdentity friend = buildUserIdentity(ADDRESSEE_OIDC, "Bob", "Smith");
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of(ADDRESSEE_OIDC, friend));

            List<FriendDto> result = service.getFriends(REQUESTER_OIDC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser_id()).isEqualTo(ADDRESSEE_OIDC);
            assertThat(result.get(0).getName()).isEqualTo("Bob Smith");
        }

        @Test
        void should_filterOutFriend_when_userDataIsMissing() {
            FriendshipRequest fs = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.getFriendships(REQUESTER_OIDC)).thenReturn(List.of(fs));
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of());

            assertThat(service.getFriends(REQUESTER_OIDC)).isEmpty();
        }
    }

    @Nested
    class GetIncomingRequests {

        @Test
        void should_returnEmptyList_when_noIncomingRequests() {
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC)).thenReturn(List.of());

            assertThat(service.getIncomingRequests(ADDRESSEE_OIDC)).isEmpty();
        }

        @Test
        void should_returnDtos_when_incomingRequestsExist() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC)).thenReturn(List.of(request));
            UserIdentity requester = buildUserIdentity(REQUESTER_OIDC, "Alice", "Jones");
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of(REQUESTER_OIDC, requester));

            List<FriendshipRequestDto> result = service.getIncomingRequests(ADDRESSEE_OIDC);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.getUserId()).isEqualTo(REQUESTER_OIDC);
            assertThat(dto.isIncoming()).isTrue();
            assertThat(dto.getRespondedAt()).isNotNull();
        }

        @Test
        void should_filterOutRequest_when_requesterUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.getIncomingRequests(ADDRESSEE_OIDC)).thenReturn(List.of(request));
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of());

            assertThat(service.getIncomingRequests(ADDRESSEE_OIDC)).isEmpty();
        }
    }

    @Nested
    class GetOutgoingRequests {

        @Test
        void should_returnEmptyList_when_noOutgoingRequests() {
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC)).thenReturn(List.of());

            assertThat(service.getOutgoingRequests(REQUESTER_OIDC)).isEmpty();
        }

        @Test
        void should_returnDtos_when_outgoingRequestsExist_withNullRespondedAt() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            request.setRespondedAt(null);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC)).thenReturn(List.of(request));
            UserIdentity addressee = buildUserIdentity(ADDRESSEE_OIDC, "Bob", "Smith");
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of(ADDRESSEE_OIDC, addressee));

            List<FriendshipRequestDto> result = service.getOutgoingRequests(REQUESTER_OIDC);

            assertThat(result).hasSize(1);
            FriendshipRequestDto dto = result.get(0);
            assertThat(dto.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(dto.isIncoming()).isFalse();
            assertThat(dto.getRespondedAt()).isNull();
        }

        @Test
        void should_filterOutRequest_when_addresseeUserDataIsMissing() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.getOutgoingRequests(REQUESTER_OIDC)).thenReturn(List.of(request));
            when(identityPort.findByIds(any(Collection.class))).thenReturn(Map.of());

            assertThat(service.getOutgoingRequests(REQUESTER_OIDC)).isEmpty();
        }
    }

    @Nested
    class GetFriendshipStatus {

        @Test
        void should_returnStatus_when_requestExists() {
            FriendshipRequest request = buildRequest(REQUEST_ID, REQUESTER_OIDC, ADDRESSEE_OIDC, FriendshipStatus.PENDING);
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.of(request));

            assertThat(service.getFriendshipStatus(REQUESTER_OIDC, ADDRESSEE_OIDC)).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        void should_returnNull_when_noRequestExists() {
            when(friendshipRepo.findRequest(REQUESTER_OIDC, ADDRESSEE_OIDC)).thenReturn(Optional.empty());

            assertThat(service.getFriendshipStatus(REQUESTER_OIDC, ADDRESSEE_OIDC)).isNull();
        }
    }

    @Nested
    class FindNonFriends {

        @Test
        void should_returnFilteredUsers_when_called() {
            UserIdentity u = buildUserIdentity(ADDRESSEE_OIDC, "Bob", "Smith");
            when(friendshipRepo.getFriendOidcIds(REQUESTER_OIDC)).thenReturn(Set.of());
            when(identityPort.search("bob", 15)).thenReturn(List.of(u));

            List<UserDto> result = service.findNonFriends("bob", REQUESTER_OIDC, 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_OIDC);
            assertThat(result.get(0).first_name()).isEqualTo("Bob");
            assertThat(result.get(0).last_name()).isEqualTo("Smith");
        }
    }

    @Nested
    class FindRecentNonFriends {

        @Test
        void should_returnFilteredUsers_when_called() {
            UserIdentity u = buildUserIdentity(ADDRESSEE_OIDC, "Carol", "Doe");
            when(friendshipRepo.getFriendOidcIds(REQUESTER_OIDC)).thenReturn(Set.of());
            when(identityPort.search("", 9)).thenReturn(List.of(u));

            List<UserDto> result = service.findRecentNonFriends(REQUESTER_OIDC, 3);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(ADDRESSEE_OIDC);
            assertThat(result.get(0).first_name()).isEqualTo("Carol");
        }
    }
}
