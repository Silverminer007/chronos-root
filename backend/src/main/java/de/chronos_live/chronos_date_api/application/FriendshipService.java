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
import io.micrometer.core.annotation.Timed;
import io.quarkus.logging.Log;
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
    Event<FriendshipRequestSentEvent> friendshipRequestEvent;

    @Inject
    Event<FriendshipAcceptedEvent> friendshipAcceptedEvent;

    @Inject
    Event<FriendshipDeclinedEvent> friendshipDeclinedEvent;

    @Inject
    Event<FriendshipRemovedEvent> friendshipRemovedEvent;

    public void sendFriendshipRequest(Long requesterId, Long addresseeId, String email) {
        if (addresseeId == null) {
            if (email == null) {
                throw new BadRequestException("You must either set addressee_id or email");
            }

            User addressee =
                    (User) User.find("email = ?1", email)
                            .firstResultOptional()
                            .orElseThrow(() ->
                                    new ResourceNotFoundException("Es wurde kein User mit der E-Mail Adresse " + email + " gefunden"));
            addresseeId = addressee.id;
        }
        this.sendFriendshipRequest(requesterId, addresseeId);
    }

    /**
     * Sendet eine Freundschaftsanfrage
     */
    @Transactional
    public void sendFriendshipRequest(Long requesterId, Long addresseeId) {
        LOGGER.debugf("[Principal %s][Addressee %s] Sending Friendship Request", requesterId, addresseeId);

        Log.info("User " + requesterId + " sending friendship request to " + addresseeId);

        // Validierung
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("Du kannst dir nicht selbst eine Freundschaftsanfrage senden");
        }

        // Prüfe ob Empfänger existiert
        User.findByIdOptional(addresseeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", addresseeId));

        // Prüfe ob bereits eine Anfrage oder Freundschaft existiert
        Optional<FriendshipRequest> existing = friendshipRepo.findRequest(requesterId, addresseeId);
        if (existing.isPresent()) {
            FriendshipRequest existingRequest = existing.get();

            if (existingRequest.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new ValidationException("Ihr seid bereits befreundet");
            }

            if (existingRequest.getStatus() == FriendshipStatus.PENDING) {
                // Prüfe Richtung
                if (existingRequest.getRequesterId().equals(requesterId)) {
                    throw new ValidationException("Du hast bereits eine Freundschaftsanfrage gesendet");
                } else {
                    throw new ValidationException(
                            "Dieser User hat dir bereits eine Freundschaftsanfrage gesendet. " +
                                    "Bitte nimm diese an."
                    );
                }
            }

            // Status = DECLINED: Erlaube neue Anfrage nach Ablehnung
            if (existingRequest.getStatus() == FriendshipStatus.DECLINED) {
                // Lösche alte abgelehnte Anfrage
                friendshipRepo.delete(existingRequest);
            }
        }

        // Erstelle neue Anfrage
        FriendshipRequest request = new FriendshipRequest();
        request.setRequesterId(requesterId);
        request.setAddresseeId(addresseeId);
        request.setStatus(FriendshipStatus.PENDING);
        request.setCreatedAt(Instant.now());
        friendshipRepo.persist(request);

        // Event feuern
        User requester = User.findById(requesterId);
        this.friendshipRequestEvent.fire(new FriendshipRequestSentEvent(
                request.id,
                requesterId,
                addresseeId,
                requester.getName()
        ));

        Log.info("Friendship request created with ID " + request.id);
    }

    /**
     * Nimmt Freundschaftsanfrage an
     */
    @Transactional
    public void acceptFriendshipRequest(Long requestId, Long acceptingUserId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Accepting Friendship Request", acceptingUserId, requestId);

        Log.info("User " + acceptingUserId + " accepting friendship request " + requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        // Prüfe ob User der Empfänger ist
        if (!request.getAddresseeId().equals(acceptingUserId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen annehmen");
        }

        // Prüfe Status
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        // Akzeptiere Anfrage
        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());

        // Event feuern
        this.friendshipAcceptedEvent.fire(new FriendshipAcceptedEvent(
                request.id,
                request.getRequesterId(),
                request.getAddresseeId()
        ));

        Log.info("Friendship request accepted");
    }

    /**
     * Lehnt Freundschaftsanfrage ab
     */
    @Transactional
    public void declineFriendshipRequest(Long requestId, Long decliningUserId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Declining Friendship Request", decliningUserId, requestId);

        Log.info("User " + decliningUserId + " declining friendship request " + requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        // Prüfe ob User der Empfänger ist
        if (!request.getAddresseeId().equals(decliningUserId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen ablehnen");
        }

        // Prüfe Status
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        // Lehne ab
        request.setStatus(FriendshipStatus.DECLINED);
        request.setRespondedAt(Instant.now());

        // Event feuern
        this.friendshipDeclinedEvent.fire(new FriendshipDeclinedEvent(
                request.id,
                request.getRequesterId(),
                request.getAddresseeId()
        ));

        Log.info("Friendship request declined");
    }

    /**
     * Zieht eigene Freundschaftsanfrage zurück
     */
    @Transactional
    public void cancelFriendshipRequest(Long requestId, Long cancellingUserId) {
        LOGGER.debugf("[Principal %s][Friendship Request %s] Cancelling Friendship Request", cancellingUserId, requestId);

        Log.info("User " + cancellingUserId + " cancelling friendship request " + requestId);

        FriendshipRequest request = friendshipRepo.findByIdOptional(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Freundschaftsanfrage", requestId));

        // Prüfe ob User der Absender ist
        if (!request.getRequesterId().equals(cancellingUserId)) {
            throw new ForbiddenException("Du kannst nur deine eigenen Freundschaftsanfragen zurückziehen");
        }

        // Prüfe Status
        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ValidationException("Diese Freundschaftsanfrage wurde bereits bearbeitet");
        }

        // Lösche Anfrage
        friendshipRepo.delete(request);

        Log.info("Friendship request cancelled");
    }

    /**
     * Entfernt Freundschaft
     */
    @Transactional
    public void removeFriendship(Long userId1, Long userId2) {
        LOGGER.debugf("[Principal %s][User %s] Removing Friendship", userId1, userId2);

        Log.info("Removing friendship between " + userId1 + " and " + userId2);

        // Finde Freundschaft
        FriendshipRequest friendship = friendshipRepo.findFriendship(userId1, userId2)
                .orElseThrow(() -> new ResourceNotFoundException("Ihr seid nicht befreundet"));

        // Prüfe ob einer der beiden User beteiligt ist
        if (!friendship.getRequesterId().equals(userId1) &&
                !friendship.getAddresseeId().equals(userId1)) {
            throw new ForbiddenException("Du bist nicht Teil dieser Freundschaft");
        }

        // Lösche Freundschaft
        friendshipRepo.delete(friendship);

        // Event feuern
        friendshipRemovedEvent.fire(new FriendshipRemovedEvent(userId1, userId2));

        Log.info("Friendship removed");
    }

    /**
     * Lädt alle Freunde eines Users
     */
    public List<FriendDto> getFriends(Long userId) {
        LOGGER.debugf("[Principal %s] Reading Friends", userId);
        List<FriendshipRequest> friendships = friendshipRepo.getFriendships(userId);

        // Extrahiere Friend-IDs
        List<Long> friendIds = friendships.stream()
                .map(f -> f.getRequesterId().equals(userId) ?
                        f.getAddresseeId() : f.getRequesterId())
                .distinct()
                .toList();

        if (friendIds.isEmpty()) {
            return List.of();
        }

        // Lade User-Daten
        Map<Long, User> users = User.findByIds(friendIds).stream()
                .collect(Collectors.toMap(u -> ((User) u).id, u -> (User) u));

        // Erstelle Mapping: friendId -> friendship (für friendsSince)
        Map<Long, FriendshipRequest> friendshipMap = friendships.stream()
                .collect(Collectors.toMap(
                        f -> f.getRequesterId().equals(userId) ?
                                f.getAddresseeId() : f.getRequesterId(),
                        f -> f
                ));

        // Baue DTOs
        return friendIds.stream()
                .map(friendId -> {
                    User friend = users.get(friendId);
                    if (friend == null) return null;

                    FriendDto dto = new FriendDto();
                    dto.setUser_id(friend.id);
                    dto.setName(friend.getName());
                    dto.setEmail(friend.getEmail());
                    dto.setProfile_picture_url(friend.getProfilePictureUrl());
                    dto.setFriends_since(friendshipMap.get(friendId).getRespondedAt().toString());
                    return dto;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FriendDto::getName))
                .collect(Collectors.toList());
    }

    /**
     * Lädt eingehende Freundschaftsanfragen
     */
    public List<FriendshipRequestDto> getIncomingRequests(Long userId) {
        LOGGER.debugf("[Principal %s] Reading Incoming Friendship Requests", userId);
        List<FriendshipRequest> requests = friendshipRepo.getIncomingRequests(userId);
        return buildRequestDTOs(requests, userId, true);
    }

    /**
     * Lädt ausgehende Freundschaftsanfragen
     */
    public List<FriendshipRequestDto> getOutgoingRequests(Long userId) {
        LOGGER.debugf("[Principal %s] Reading Outgoing Friendship Requests", userId);
        List<FriendshipRequest> requests = friendshipRepo.getOutgoingRequests(userId);
        return buildRequestDTOs(requests, userId, false);
    }

    /**
     * Gibt Freundschaftsstatus zwischen zwei Usern zurück
     */
    public FriendshipStatus getFriendshipStatus(Long userId1, Long userId2) {
        LOGGER.debugf("[Principal %s][User %s] Reading Friendship Status", userId1, userId2);
        return friendshipRepo.findRequest(userId1, userId2)
                .map(FriendshipRequest::getStatus)
                .orElse(null);
    }

    /**
     * Hilfsmethode: Baut Request DTOs
     */
    private List<FriendshipRequestDto> buildRequestDTOs(
            List<FriendshipRequest> requests, Long currentUserId, boolean incoming) {

        if (requests.isEmpty()) {
            return List.of();
        }

        // Extrahiere User-IDs (der jeweils andere User)
        List<Long> userIds = requests.stream()
                .map(r -> incoming ? r.getRequesterId() : r.getAddresseeId())
                .distinct()
                .toList();

        // Lade User-Daten
        Map<Long, User> users = User.findByIds(userIds).stream()
                .collect(Collectors.toMap(u -> ((User) u).id, u -> (User) u));

        // Baue DTOs
        return requests.stream()
                .map(r -> {
                    Long otherUserId = incoming ? r.getRequesterId() : r.getAddresseeId();
                    User otherUser = users.get(otherUserId);
                    if (otherUser == null) return null;

                    FriendshipRequestDto dto = new FriendshipRequestDto();
                    dto.setRequestId(r.id);
                    dto.setUserId(otherUser.id);
                    dto.setUserName(otherUser.getName());
                    dto.setUserEmail(otherUser.getEmail());
                    dto.setProfilePictureUrl(otherUser.getProfilePictureUrl());
                    dto.setStatus(r.getStatus());
                    dto.setCreatedAt(r.getCreatedAt().toString());
                    dto.setRespondedAt(r.getRespondedAt() == null ? null : r.getRespondedAt().toString());
                    dto.setIncoming(incoming);
                    return dto;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FriendshipRequestDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<UserDto> findNonFriends(String search, Long userId, int limit) {
        return this.friendshipRepo.findNonFriends(search, userId, limit)
                .stream()
                .map(u -> new UserDto(u.id, u.getFirstName(), u.getLastName()))
                .toList();
    }

    public List<UserDto> findRecentNonFriends(Long userId, int limit) {
        return this.friendshipRepo.findRecentNonFriends(userId, limit)
                .stream()
                .map(u -> new UserDto(u.id, u.getFirstName(), u.getLastName()))
                .toList();
    }
}
