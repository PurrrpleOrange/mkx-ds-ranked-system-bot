package com.mkx.ranked.model.dto;

import java.util.List;

public record PageDto<T>(
        List<T> content,
        int currentPage,
        int totalPages,
        long totalItems,
        int pageSize
) {
    public boolean isFirst() {
        return currentPage <= 0;
    }

    public boolean isLast() {
        return totalPages == 0 || currentPage >= totalPages - 1;
    }
}
