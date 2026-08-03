package com.dyinfotech.annualleavebackend.common.type;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.dyinfotech.annualleavebackend.config.CommonConfig;

import lombok.Getter;

@Getter
public enum LeaveUnitType {
	DAY			("DAY",		"1일 단위",		CommonConfig.DAILY_STANDARD_WORKING_MINUTES)
	,HALF		("HALF",	"0.5일 단위",		CommonConfig.DAILY_STANDARD_WORKING_MINUTES / 2)
	,QUARTER	("QUARTER",	"0.25일 단위",	CommonConfig.DAILY_STANDARD_WORKING_MINUTES / 4)
	,HOUR		("HOUR",	"1시간 단위",		(int) Duration.ofHours(1).toMinutes())
	;
	
	private final String name;			// 사실 enum의 name() 메소드 써도 상관은 없다.
	private final String desc;
	private final int minutesPerUnit;	// 최소 사용 분 단위
	
	private static final Map<String, LeaveUnitType> nameToEnumMap = Arrays.stream(values())
																			.collect(Collectors.toUnmodifiableMap(LeaveUnitType::getName,
																		              								Function.identity()));
	
	LeaveUnitType(String name, String desc, int minutesPerUnit) {
		this.name = name;
		this.desc = desc;
		this.minutesPerUnit = minutesPerUnit;
	}
	
	public static LeaveUnitType fromName(String name) {
		return nameToEnumMap.get(name);
	}
	
	protected boolean isValidMinutes(int useMinutes) {
		return useMinutes % minutesPerUnit == 0;
	}
	protected String getValidationMessage() {
    	return desc + "로 사용 가능합니다";
	}
}
