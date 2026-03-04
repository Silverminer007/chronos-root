package de.chronos_live.chronos_date_api.dto;

import lombok.Data;

@Data
public class ChangePasswordDto {
    private String current_password;
    private String new_password;
}
