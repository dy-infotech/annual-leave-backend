package com.dyinfotech.annualleavebackend.common.type;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public enum LeaveType {
	FULL			("FULL",		"연차")
	,AM_HALF		("AM_HALF",		"반차(오전)")
	,PM_HALF		("PM_HALF",		"반차(오후)")
	,ALTERNATIVE	("ALTERNATIVE",	"대체 휴가")
	,PERENTAL		("PERENTAL",	"출산 휴가")
	,FAMILY			("FAMILY",		"가족 돌봄 휴가")
	,OTHER			("OTHER",		"기타")
	;
	
	private String name;	// 사실 enum의 name() 메소드 써도 상관은 없다.
	private String desc;
	
	private static final Map<String, LeaveType> nameToEnumMap = new HashMap<>();
	static {
		for (LeaveType type : LeaveType.values()) {
			nameToEnumMap.put(type.getName(), type);
		}
	}
	
	LeaveType(String name, String desc) {
		this.name = name;
		this.desc = desc;
	}
	
	public static LeaveType fromName(String name) {
		return nameToEnumMap.get(name);
	}
}
