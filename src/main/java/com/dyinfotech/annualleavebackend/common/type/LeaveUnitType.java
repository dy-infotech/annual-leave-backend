package com.dyinfotech.annualleavebackend.common.type;

import java.util.HashMap;
import java.util.Map;

import com.dyinfotech.annualleavebackend.config.CommonConfig;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public enum LeaveUnitType {
	DAY			("DAY",		"1일 단위",		CommonConfig.DAILY_STANDARD_WORKING_MINUTES)
	,HALF		("HALF",	"0.5일 단위",		CommonConfig.DAILY_STANDARD_WORKING_MINUTES / 2)
	,QUATER		("QUATER",	"0.25일 단위",	CommonConfig.DAILY_STANDARD_WORKING_MINUTES / 4)
	,HOUR		("HOUR",	"1시간 단위",		60)
	;
	
	private String name;		// 사실 enum의 name() 메소드 써도 상관은 없다.
	private String desc;
	private int minutesPerUnit;	// 최소 사용 분 단위
	
	private static final Map<String, LeaveUnitType> nameToEnumMap = new HashMap<>();
	static {
		for (LeaveUnitType type : LeaveUnitType.values()) {
			nameToEnumMap.put(type.getName(), type);
		}
	}
	
	LeaveUnitType(String name, String desc, int minutesPerUnit) {
		this.name = name;
		this.desc = desc;
		this.minutesPerUnit = minutesPerUnit;
	}
	
	public static LeaveUnitType fromName(String name) {
		return nameToEnumMap.get(name);
	}
	
	public static final int toMinutes(@NotNull Float useDays) {
		return Math.round(useDays * CommonConfig.DAILY_STANDARD_WORKING_MINUTES);
	}
	protected boolean isValidMinutes(int useMinutes) {
		return useMinutes % minutesPerUnit == 0;
	}
	protected String getValidationMessage() {
    	return desc + "로 사용 가능합니다";
	}
}
