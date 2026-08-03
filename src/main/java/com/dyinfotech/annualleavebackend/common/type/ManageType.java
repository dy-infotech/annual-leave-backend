package com.dyinfotech.annualleavebackend.common.type;

import lombok.Getter;

@Getter
public enum ManageType {
	IS_NEW_TEAM			(1, "신규 팀 생성 여부")
	,IS_TEAM_MANAGER	(2, "팀 매니저 여부")
	;
	
	private final int code;
	private final String desc;
	
	ManageType(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}
	
	public boolean hasCode(int data) {
		return (code & data) != 0;
	}
	public int getAppliedCode(int data) {
		return code | data;
	}
}
