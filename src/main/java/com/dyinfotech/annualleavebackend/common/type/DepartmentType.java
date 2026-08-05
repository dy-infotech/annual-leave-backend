package com.dyinfotech.annualleavebackend.common.type;


import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum DepartmentType {
	CEO				("대표이사")
	,SI_BUSINESS	("SI사업팀")
	;
	
	private final String name;
	
	private static final Map<String, DepartmentType> nameToEnumMap = Arrays.stream(values())
																			.collect(Collectors.toUnmodifiableMap(DepartmentType::getName,
																			              							Function.identity()));
	
	private DepartmentType(String name) {
		this.name = name;
	}
	
	public static final DepartmentType getType(String type) {
		return nameToEnumMap.get(type);
	}
	public static final DepartmentType getParentDepartmentType() {
		return DepartmentType.CEO;
	}
}
