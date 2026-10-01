package com.dyinfotech.annualleavebackend.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.dyinfotech.annualleavebackend.common.cache.HolidayCacheKey;
import com.dyinfotech.annualleavebackend.common.factory.BasisDataFactory;
import com.dyinfotech.annualleavebackend.common.type.BasisDataType;
import com.dyinfotech.annualleavebackend.domain.Holiday;
import com.dyinfotech.annualleavebackend.repository.HolidayRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class HolidaySyncService {
	private final BasisDataFactory basisDataFactory;
	private final HolidayRepository holidayRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final HolidayCacheKey holidayCacheKey;
    
    private final ReentrantLock lock = new ReentrantLock();

    private final String serviceKey;
    
    public HolidaySyncService(
    		BasisDataFactory basisDataFactory,
            HolidayRepository holidayRepository,
            ObjectMapper objectMapper,
            WebClient webClient,
            HolidayCacheKey holidayCacheKey,
            @Value("${openapi.service-key}") String serviceKey
    ) {
    	this.basisDataFactory = basisDataFactory;
    	this.holidayRepository = holidayRepository;
    	this.objectMapper = objectMapper;
    	this.webClient = webClient;
        this.holidayCacheKey = holidayCacheKey;
    	this.serviceKey = serviceKey;
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

        URI uri = UriComponentsBuilder.fromUriString(resolveApiUrl())
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
        		.timeout(Duration.ofSeconds(30L))
        		.map(response -> {
        			try {
        				return parseHolidays(response, YearMonth.of(year, month));
        			} catch (Exception e) {
        				throw reactor.core.Exceptions.propagate(e);
        			}
        		})
        		.retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
        						.filter(this::isRetryable))
        		.doOnError(e ->
	                log.error(
	                    "[공공데이터] {}년 {}월 공휴일 조회 실패. errorType={}",
	                    yearStr,
	                    monthStr,
	                    e.getClass().getSimpleName()
	                )
	            )
        		// WebClient 예외에는 요청 URI가 포함될 수 있다. URI query의 serviceKey가
        		// 상위 scheduler stack trace로 노출되지 않도록 credential-safe 예외로 경계를 닫는다.
        		.onErrorMap(e -> new IllegalStateException(
        		        "공휴일 API 호출 실패 (" + e.getClass().getSimpleName() + ")"));
    }

    String resolveApiUrl() {
        String baseUrl = basisDataFactory
                .getAsString(BasisDataType.KASI_SPECIAL_DAY_API_SERVICE_URL)
                .filter(value -> !value.isBlank())
                .orElse("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService");
        String requestAddress = basisDataFactory
                .getAsString(BasisDataType.KASI_HOLIDAY_REQUEST_ADDRESS)
                .filter(value -> !value.isBlank())
                .orElse("getRestDeInfo");

        return baseUrl.endsWith("/")
                ? baseUrl + requestAddress
                : baseUrl + "/" + requestAddress;
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return responseException.getStatusCode().is5xxServerError() || status == 429;
        }
        return e instanceof TimeoutException
            || e instanceof WebClientRequestException
            || e instanceof IOException;
    }
    
    public Mono<Void> deleteAndSaveHolidays(int year, int month, List<Holiday> holidays) {
    	return Mono.fromRunnable(() -> {
    		lock.lock();
        	try {
    	        holidayRepository.replaceMonthlyHolidays(year, month, holidays);
                // repository 트랜잭션이 반환(커밋)된 뒤 세대를 올린다. 늦은 구세대 조회는 이후 hit되지 않는다.
                holidayCacheKey.advance();
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

    private List<Holiday> parseHolidays(String jsonResponse, YearMonth requestedMonth) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode responseNode = root.path("response");
        JsonNode headerNode = responseNode.path("header");
        String resultCode = headerNode.path("resultCode").asString(ResultCode.FAIL.getCode());
        if (!ResultCode.SUCCESS.getCode().equals(resultCode)) {
            String errorMsg = "[공공데이터] 공휴일 데이터 파싱 오류 resultCode: " + resultCode
                    + ", resultMsg: " + headerNode.path("resultMsg").asString();
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        JsonNode bodyNode = responseNode.path("body");
        JsonNode totalNode = bodyNode.path("totalCount");
        if (!totalNode.isIntegralNumber() || totalNode.asInt() < 0) {
            throw new IllegalStateException("[공공데이터] 공휴일 응답 totalCount가 잘못되었습니다.");
        }

        int totalCount = totalNode.asInt();
        JsonNode itemNode = bodyNode.path("items").path("item");
        List<JsonNode> rows = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(rows::add);
        } else if (itemNode.isObject()) {
            rows.add(itemNode);
        } else if (totalCount != 0) {
            throw new IllegalStateException("[공공데이터] 공휴일 응답 항목이 누락되었습니다.");
        }

        // numOfRows=100으로 월 전체를 요청한다. 일부 페이지만 받은 snapshot으로 기존 월 데이터를 지우지 않는다.
        if (rows.size() != totalCount) {
            throw new IllegalStateException(
                    "[공공데이터] 공휴일 응답이 불완전합니다. totalCount=" + totalCount + ", rows=" + rows.size());
        }

        List<Holiday> holidays = new ArrayList<>();
        for (JsonNode item : rows) {
            Holiday holiday = convertToEntity(item, requestedMonth);
            if (holiday != null) {
                holidays.add(holiday);
            }
        }
        return holidays;
    }

    private Holiday convertToEntity(JsonNode item, YearMonth requestedMonth) {
        String isHoliday = item.path("isHoliday").asString("");
        if (!"Y".equals(isHoliday) && !"N".equals(isHoliday)) {
            throw new IllegalStateException("[공공데이터] 공휴일 여부가 누락되었거나 잘못되었습니다.");
        }
        if ("N".equals(isHoliday)) {
            return null;
        }

        String rawDate = item.path("locdate").asString("");
        if (!rawDate.matches("\\d{8}")) {
            throw new IllegalStateException("[공공데이터] 공휴일 날짜가 잘못되었습니다: " + rawDate);
        }

        final LocalDate holidayDate;
        try {
            holidayDate = LocalDate.parse(rawDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException e) {
            throw new IllegalStateException("[공공데이터] 공휴일 날짜를 해석할 수 없습니다: " + rawDate, e);
        }
        if (!YearMonth.from(holidayDate).equals(requestedMonth)) {
            throw new IllegalStateException(
                    "[공공데이터] 요청 월과 공휴일 날짜가 다릅니다. requested=" + requestedMonth + ", actual=" + holidayDate);
        }

        String dateName = item.path("dateName").asString("공휴일");
        return Holiday.builder()
                .holidayDate(holidayDate)
                .name(dateName)
                .build();
    }

}
