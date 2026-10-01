package com.dyinfotech.annualleavebackend.dto;

import java.util.List;

public record PageResponseDto<T>(
        List<T> items,
        long totalCount,
        boolean hasMore
) {
    public PageResponseDto {
        items = List.copyOf(items);
    }
}
