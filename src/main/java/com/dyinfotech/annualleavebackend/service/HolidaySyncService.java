package com.dyinfotech.annualleavebackend.service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.repository.HolidayRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class HolidaySyncService {
	private final BasisDataFactory basisDataFactory;
	private final HolidayRepository holidayRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    
    private final ReentrantLock lock = new ReentrantLock();

    private final String serviceKey;
    
    private final String API_URL;
    
    public HolidaySyncService(
    		BasisDataFactory basisDataFactory,
            HolidayRepository holidayRepository,
            ObjectMapper objectMapper,
            WebClient webClient,
            @Value("${openapi.service-key}") String serviceKey
    ) {
    	this.basisDataFactory = basisDataFactory;
    	this.holidayRepository = holidayRepository;
    	this.objectMapper = objectMapper;
    	this.webClient = webClient;
    	this.serviceKey = serviceKey;
    	this.API_URL = this.basisDataFactory.getAsString(BasisDataType.KASI_SPECIAL_DAY_API_SERVICE_URL).orElse("http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService") + "/" + 
    					this.basisDataFactory.getAsString(BasisDataType.KASI_HOLIDAY_REQUEST_ADDRESS).orElse("getRestDeInfo");
    }
    
    @Transactional(readOnly = true)
    public List<Holiday> findAllByYear(int year) {
    	return holidayRepository.findAllByYear(year);
    }
    
    public boolean existsByYear(int year) {
    	return holidayRepository.existsByYear(year);
    }
    
    @Transactional(readOnly = true)
    public List<Holiday> findByYearRange(int startYear, int endYear) {
    	return holidayRepository.findByYearRange(startYear, endYear);
    }
    
    public Mono<List<Holiday>> fetchHolidaysFromApi(int year, int month) {
        String yearStr = String.valueOf(year);
        String monthStr = String.format("%02d", month);

        URI uri = UriComponentsBuilder.fromUriString(API_URL)
        		.queryParam("serviceKey", serviceKey)
        		.queryParam("solYear", yearStr)
        		.queryParam("solMonth", monthStr)
        		.queryParam("_type", "json")
        		.build(true)
        		.toUri();
        return webClient.get()
        		.uri(uri)
        		.retrieve()
        		.bodyToMono(String.class)
        		// XXX: With JDK HttpClient, readTimeout is only available at the HttpRequest level
        		.timeout(Duration.ofSeconds(10L))
        		.map(response -> {
        			try {
        				return parseHolidays(response);
        			} catch (Exception e) {
        				throw reactor.core.Exceptions.propagate(e);
        			}
        		})
        		.retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
        		.onErrorResume(e -> {
        			log.error("[공공데이터] {}년 {}월 공휴일 조회 중 에러 발생", yearStr, monthStr, e);
                    return Mono.error(new RuntimeException("API 호출 실패", e));
        		});
    }
    
    public Mono<Void> deleteAndSaveHolidays(int year, int month, List<Holiday> holidays) {
    	return Mono.fromRunnable(() -> {
    		lock.lock();
        	try {
    	        holidayRepository.replaceMonthlyHolidays(year, month, holidays);
    	        log.info("[공공데이터] {}년 {}월 공휴일 동기화 완료", year, String.format("%02d", month));
        	} finally {
        		lock.unlock();
        	}
    	})
        .subscribeOn(Schedulers.boundedElastic()) //	 블로킹 전용 스레드 사용
        .then();
    }
    
    private enum ResultCode {
    	SUCCESS ("00")
    	,FAIL	("99")
    	;
    	
    	private String code;
    	
    	ResultCode(String code) {
    		this.code = code;
    	}
    	
    	public String getCode() {
    		return code;
    	}
    }

    private List<Holiday> parseHolidays(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode responseNode = root.path("response");
        JsonNode headerNode = responseNode.path("header");
        String resultCode = headerNode.path("resultCode").asText(ResultCode.FAIL.getCode());
        if (!ResultCode.SUCCESS.getCode().equals(resultCode)) {
        	String errorMsg = "[공공데이터] 공휴일 데이터 파싱 오류 resultCode: " + resultCode + ", resultMsg: " + headerNode.path("resultMsg").asText();
        	log.error(errorMsg);
        	throw new RuntimeException(errorMsg);
        }
        
        JsonNode itemsNode = responseNode.path("body").path("items");
        if (itemsNode.isMissingNode() || itemsNode.path("item").isMissingNode()) {
            return Collections.emptyList();
        }
        
        // 2개 이상의 데이터면 isArray: true, 1개의 데이터면 isObject: true
        JsonNode itemNode = itemsNode.path("item");
        List<Holiday> holidays = new ArrayList<>();

        if (itemNode.isArray()) {
            for (JsonNode item : itemNode) {
                Holiday h = convertToEntity(item);
                if (h != null) holidays.add(h);
            }
        } else if (itemNode.isObject()) {
            Holiday h = convertToEntity(itemNode);
            if (h != null) holidays.add(h);
        }

        return holidays;
    }

    private Holiday convertToEntity(JsonNode item) {
        String isHoliday = item.path("isHoliday").asText("N");
        if (!"Y".equals(isHoliday)) {
            return null; 
        }

        // 공공데이터 날짜 추출 (예: "20150901")
        String locdateStr = item.path("locdate").asText(); 
        if (locdateStr.length() != 8) return null;

        // substring으로 분할하여 빌더로 조립
        String year = locdateStr.substring(0, 4);  // "2015"
        String month = locdateStr.substring(4, 6); // "09"
        String day = locdateStr.substring(6, 8);   // "01"
        String dateName = item.path("dateName").asText("공휴일");

        return Holiday.builder()
        		.holidayDate(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)))
                .name(dateName)
                .build();
    }
}
