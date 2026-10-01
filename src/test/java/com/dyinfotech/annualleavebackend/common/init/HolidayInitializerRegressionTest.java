package com.dyinfotech.annualleavebackend.common.init;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.service.HolidaySyncService;

import reactor.core.publisher.Mono;

class HolidayInitializerRegressionTest {

    @Test
    void syncYear_refreshesAllTwelveMonthsInsteadOfTrustingPartialYearRows() {
        HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        HolidayInitializer initializer = new HolidayInitializer(holidaySyncService, clock);

        for (int month = 1; month <= 12; month++) {
            when(holidaySyncService.fetchHolidaysFromApi(2026, month))
                    .thenReturn(Mono.just(List.of()));
            when(holidaySyncService.deleteAndSaveHolidays(eq(2026), eq(month), anyList()))
                    .thenReturn(Mono.empty());
        }

        initializer.syncYear(2026);

        for (int month = 1; month <= 12; month++) {
            verify(holidaySyncService).fetchHolidaysFromApi(2026, month);
        }
        verify(holidaySyncService, times(1)).findAllByYear(2026);
    }
    @Test
    void syncYear_continuesOtherMonthsWhenOneMonthFails() {
        HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        HolidayInitializer initializer = new HolidayInitializer(holidaySyncService, clock);

        for (int month = 1; month <= 12; month++) {
            if (month == 5) {
                when(holidaySyncService.fetchHolidaysFromApi(2026, month))
                        .thenReturn(Mono.error(new IllegalStateException("temporary api failure")));
            } else {
                when(holidaySyncService.fetchHolidaysFromApi(2026, month))
                        .thenReturn(Mono.just(List.of()));
                when(holidaySyncService.deleteAndSaveHolidays(eq(2026), eq(month), anyList()))
                        .thenReturn(Mono.empty());
            }
        }

        initializer.syncYear(2026);

        for (int month = 1; month <= 12; month++) {
            verify(holidaySyncService).fetchHolidaysFromApi(2026, month);
        }
        verify(holidaySyncService, times(1)).findAllByYear(2026);
    }

}
