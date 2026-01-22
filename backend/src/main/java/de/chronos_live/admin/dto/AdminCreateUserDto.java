package de.chronos_live.admin.dto;

import lombok.Data;

@Data
public class AdminCreateUserDto {
    private String firstName, lastName, email, oidcId;
}
