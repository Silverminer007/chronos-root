package de.chronos_live.chronos_date_api.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> data,
        Meta meta
) {
    public record Meta(
            int page,
            int size,
            long total
    ) {
    }
}
