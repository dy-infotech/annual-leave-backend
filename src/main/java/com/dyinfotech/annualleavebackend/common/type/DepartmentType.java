package com.dyinfotech.annualleavebackend.common.type;

import lombok.Getter;

@Getter
public enum DepartmentType {
	CEO				("대표이사")
	,SI_BUSINESS	("SI사업팀")
	;
	
	private String name;
	
	private DepartmentType(String name) {
		this.name = name;
	}
}
