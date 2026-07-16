package de.chronos_live.chronos_date_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendDto {
    private String user_id;
    private String name;
    private String email;
    private String profile_picture_url;
    private String friends_since;
}