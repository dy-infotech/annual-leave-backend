package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpecialDayDto {

    @Getter
    @Builder
    public static class SpecialDayResponse {
        private String name;
        private LocalDate date;
    }
}
