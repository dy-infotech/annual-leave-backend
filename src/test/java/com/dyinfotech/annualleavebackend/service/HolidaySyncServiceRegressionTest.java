package com.dyinfotech.annualleavebackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

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
                "service-key");

        assertEquals(
                "https://old.example/api/old-path",
                service.resolveApiUrl());
        assertEquals(
                "https://new.example/api/new-path",
                service.resolveApiUrl());
    }
}
