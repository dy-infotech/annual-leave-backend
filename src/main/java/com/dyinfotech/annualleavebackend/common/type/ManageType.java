package com.dyinfotech.annualleavebackend.common.type;

import lombok.Getter;

@Getter
public enum ManageType {
	IS_NEW_TEAM				(1 << 0, "신규 팀 생성 여부")
	,IS_TEAM_MANAGER		(1 << 1, "팀 매니저 여부")
	,IS_VALID_DEPARTMENT	(1 << 2, "유효한 부서 여부")
	,IS_VALID_POSITION    	(1 << 3, "유효한 직급 여부")
	;
	
	private final int code;
	private final String desc;
	
	ManageType(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}
	
	public boolean contains(int data) {
		return (code & data) != 0;
	}
	public int addFlag(int data) {
		return code | data;
	}
}
