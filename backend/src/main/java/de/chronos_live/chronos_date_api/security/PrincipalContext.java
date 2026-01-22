package de.chronos_live.chronos_date_api.security;

import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@RequestScoped
public class PrincipalContext {
    private boolean adminRequest = false;
    private User principal;
}