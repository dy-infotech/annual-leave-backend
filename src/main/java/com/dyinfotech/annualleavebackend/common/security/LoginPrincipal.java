package com.dyinfotech.annualleavebackend.common.security;

import com.dyinfotech.annualleavebackend.common.type.Role;

public record LoginPrincipal(Long employeeId, Role role) {
	
}
