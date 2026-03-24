package de.chronos_live.admin.application;

import de.chronos_live.admin.dto.AdminUserDto;
import de.chronos_live.admin.dto.AdminUserListResponse;
import de.chronos_live.chronos_date_api.domain.User;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
@Timed("service.admin.users")
public class AdminUserService {

    public AdminUserListResponse listUsers(int page, int size, Instant lastSeenAfter, Instant lastSeenBefore) {
        StringBuilder query = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (lastSeenAfter != null) {
            query.append(" AND lastSeen >= :lastSeenAfter");
            params.put("lastSeenAfter", lastSeenAfter);
        }
        if (lastSeenBefore != null) {
            query.append(" AND lastSeen <= :lastSeenBefore");
            params.put("lastSeenBefore", lastSeenBefore);
        }

        PanacheQuery<User> panacheQuery = User.find(query.toString(), Sort.descending("lastSeen"), params);
        long total = panacheQuery.count();
        List<User> users = panacheQuery.page(page, size).list();

        List<AdminUserDto> items = users.stream()
                .map(u -> new AdminUserDto(u.id, u.getFirstName(), u.getLastName(), u.getEmail(),
                        u.getCreatedAt(), u.getLastUpdate(), u.getLastSeen()))
                .toList();

        return new AdminUserListResponse(items, page, size, total);
    }
}
