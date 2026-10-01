package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.dyinfotech.annualleavebackend.common.cache.HolidayCacheKey;
import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.repository.HolidayRepository;

import tools.jackson.databind.ObjectMapper;

class HolidaySyncServiceRegressionTest {

    @Test
    void apiUrl_readsReloadedBasisDataInsteadOfConstructorSnapshot() {
        BasisDataFactory basisDataFactory = mock(BasisDataFactory.class);
        when(basisDataFactory.getAsString(BasisDataType.KASI_SPECIAL_DAY_API_SERVICE_URL))
                .thenReturn(
                        Optional.of("https://old.example/api"),
                        Optional.of("https://new.example/api/"));
        when(basisDataFactory.getAsString(BasisDataType.KASI_HOLIDAY_REQUEST_ADDRESS))
                .thenReturn(
                        Optional.of("old-path"),
                        Optional.of("new-path"));

        HolidaySyncService service = new HolidaySyncService(
                basisDataFactory,
                mock(HolidayRepository.class),
                mock(ObjectMapper.class),
                mock(WebClient.class),
                new HolidayCacheKey(),
                "service-key");

        assertEquals(
                "https://old.example/api/old-path",
                service.resolveApiUrl());
        assertEquals(
                "https://new.example/api/new-path",
                service.resolveApiUrl());
    }
    @Test
    void parser_allowsExplicitZeroCountSnapshot() throws Exception {
        HolidaySyncService service = serviceWithRealObjectMapper();

        var holidays = service.parseHolidays(
                """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":0,"items":{}}}}
                """,
                YearMonth.of(2026, 10));

        assertEquals(0, holidays.size());
    }

    @Test
    void parser_rejectsMissingRowsWhenTotalCountIsPositive() {
        HolidaySyncService service = serviceWithRealObjectMapper();

        assertThrows(IllegalStateException.class, () -> service.parseHolidays(
                """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{}}}}
                """,
                YearMonth.of(2026, 10)));
    }

    @Test
    void parser_rejectsMalformedHolidayDateInsteadOfDroppingIt() {
        HolidaySyncService service = serviceWithRealObjectMapper();

        assertThrows(IllegalStateException.class, () -> service.parseHolidays(
                """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{"item":{"isHoliday":"Y","locdate":"202610","dateName":"bad"}}}}}
                """,
                YearMonth.of(2026, 10)));
    }

    @Test
    void parser_rejectsHolidayFromDifferentMonth() {
        HolidaySyncService service = serviceWithRealObjectMapper();

        assertThrows(IllegalStateException.class, () -> service.parseHolidays(
                """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{"item":{"isHoliday":"Y","locdate":"20261101","dateName":"wrong month"}}}}}
                """,
                YearMonth.of(2026, 10)));
    }

    private HolidaySyncService serviceWithRealObjectMapper() {
        return new HolidaySyncService(
                mock(BasisDataFactory.class),
                mock(HolidayRepository.class),
                new ObjectMapper(),
                mock(WebClient.class),
                new HolidayCacheKey(),
                "service-key");
    }

}
