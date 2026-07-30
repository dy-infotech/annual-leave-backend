package com.dyinfotech.annualleavebackend.common.type;

import java.util.HashMap;
import java.util.Map;

public enum LeaveType {
	FULL			("FULL",		"연차",			LeaveUnitType.DAY)
	,AM_HALF		("AM_HALF",		"반차(오전)",		LeaveUnitType.HALF)
	,PM_HALF		("PM_HALF",		"반차(오후)",		LeaveUnitType.HALF)
	,ALTERNATIVE	("ALTERNATIVE",	"대체 휴가",		LeaveUnitType.DAY)
	,PERENTAL		("PERENTAL",	"출산 휴가",		LeaveUnitType.DAY)
	,FAMILY			("FAMILY",		"가족 돌봄 휴가",	LeaveUnitType.DAY)
	,OTHER			("OTHER",		"기타",			LeaveUnitType.DAY)
	;
	
	private String name;		// 사실 enum의 name() 메소드 써도 상관은 없다.
	private String desc;
	private LeaveUnitType leaveUnitType;	// 최소 사용 단위
	
	private static final Map<String, LeaveType> nameToEnumMap = new HashMap<>();
	static {
		for (LeaveType type : LeaveType.values()) {
			nameToEnumMap.put(type.getName(), type);
		}
	}
	
	LeaveType(String name, String desc, LeaveUnitType leaveUnitType) {
		this.name = name;
		this.desc = desc;
		this.leaveUnitType = leaveUnitType;
	}
	
	public static LeaveType fromName(String name) {
		return nameToEnumMap.get(name);
	}
	
	// XXX: leaveUnitType이 노출되지 않게 하도록 하기 위해 @Getter를 제외하고 직접 getter 구현 (노출되지 않는 게 개발자에게 혼동의 여지가 적다.)
	public String getName() {
		return name;
	}
	public String getDesc() {
		return desc;
	}
	
	/**
     * 하루 미만 단위 휴가인지 여부
     */
    public boolean isPartialLeave() {
        return !this.leaveUnitType.equals(LeaveUnitType.DAY);
    }
    
    /**
     * 반차 여부
     */
	public boolean isHalfLeave() {
		return this.leaveUnitType.equals(LeaveUnitType.HALF);
	}
	
	public boolean isValidMinutes(int useMinutes) {
		return this.leaveUnitType.isValidMinutes(useMinutes);
	}

	public String getValidationMessage() {
		return this.leaveUnitType.getValidationMessage();
	}
}
