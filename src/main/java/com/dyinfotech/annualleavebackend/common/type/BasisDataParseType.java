package com.dyinfotech.annualleavebackend.common.type;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum BasisDataParseType {
	BOOLEAN		(0)
	, INTEGER	(1)
	, LONG		(2)
	, FLOAT		(3)
	, DOUBLE	(4)
	, STRING	(5)
	;
	
	private final int code;
	
	private static final Map<Integer, BasisDataParseType> codeToEnumMap = Arrays.stream(values())
																				.collect(Collectors.toUnmodifiableMap(BasisDataParseType::getCode,
																		              									Function.identity()));
	
	BasisDataParseType(int code) {
		this.code = code;
	}
	
	public static BasisDataParseType fromCode(int code) {
		return codeToEnumMap.get(code);
	}
}
