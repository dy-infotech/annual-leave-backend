package com.dyinfotech.annualleavebackend.common.type;

import lombok.Getter;

@Getter
public enum Sign {
	PLUS	("plus"),
	MINUS	("minus")
	;
	
	private String name;
	
	Sign(String name) {
		this.name = name;
	}
}
