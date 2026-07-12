package com.dyinfotech.annualleavebackend.common.type;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public enum BasisDataType {
	FIRST_YEAR_LEAVE_DAYS				(1L, "1년차 연차일수")
	,N_YEARS_OF_ADDITIONAL_LEAVE		(2L, "N년당 추가연차 발생")
	,ADDITIONAL_LEAVE_DAYS				(3L, "추가연차 일수")
	,MINIMUM_ATTENDANCE_RATE_FOR_LEAVE	(4L, "만근 출석 퍼센트")
	,EMPlOYEE_NUMBER_PREFIX				(5L, "사번 접두사")
	,MAXIMUM_LEAVE_DAYS					(6L, "최대 연차일수")
	,KASI_SPECIAL_DAY_API_SERVICE_URL	(7L, "한국천문연구원_특일 정보 API 서비스 URL")
	,KASI_HOLIDAY_REQUEST_ADDRESS		(8L, "한국천문연구원_특일 정보 API 공휴일 요청 주소")
	;
	
	private final long code;
	private final String description;
	
	private static final Map<Long, BasisDataType> codeToEnumMap = new HashMap<>();
	static {
		for (BasisDataType type : BasisDataType.values()) {
			codeToEnumMap.put(type.getCode(), type);
		}
	}
	
	BasisDataType(long code, String description) {
		this.code = code;
		this.description = description;
	}
	
	public static BasisDataType fromCode(long code) {
		return codeToEnumMap.get(code);
	}
}
