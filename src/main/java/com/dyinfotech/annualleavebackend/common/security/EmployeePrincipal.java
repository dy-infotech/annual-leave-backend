package com.dyinfotech.annualleavebackend.common.security;

import com.dyinfotech.annualleavebackend.common.type.Role;

public record EmployeePrincipal(Long employeeId, Role role) {
	
}
