package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.FriendshipAcceptedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipDeclinedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRemovedEvent;
import de.chronos_live.chronos_date_api.application.events.FriendshipRequestSentEvent;
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
import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.infrastructure.FriendshipRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Timed("service.friendship")
public class FriendshipService {
    private static final Logger LOGGER = Logger.getLogger(FriendshipService.class);

    @Inject
    FriendshipRepository friendshipRepo;

    @Inject
    UserService userService;
    @Inject
    IdentityPort identityPort;

    @Inject
    Event<FriendshipRequestSentEvent> friendshipRequestEvent;

    @Inject
    Event<FriendshipAcceptedEvent> friendshipAcceptedEvent;

    @Inject
    Event<FriendshipDeclinedEvent> friendshipDeclinedEvent;

    @Inject
    Event<FriendshipRemovedEvent> friendshipRemovedEvent;

    /**
     * Sends a friendship request, resolving the addressee by email via Keycloak when needed.
     */
    public void sendFriendshipRequest(String requesterOidcId, String addresseeOidcId, String email) {
        if (addresseeOidcId == null) {
            if (email == null) {
                throw new BadRequestException("You must either set addressee_id or email");
            }
            // Resolve oidcId from email via Keycloak Admin API
            List<UserIdentity> found = userService.searchUsers(email, 1);
            if (found.isEmpty() || !email.equalsIgnoreCase(found.get(0).email())) {
                throw new ResourceNotFoundException("Es wurde kein User mit der E-Mail Adresse " + email + " gefunden");
            }
            addresseeOidcId = found.get(0).oidcId();
        }
        this.sendFriendshipRequest(requesterOidcId, addresseeOidcId);
    }

    @Transactional
    public void sendFriendshipRequest(String requesterOidcId, String addresseeOidcId) {
        LOGGER.debugf("[Principal %s][Addressee %s] Sending Friendship Request", requesterOidcId, addresseeOidcId);

        if (requesterOidcId.equals(addresseeOidcId)) {
            throw new BadRequestException("Du kannst dir nicht selbst eine Freundschaftsanfrage senden");
        }

        Optional<FriendshipRequest> existing = friendshipRepo.findRequest(requesterOidcId, addresseeOidcId);
        if (existing.isPresent()) {
            FriendshipRequest existingRequest = existing.get();
            if (existingRequest.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new ValidationException("Ihr seid bereits befreundet");
            }
            if (existingRequest.getStatus() == FriendshipStatus.PENDING) {
                if (existingRequest.getRequesterId().equals(requesterOidcId)) {
                    throw new ValidationException("Du hast bereits eine Freundschaftsanfrage gesendet");
                } else {
                    throw new ValidationException(
                            "Dieser User hat dir bereits eine Freundschaftsanfrage gesendet. Bitte nimm diese an.");
                }
            }
            if (existingRequest.getStatus() == FriendshipStatus.DECLINED) {
                friendshipRepo.delete(existingRequest);
            }
        }

        FriendshipRequest request = new FriendshipRequest();
        request.setRequesterId(requesterOidcId);
        request.setAddresseeId(addresseeOidcId);
        request.setStatus(FriendshipStatus.PENDING);
        request.setCreatedAt(Instant.now());
        friendshipRepo.persist(request);

        UserIdentity requester = identityPort.findById(requesterOidcId);
        friendshipRequestEvent.fire(new FriendshipRequestSentEvent(
                request.id,
                requesterOidcId,
                addresseeOidcId,
                requester.getName()
        ));
    }

