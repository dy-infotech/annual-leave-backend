package com.dyinfotech.annualleavebackend.common.cache;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 공휴일 캐시 세대 키.
 *
 * 갱신 전에 시작한 느린 조회가 캐시 eviction 뒤에 완료되더라도 구세대 키에만
 * 결과를 저장하게 하여 새 공휴일 snapshot을 다시 오염시키지 않는다.
 * 단일 인스턴스 배포를 전제로 한다.
 */
@Component("holidayCacheKey")
public class HolidayCacheKey {

    private final AtomicLong generation = new AtomicLong();

    public String year(int year) {
        return generation.get() + ":year:" + year;
    }

    public String range(int startYear, int endYear) {
        return generation.get() + ":range:" + startYear + ":" + endYear;
    }

    public void advance() {
        generation.incrementAndGet();
    }

    long currentGeneration() {
        return generation.get();
    }
}
