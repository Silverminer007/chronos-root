package de.chronos_live.chronos_date_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.chronos_live.chronos_date_api.domain.FriendshipStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendshipRequestDto {
    private Long requestId;
    private String userId;
    private String userName;
    private String userEmail;
    private String profilePictureUrl;
    private FriendshipStatus status;
    private String createdAt;
    private String respondedAt;
    private boolean isIncoming;  // true = eingehend, false = ausgehend
}