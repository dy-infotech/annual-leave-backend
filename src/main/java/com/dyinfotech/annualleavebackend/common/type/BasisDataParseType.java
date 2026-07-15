package com.dyinfotech.annualleavebackend.common.type;

import java.util.HashMap;
import java.util.Map;

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
	
	private static final Map<Integer, BasisDataParseType> codeToEnumMap = new HashMap<>();
	static {
		for (BasisDataParseType type : BasisDataParseType.values()) {
			codeToEnumMap.put(type.getCode(), type);
		}
	}
	
	BasisDataParseType(int code) {
		this.code = code;
	}
	
	public static BasisDataParseType fromCode(int code) {
		return codeToEnumMap.get(code);
	}
}
