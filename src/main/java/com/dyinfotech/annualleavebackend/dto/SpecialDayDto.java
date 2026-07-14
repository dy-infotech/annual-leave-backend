package com.dyinfotech.annualleavebackend.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

public class SpecialDayDto {

//    @Getter
//    @NoArgsConstructor
//    public static class SpecialDayRequest {
//    	
//    }

    @Getter
    @Builder
    public static class SpecialDayResponse {
        private String name;
        private LocalDate date;
    }
}
