package com.dyinfotech.annualleavebackend.common.type;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum PositionType {
	INTERN						("인턴")
	,ASSOCIATE					("사원")
	,SENIOR_ASSOCIATE			("주임")
	,ASSISTANT_MANAGER			("대리")
	,MANAGER					("과장")
	,SENIOR_MANAGER				("차장")
	,GENERAL_MANAGER			("부장")
	,DIRECTOR					("이사")
	,MANAGING_DIRECTOR			("상무")
	,SENIOR_MANAGING_DIRECTOR	("전무")
	,CEO						("사장")
	;
	
	private final String name;
	
	private static final Map<String, PositionType> nameToEnumMap = Arrays.stream(values())
																		.collect(Collectors.toUnmodifiableMap(PositionType::getName,
																		              							Function.identity()));
	
	private PositionType(String name) {
		this.name = name;
	}
	
	public static final PositionType getType(String name) {
		return nameToEnumMap.get(name);
	}
	public static final boolean isCEO(PositionType position) {
		return CEO.equals(position);
	}
}