    @Transactional
    public void acceptFriendshipRequest(Long requestId, String acceptingUserOidcId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Accepting", acceptingUserOidcId, requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        if (!request.getAddresseeId().equals(acceptingUserOidcId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen annehmen");
        }
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());

        friendshipAcceptedEvent.fire(new FriendshipAcceptedEvent(
                request.id, request.getRequesterId(), request.getAddresseeId()));
    }

    @Transactional
    public void declineFriendshipRequest(Long requestId, String decliningUserOidcId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Declining", decliningUserOidcId, requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        if (!request.getAddresseeId().equals(decliningUserOidcId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen ablehnen");
        }
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        request.setStatus(FriendshipStatus.DECLINED);
        request.setRespondedAt(Instant.now());

        friendshipDeclinedEvent.fire(new FriendshipDeclinedEvent(
                request.id, request.getRequesterId(), request.getAddresseeId()));
    }

    @Transactional
    public void cancelFriendshipRequest(Long requestId, String cancellingUserOidcId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Cancelling", cancellingUserOidcId, requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        if (!request.getRequesterId().equals(cancellingUserOidcId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen zurückziehen");
        }
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        friendshipRepo.delete(request);
    }

    @Transactional
    public void removeFriendship(String oidcId1, String oidcId2) {
        LOGGER.debugf("[Principal %s][User %s] Removing Friendship", oidcId1, oidcId2);

        FriendshipRequest friendship = friendshipRepo.findFriendship(oidcId1, oidcId2)
                .orElseThrow(() -> new ResourceNotFoundException("Ihr seid nicht befreundet"));

        if (!friendship.getRequesterId().equals(oidcId1) && !friendship.getAddresseeId().equals(oidcId1)) {
            throw new ForbiddenException("Du bist nicht Teil dieser Freundschaft");
        }

        friendshipRepo.delete(friendship);
        friendshipRemovedEvent.fire(new FriendshipRemovedEvent(oidcId1, oidcId2));
    }

    public List<FriendDto> getFriends(String oidcId) {
        LOGGER.debugf("[Principal %s] Reading Friends", oidcId);
        List<FriendshipRequest> friendships = friendshipRepo.getFriendships(oidcId);

        List<String> friendOidcIds = friendships.stream()
                .map(f -> f.getRequesterId().equals(oidcId) ? f.getAddresseeId() : f.getRequesterId())
                .distinct()
                .toList();

        if (friendOidcIds.isEmpty()) return List.of();

        Map<String, UserIdentity> users = identityPort.findByIds(friendOidcIds);

        Map<String, FriendshipRequest> friendshipMap = friendships.stream()
                .collect(Collectors.toMap(
                        f -> f.getRequesterId().equals(oidcId) ? f.getAddresseeId() : f.getRequesterId(),
                        f -> f
                ));

        return friendOidcIds.stream()
                .map(friendOidcId -> {
                    UserIdentity friend = users.get(friendOidcId);
                    if (friend == null) return null;
                    FriendDto dto = new FriendDto();
                    dto.setUser_id(friend.oidcId());
                    dto.setName(friend.getName());
                    dto.setEmail(friend.email());
                    dto.setProfile_picture_url(friend.profilePictureUrl());
                    FriendshipRequest fr = friendshipMap.get(friendOidcId);
                    dto.setFriends_since(fr.getRespondedAt() != null ? fr.getRespondedAt().toString() : null);
                    return dto;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FriendDto::getName))
                .collect(Collectors.toList());
    }

    public List<FriendshipRequestDto> getIncomingRequests(String oidcId) {
        LOGGER.debugf("[Principal %s] Reading Incoming Friendship Requests", oidcId);
        return buildRequestDTOs(friendshipRepo.getIncomingRequests(oidcId), oidcId, true);
    }

    public List<FriendshipRequestDto> getOutgoingRequests(String oidcId) {
        LOGGER.debugf("[Principal %s] Reading Outgoing Friendship Requests", oidcId);
        return buildRequestDTOs(friendshipRepo.getOutgoingRequests(oidcId), oidcId, false);
    }

    public FriendshipStatus getFriendshipStatus(String oidcId1, String oidcId2) {
        LOGGER.debugf("[Principal %s][User %s] Reading Friendship Status", oidcId1, oidcId2);
        return friendshipRepo.findRequest(oidcId1, oidcId2)
                .map(FriendshipRequest::getStatus)
                .orElse(null);
    }

    /**
     * Find users who are not yet friends with the given user.
     * Delegates to Keycloak search, then filters out existing friends.
     */
    public List<UserDto> findNonFriends(String search, String oidcId, int limit) {
        Set<String> friendIds = friendshipRepo.getFriendOidcIds(oidcId);
        return userService.searchUsers(search, limit * 3).stream()
                .filter(u -> !u.oidcId().equals(oidcId) && !friendIds.contains(u.oidcId()))
                .limit(limit)
                .map(u -> new UserDto(u.oidcId(), u.firstName(), u.lastName()))
                .toList();
    }

    /**
     * Returns recently registered users who are not friends with the given user.
     * Keycloak doesn't expose a createdAt sort, so this returns the first page of results.
     */
    public List<UserDto> findRecentNonFriends(String oidcId, int limit) {
        Set<String> friendIds = friendshipRepo.getFriendOidcIds(oidcId);
        return userService.searchUsers("", limit * 3).stream()
                .filter(u -> !u.oidcId().equals(oidcId) && !friendIds.contains(u.oidcId()))
                .limit(limit)
                .map(u -> new UserDto(u.oidcId(), u.firstName(), u.lastName()))
                .toList();
    }

    private List<FriendshipRequestDto> buildRequestDTOs(
            List<FriendshipRequest> requests, String currentOidcId, boolean incoming) {

        if (requests.isEmpty()) return List.of();

        List<String> otherOidcIds = requests.stream()
                .map(r -> incoming ? r.getRequesterId() : r.getAddresseeId())
                .distinct()
                .toList();

        Map<String, UserIdentity> users = identityPort.findByIds(otherOidcIds);

        return requests.stream()
                .map(r -> {
                    String otherOidcId = incoming ? r.getRequesterId() : r.getAddresseeId();
                    UserIdentity otherUser = users.get(otherOidcId);
                    if (otherUser == null) return null;

                    FriendshipRequestDto dto = new FriendshipRequestDto();
                    dto.setRequestId(r.id);
                    dto.setUserId(otherUser.oidcId());
                    dto.setUserName(otherUser.getName());
                    dto.setUserEmail(otherUser.email());
                    dto.setProfilePictureUrl(otherUser.profilePictureUrl());
                    dto.setStatus(r.getStatus());
                    dto.setCreatedAt(r.getCreatedAt().toString());
                    dto.setRespondedAt(r.getRespondedAt() != null ? r.getRespondedAt().toString() : null);
                    dto.setIncoming(incoming);
                    return dto;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FriendshipRequestDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }
}
