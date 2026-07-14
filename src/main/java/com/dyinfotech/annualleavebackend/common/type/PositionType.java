package com.dyinfotech.annualleavebackend.common.type;

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
	,CEO						("대표이사")
	;
	
	private String name;
	
	private PositionType(String name) {
		this.name = name;
	}
}
