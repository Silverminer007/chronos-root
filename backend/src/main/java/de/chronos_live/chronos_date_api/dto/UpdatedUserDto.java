package de.chronos_live.chronos_date_api.dto;

import lombok.Data;

@Data
public class UpdatedUserDto {
    private PrincipalDto user;
    private String verifyEmailUrl;
}
